// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.os.SystemClock
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nexttv.core.App
import com.nexttv.core.data.playbackHeaders
import com.nexttv.core.data.playbackUserAgent
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Forced backend comparison. No alternate audio decoder can mask a failed backend. */
class Mp2BackendInstrumentation : Instrumentation() {
    private lateinit var options: Bundle

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        options = arguments ?: Bundle()
        start()
    }

    private fun checkedMain(block: () -> Unit) {
        var result: Result<Unit>? = null
        runOnMainSync { result = runCatching(block) }
        checkNotNull(result).getOrThrow()
    }

    override fun onStart() {
        val measurements = JSONArray()
        var activity: Activity? = null
        val result = runCatching {
            val host = startActivitySync(Intent().setClassName("com.nexttv", "com.nexttv.core.tv.TvActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = host
            lateinit var surface: SurfaceView
            checkedMain {
                surface = SurfaceView(host)
                host.addContentView(surface, FrameLayout.LayoutParams(-1, -1))
            }
            val channelName = options.getString("channel")
            val ordinal = options.getString("route")?.toIntOrNull() ?: 0
            val seconds = (options.getString("seconds")?.toIntOrNull() ?: 12).coerceIn(10, 120)
            val switches = (options.getString("switches")?.toIntOrNull() ?: 0).coerceIn(0, 4)
            require(switches == 0 || channelName == null)
            val otherItem = if (switches > 0) {
                val file = File(host.filesDir, "mp2-switch-44100.ts")
                check(file.isFile)
                MediaItem.fromUri(file.toURI().toString())
            } else null
            val order = options.getString("order", "ABBAAB")
            require(order.length in 2..8 && order.all { it == 'A' || it == 'B' })
            val headers = if (channelName != null) {
                val app = App.get()
                val deadline = SystemClock.elapsedRealtime() + 10_000
                while (!app.cacheReady && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
                val channel = app.channels.channels.first { it.name == channelName }
                val route = channel.playbackRoutes[ordinal]
                val source = app.channels.sources.firstOrNull { it.id == route.sourceId }
                playbackHeaders(source, app.prefs.defaultUserAgent, app.prefs.defaultReferer, route.headers) to route.url
            } else {
                val name = options.getString("fixture", "mp2-validation.ts")
                val location = if (name == "mp2-broadcast-hls") {
                    "http://127.0.0.1:18743/live.m3u8"
                } else {
                    require(name in setOf("mp2-validation.ts", "mp2-broadcast.ts", "mp2-h264-broadcast.ts"))
                    val fixture = File(host.filesDir, name)
                    check(fixture.isFile) { "No verified MP2 stream or isolated codec fixture" }
                    fixture.toURI().toString()
                }
                emptyMap<String, String>() to location
            }
            val (requestHeaders, location) = headers
            val resolved = if (channelName != null) runBlocking { resolveStream(location, requestHeaders) } else null
            val item = MediaItem.Builder().setUri(resolved?.url ?: location)
                .setMimeType(resolved?.mime).build()
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(playbackUserAgent(requestHeaders, App.get().prefs.defaultUserAgent))
                .setDefaultRequestProperties(requestHeaders).setAllowCrossProtocolRedirects(true)
            val sourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(host, http))
            val renderer = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
                .getConstructor(Handler::class.java, AudioRendererEventListener::class.java, AudioSink::class.java)
            for ((index, backend) in order.withIndex()) {
                var player: ExoPlayer? = null
                try {
                    var decoder = ""
                    var firstSound = -1L
                    var firstVideo = -1L
                    var failure: PlaybackException? = null
                    var started = 0L
                    var firstMediaLoaded = -1L
                    var decoderReady = -1L
                    val firstPcm = AtomicLong(-1L)
                    val pcmBytes = AtomicLong()
                    val nonSilentBuffers = AtomicLong()
                    val factory = object : DefaultRenderersFactory(host) {
                        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
                                                    enableAudioTrackPlaybackParams: Boolean): AudioSink? {
                            val sink = super.buildAudioSink(context, false, enableAudioTrackPlaybackParams) ?: return null
                            return object : ForwardingAudioSink(sink) {
                                override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long,
                                                          encodedAccessUnitCount: Int): Boolean {
                                    if (buffer.hasRemaining() && firstPcm.get() < 0) {
                                        firstPcm.compareAndSet(-1L, SystemClock.elapsedRealtime() - started)
                                    }
                                    val start = buffer.position()
                                    val handled = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
                                    val end = buffer.position()
                                    pcmBytes.addAndGet((end - start).toLong())
                                    if ((start until end).step(17).any { buffer.get(it).toInt() != 0 }) {
                                        nonSilentBuffers.incrementAndGet()
                                    }
                                    return handled
                                }
                            }
                        }

                        override fun buildAudioRenderers(context: Context, extensionRendererMode: Int,
                            mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean,
                            audioSink: AudioSink, eventHandler: Handler,
                            eventListener: AudioRendererEventListener, out: ArrayList<Renderer>) {
                            out += if (backend == 'A') Mp2AudioRenderer(eventHandler, eventListener, audioSink)
                            else renderer.newInstance(eventHandler, eventListener, audioSink) as Renderer
                        }
                    }
                    checkedMain {
                        val active = ExoPlayer.Builder(host, factory).setMediaSourceFactory(sourceFactory).build()
                        player = active
                        active.addAnalyticsListener(object : AnalyticsListener {
                            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime,
                                decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                                decoder = decoderName
                                if (decoderReady < 0) decoderReady = initializedTimestampMs - started
                            }
                            override fun onLoadCompleted(
                                eventTime: AnalyticsListener.EventTime,
                                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
                            ) {
                                if (firstMediaLoaded < 0 && mediaLoadData.dataType == androidx.media3.common.C.DATA_TYPE_MEDIA) {
                                    firstMediaLoaded = loadEventInfo.elapsedRealtimeMs - started
                                }
                            }
                            override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime,
                                playoutStartSystemTimeMs: Long) {
                                if (firstSound < 0) firstSound = SystemClock.elapsedRealtime() - started
                            }
                            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime,
                                output: Any, renderTimeMs: Long) {
                                if (firstVideo < 0) firstVideo = SystemClock.elapsedRealtime() - started
                            }
                            override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
                                failure = error
                            }
                        })
                        active.setVideoSurfaceView(surface)
                        started = SystemClock.elapsedRealtime()
                        active.setMediaItem(item)
                        active.prepare()
                        active.play()
                    }
                    fun awaitAv() {
                        val deadline = started + 35_000
                        var ready = false
                        while (SystemClock.elapsedRealtime() < deadline && !ready) {
                            checkedMain {
                                failure?.let { throw IllegalStateException(it.errorCodeName, it) }
                                ready = firstSound >= 0 && firstVideo >= 0
                            }
                            if (!ready) SystemClock.sleep(100)
                        }
                        check(ready) { "$backend did not render audio and video" }
                    }
                    awaitAv()
                    val cpu = Process.getElapsedCpuTime()
                    val initialPcm = pcmBytes.get()
                    val initialNonSilent = nonSilentBuffers.get()
                    var startPosition = 0L
                    checkedMain { startPosition = player!!.currentPosition }
                    SystemClock.sleep(seconds * 1000L)
                    var metrics: JSONObject? = null
                    checkedMain {
                        val active = checkNotNull(player)
                        val expected = if (backend == 'A') "Mp2Decoder" else "ffmpeg"
                        check(decoder.contains(expected, ignoreCase = true)) { "Expected $expected; actual=$decoder" }
                        check(active.audioFormat?.sampleMimeType == MimeTypes.AUDIO_MPEG_L2) { "Media is not MP2" }
                        check(active.playbackState == Player.STATE_READY && active.isPlaying && failure == null)
                        val advanced = active.currentPosition - startPosition
                        val pcm = pcmBytes.get() - initialPcm
                        val nonSilent = nonSilentBuffers.get() - initialNonSilent
                        check(advanced >= seconds * 750L && pcm > 0 && nonSilent > 0) {
                            "Playback clock stalled or produced no non-silent PCM"
                        }
                        metrics = JSONObject().put("round", index + 1).put("backend", backend.toString())
                            .put("decoder", decoder).put("firstAudioMs", firstSound).put("firstVideoMs", firstVideo)
                            .put("firstMediaLoadCompletedMs", firstMediaLoaded)
                            .put("decoderInitializedMs", decoderReady).put("firstPcmToSinkMs", firstPcm.get())
                            .put("processCpuMs", Process.getElapsedCpuTime() - cpu)
                            .put("pcmBytes", pcm).put("nonSilentBuffers", nonSilent)
                            .put("clockAdvanceMs", advanced).put("sampleSeconds", seconds)
                    }
                    val switchAudio = JSONArray()
                    if (otherItem != null) repeat(switches) { switch ->
                        val target = if (switch % 2 == 0) otherItem else item
                        checkedMain {
                            val active = checkNotNull(player)
                            firstSound = -1
                            firstVideo = -1
                            started = SystemClock.elapsedRealtime()
                            active.setMediaItem(target)
                            active.prepare()
                            active.play()
                        }
                        awaitAv()
                        checkedMain {
                            val format = checkNotNull(player?.audioFormat)
                            check(format.sampleMimeType == MimeTypes.AUDIO_MPEG_L2)
                            val expectedRate = if (switch % 2 == 0) 44_100 else 48_000
                            check(format.sampleRate == expectedRate)
                            switchAudio.put(firstSound)
                        }
                    }
                    metrics?.put("switchAudioMs", switchAudio)
                    measurements.put(metrics)
                    sendStatus(0, Bundle().apply { putString("stream", "MP2_ROUND=$metrics\n") })
                } finally {
                    checkedMain { player?.release() }
                }
            }
        }
        activity?.let { host -> checkedMain { host.finish() } }
        finish(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", result.exceptionOrNull()?.stackTraceToString()
                ?: "PASS MP2 forced backend rounds with decoded audio and video\nMP2_RESULTS=$measurements\n")
        })
    }
}
