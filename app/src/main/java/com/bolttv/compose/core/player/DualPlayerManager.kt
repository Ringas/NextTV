package com.bolttv.compose.core.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.DualPlayerState
import com.bolttv.compose.core.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 双播放器预热 + 错误自动重试
 */
class DualPlayerManager(private val context: Context) {

    private val TAG = "DualPlayerManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var primaryPlayer: ExoPlayer? = null
    private var secondaryPlayer: ExoPlayer? = null

    private val _state = MutableStateFlow(DualPlayerState())
    val state: StateFlow<DualPlayerState> = _state.asStateFlow()

    @Volatile
    var activePlayer: ExoPlayer? = null
        private set

    private var retryCount = 0
    private var retryRunnable: Runnable? = null
    private val maxRetries = 3
    private val retryDelayMs = 2000L

    private val primaryListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.update { it.copy(playbackState = PlaybackState.Buffering) }
                }
                Player.STATE_READY -> {
                    retryCount = 0
                    val ch = _state.value.currentChannel
                    if (ch != null) {
                        _state.update { it.copy(playbackState = PlaybackState.Playing(ch)) }
                    }
                }
                Player.STATE_ENDED -> { /* live rarely ends */ }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Primary player error (retry=$retryCount)", error)
            val msg = error.message ?: "播放错误"
            val ch = _state.value.currentChannel
            _state.update {
                it.copy(playbackState = PlaybackState.Error(msg, ch))
            }
            scheduleRetry(ch)
        }
    }

    fun initialize() {
        if (primaryPlayer != null) return
        primaryPlayer = buildPlayer().also {
            it.addListener(primaryListener)
            it.volume = 1f
        }
        secondaryPlayer = buildPlayer().also { it.volume = 0f }
        activePlayer = primaryPlayer
        Log.d(TAG, "DualPlayer initialized")
    }

    private fun buildPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(800, 25000, 500, 1000)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { playWhenReady = true }
    }

    fun play(channel: Channel) {
        initialize()
        cancelRetry()
        retryCount = 0

        val current = _state.value.currentChannel
        if (current?.id == channel.id &&
            _state.value.playbackState is PlaybackState.Playing
        ) return

        val alreadyPreloaded = _state.value.nextChannel?.id == channel.id
        val secondary = secondaryPlayer

        if (alreadyPreloaded && secondary != null) {
            Log.d(TAG, "Hot switch to ${channel.name}")
            swapPlayers()
            _state.update {
                it.copy(
                    currentChannel = channel,
                    nextChannel = null,
                    isPreloading = false,
                    playbackState = PlaybackState.Playing(channel)
                )
            }
        } else {
            Log.d(TAG, "Cold start ${channel.name}")
            startOnPrimary(channel)
        }
    }

    private fun startOnPrimary(channel: Channel) {
        val primary = primaryPlayer ?: return
        primary.setMediaItem(MediaItem.fromUri(channel.url))
        primary.prepare()
        primary.play()
        activePlayer = primary
        _state.update {
            it.copy(
                currentChannel = channel,
                nextChannel = null,
                isPreloading = false,
                playbackState = PlaybackState.Buffering
            )
        }
    }

    private fun scheduleRetry(channel: Channel?) {
        if (channel == null) return
        if (retryCount >= maxRetries) {
            Log.w(TAG, "Max retries reached for ${channel.name}")
            return
        }
        cancelRetry()
        retryCount++
        val attempt = retryCount
        val task = Runnable {
            Log.d(TAG, "Retry #$attempt ${channel.name}")
            // 仅当仍是当前频道时重试
            if (_state.value.currentChannel?.id == channel.id) {
                startOnPrimary(channel)
            }
        }
        retryRunnable = task
        mainHandler.postDelayed(task, retryDelayMs)
        _state.update {
            it.copy(
                playbackState = PlaybackState.Error(
                    "播放失败，${retryDelayMs / 1000}s 后重试 ($attempt/$maxRetries)",
                    channel
                )
            )
        }
    }

    fun retryNow() {
        val ch = _state.value.currentChannel ?: return
        cancelRetry()
        retryCount = 0
        startOnPrimary(ch)
    }

    private fun cancelRetry() {
        retryRunnable?.let { mainHandler.removeCallbacks(it) }
        retryRunnable = null
    }

    fun preload(channel: Channel?) {
        if (channel == null) return
        val secondary = secondaryPlayer ?: return
        if (_state.value.nextChannel?.id == channel.id) return
        if (_state.value.currentChannel?.id == channel.id) return
        Log.d(TAG, "Preloading ${channel.name}")
        secondary.setMediaItem(MediaItem.fromUri(channel.url))
        secondary.prepare()
        _state.update { it.copy(nextChannel = channel, isPreloading = true) }
    }

    private fun swapPlayers() {
        val temp = primaryPlayer
        primaryPlayer = secondaryPlayer
        secondaryPlayer = temp
        primaryPlayer?.volume = 1f
        primaryPlayer?.play()
        secondaryPlayer?.volume = 0f
        secondaryPlayer?.stop()
        activePlayer = primaryPlayer
        secondaryPlayer?.removeListener(primaryListener)
        primaryPlayer?.addListener(primaryListener)
    }

    fun stop() {
        cancelRetry()
        primaryPlayer?.stop()
        secondaryPlayer?.stop()
        _state.update {
            it.copy(
                currentChannel = null,
                nextChannel = null,
                playbackState = PlaybackState.Idle
            )
        }
    }

    fun release() {
        cancelRetry()
        primaryPlayer?.release()
        secondaryPlayer?.release()
        primaryPlayer = null
        secondaryPlayer = null
        activePlayer = null
        _state.value = DualPlayerState()
        Log.d(TAG, "DualPlayer released")
    }
}
