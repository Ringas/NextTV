package com.bolttv.compose.core.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.DualPlayerState
import com.bolttv.compose.core.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 双播放器预热管理器
 *
 * 核心思想来自 Bolt-IPTV：
 * - 主播放器负责当前频道
 * - 备用播放器在后台静音预加载「下一个」频道
 * - 换台时直接把备用提升为主，旧主变成新备用
 *
 * 在 Compose 版本中通过 StateFlow 暴露状态，UI 只负责绑定 Surface。
 */
class DualPlayerManager(private val context: Context) {

    private val TAG = "DualPlayerManager"

    private var primaryPlayer: ExoPlayer? = null
    private var secondaryPlayer: ExoPlayer? = null

    private val _state = MutableStateFlow(DualPlayerState())
    val state: StateFlow<DualPlayerState> = _state.asStateFlow()

    /** 当前真正在播的 Player（供 Compose AndroidView / PlayerSurface 使用） */
    @Volatile
    var activePlayer: ExoPlayer? = null
        private set

    private val primaryListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.update {
                        it.copy(playbackState = PlaybackState.Buffering)
                    }
                }
                Player.STATE_READY -> {
                    val ch = _state.value.currentChannel
                    if (ch != null) {
                        _state.update {
                            it.copy(playbackState = PlaybackState.Playing(ch))
                        }
                    }
                }
                Player.STATE_ENDED -> { /* live stream rarely ends */ }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Primary player error", error)
            _state.update {
                it.copy(
                    playbackState = PlaybackState.Error(
                        error.message ?: "播放错误",
                        it.currentChannel
                    )
                )
            }
        }
    }

    fun initialize() {
        if (primaryPlayer != null) return

        primaryPlayer = buildPlayer().also {
            it.addListener(primaryListener)
            it.volume = 1f
        }
        secondaryPlayer = buildPlayer().also {
            it.volume = 0f // 预热时静音
        }
        activePlayer = primaryPlayer
        Log.d(TAG, "DualPlayer initialized")
    }

    private fun buildPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // 起播优先，缓冲策略可参考 Bolt（主 800ms / 备机更激进）
                playWhenReady = true
            }
    }

    /**
     * 播放指定频道。如果备用已预加载该频道，则直接切换，实现近乎无黑屏换台。
     */
    fun play(channel: Channel) {
        initialize()

        val current = _state.value.currentChannel
        if (current?.id == channel.id) return

        // 检查备用是否已经是目标频道
        val secondary = secondaryPlayer
        val alreadyPreloaded = _state.value.nextChannel?.id == channel.id

        if (alreadyPreloaded && secondary != null) {
            // 热切换
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
            // 冷启动
            Log.d(TAG, "Cold start ${channel.name}")
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
    }

    /**
     * 预加载下一个可能观看的频道（通常是列表中的下一个）。
     * 在用户浏览频道列表时调用。
     */
    fun preload(channel: Channel?) {
        if (channel == null) return
        val secondary = secondaryPlayer ?: return
        if (_state.value.nextChannel?.id == channel.id) return
        if (_state.value.currentChannel?.id == channel.id) return

        Log.d(TAG, "Preloading ${channel.name}")
        secondary.setMediaItem(MediaItem.fromUri(channel.url))
        secondary.prepare()
        // 不 play，只准备到有关键帧

        _state.update {
            it.copy(
                nextChannel = channel,
                isPreloading = true
            )
        }
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

        // 重新挂 listener
        secondaryPlayer?.removeListener(primaryListener)
        primaryPlayer?.addListener(primaryListener)
    }

    fun stop() {
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
        primaryPlayer?.release()
        secondaryPlayer?.release()
        primaryPlayer = null
        secondaryPlayer = null
        activePlayer = null
        _state.value = DualPlayerState()
        Log.d(TAG, "DualPlayer released")
    }
}
