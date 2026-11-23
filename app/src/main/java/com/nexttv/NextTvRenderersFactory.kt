// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** Prefer system audio; retain JLayer MP2 when unsupported or an explicit bounded fallback is requested. */
@UnstableApi
class NextTvRenderersFactory(context: Context,private val preferMp2Software:Boolean=false,private val forcePcm:Boolean=false) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(context:Context,enableFloatOutput:Boolean,enableAudioTrackPlaybackParams:Boolean):AudioSink? =
        if(!forcePcm)super.buildAudioSink(context,enableFloatOutput,enableAudioTrackPlaybackParams)
        else androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
            .setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).build()


    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        if(preferMp2Software)out.add(Mp2AudioRenderer(eventHandler,eventListener,audioSink))
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
        if(!preferMp2Software)out.add(Mp2AudioRenderer(eventHandler,eventListener,audioSink))
    }
}
