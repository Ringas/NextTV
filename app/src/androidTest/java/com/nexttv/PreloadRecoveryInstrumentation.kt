package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.SystemClock
import android.view.Surface
import com.nexttv.core.App
import com.nexttv.core.data.ChannelRoute
import com.nexttv.core.data.Source
import com.nexttv.core.tv.PlaybackSession
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class PreloadRecoveryInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        waitForIdleSync()
        val app = App.get()
        val cacheDeadline = SystemClock.uptimeMillis() + 10_000
        while (!app.cacheReady && SystemClock.uptimeMillis() < cacheDeadline) SystemClock.sleep(50)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val field = app.channels.javaClass.getDeclaredField("channels").apply { isAccessible = true }
        val savedPreload = app.prefs.preloadMode
        val savedLast = app.prefs.lastChannelId
        val requests = AtomicInteger()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { connection ->
                        requests.incrementAndGet()
                        connection.soTimeout = 1500
                        val input = connection.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) Unit
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 410 Gone\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            flush()
                        }
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        var engine: PlaybackSession? = null
        var surface: Surface? = null
        var texture: SurfaceTexture? = null
        var attached: androidx.media3.exoplayer.ExoPlayer? = null
        var savedChannels = app.channels.channels
        var result = Activity.RESULT_OK
        var report = ""
        fun await(label: String, timeout: Long = 45_000, predicate: (PlaybackSession) -> Boolean) {
            val end = SystemClock.uptimeMillis() + timeout
            var diagnostics = ""
            while (SystemClock.uptimeMillis() < end) {
                var passed = false
                runOnMainSync {
                    val player = checkNotNull(engine)
                    if (attached !== player.player) {
                        attached = player.player
                        player.player.setVideoSurface(surface)
                    }
                    passed = predicate(player)
                    diagnostics = player.diagnostics()
                }
                if (passed) {
                    sendStatus(0, Bundle().apply { putString("stream", "PASS $label $diagnostics\n") })
                    return
                }
                SystemClock.sleep(60)
            }
            error("Timed out: $label requests=${requests.get()} $diagnostics")
        }

        try {
            val url = "http://192.0.2.115:8787/live.m3u"
            if (app.channels.sources.none { it.url == url }) {
                app.channels.upsertSource(Source("preload-real", "预热实测", url))
            }
            check(runBlocking { app.channels.refreshAll() }.ok > 0)
            savedChannels = app.channels.channels
            fun channel(name: String) = savedChannels.firstOrNull {
                it.name.replace(Regex("[^A-Za-z0-9]"), "").equals(name, ignoreCase = true)
            } ?: error("Channel missing: $name")

            val anchor = channel("LG1")
            val forward = channel("LG1FILM")
            val backward = channel("LG1SPOTLIGHT")
            fun broken(name: String) = channel(name).let {
                it.copy(routes = listOf(ChannelRoute("http://127.0.0.1:${server.localPort}/$name.m3u8", it.sourceId, it.id)))
            }
            val before = broken("SBSDRAMA")
            val after = broken("NEWKMOVIES")
            runOnMainSync {
                texture = SurfaceTexture(false).apply { setDefaultBufferSize(640, 360) }
                surface = Surface(texture)
                field.set(app.channels, listOf(backward, before, anchor, after, forward))
                app.prefs.preloadMode = 3
                engine = PlaybackSession(targetContext, anchor, scope)
            }
            await("LG1 real first frame") { it.current.id == anchor.id && it.rendered }
            fun waitForWarmTargets(label: String) = await(label, 40_000) {
                val diagnostics = it.diagnostics()
                val ready = diagnostics.substringAfter("readyIds=[").substringBefore(']').split(", ")
                ready.containsAll(listOf(forward.id, backward.id))
            }
            waitForWarmTargets("both directions preload past HTTP 410")
            check(requests.get() >= 4) { "Single routes did not retry in both directions" }
            var selected = ""
            runOnMainSync { engine!!.zap(1); selected = engine!!.current.name }
            check(selected == forward.name) { "Down selected $selected; requests=${requests.get()}" }
            await("down skips unavailable NEWKMOVIES and plays LG1FILM") { it.rendered && it.current.id == forward.id }
            runOnMainSync { engine!!.tune(anchor) }
            await("LG1 restored") { it.rendered && it.current.id == anchor.id }
            waitForWarmTargets("both directions rewarm before upward zap")
            runOnMainSync { engine!!.zap(-1); selected = engine!!.current.name }
            check(selected == backward.name) { "Up selected $selected; requests=${requests.get()}" }
            await("up skips unavailable SBSDRAMA and plays LG1SPOTLIGHT") { it.rendered && it.current.id == backward.id }
            runOnMainSync {
                app.prefs.preloadMode = 0
                engine!!.applyPreloadSetting()
                engine!!.tune(anchor)
            }
            await("LG1 restored with preloading off") { it.rendered && it.current.id == anchor.id }
            runOnMainSync { engine!!.zap(-1); selected = engine!!.current.name }
            check(selected == before.name) { "With preload off, expected to try the immediate neighbor: $selected" }
            await("real playback failure retries then advances upward", 60_000) {
                it.rendered && it.current.id == backward.id
            }
            runOnMainSync {
                field.set(app.channels, savedChannels)
                engine!!.tune(channel("NEWKMOVIES"))
            }
            await("NEWKMOVIES original live route renders") {
                it.rendered && it.current.id == channel("NEWKMOVIES").id
            }
            runOnMainSync { engine!!.tune(channel("SBSDRAMA")) }
            await("SBSDRAMA original live route renders") {
                it.rendered && it.current.id == channel("SBSDRAMA").id
            }
            report = "PASS actual English-channel playback and two-way failed-neighbor bypass; HTTP410 requests=${requests.get()}\n"
        } catch (failure: Throwable) {
            result = Activity.RESULT_CANCELED
            report = failure.stackTraceToString()
        } finally {
            runOnMainSync {
                engine?.release()
                scope.cancel()
                surface?.release()
                texture?.release()
                field.set(app.channels, savedChannels)
                app.prefs.preloadMode = savedPreload
                app.prefs.lastChannelId = savedLast
            }
            server.close()
        }
        finish(result, Bundle().apply { putString("stream", report) })
    }
}
