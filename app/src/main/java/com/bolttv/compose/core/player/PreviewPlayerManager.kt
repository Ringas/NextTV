package com.bolttv.compose.core.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bolttv.compose.core.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 实时预览播放器 + 首帧缓存 + 焦点 debounce
 *
 * Debounce 策略：
 * - 焦点变化时立刻更新 focusedChannel（UI 可立刻显示缓存帧）
 * - 真正起播延迟 [DEBOUNCE_MS]，期间若再次换焦则取消上次请求
 * - 快速滑动时不会对每个频道都发起网络/解码，只对停住的频道预览
 */
class PreviewPlayerManager(private val context: Context) {

    private val TAG = "PreviewPlayer"
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 焦点稳定多久后才真正加载预览（毫秒） */
    var debounceMs: Long = DEFAULT_DEBOUNCE_MS

    private var previewPlayer: ExoPlayer? = null

    /** 当前已真正在播的预览频道 id（可能与 focused 不同，因 debounce） */
    private var loadingChannelId: String? = null

    /** 待执行的 debounce 任务 */
    private var pendingLoad: Runnable? = null

    /** 弱引用式绑定，用于截帧 */
    @Volatile
    var boundPlayerView: PlayerView? = null

    private val _focusedChannel = MutableStateFlow<Channel?>(null)
    val focusedChannel: StateFlow<Channel?> = _focusedChannel.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    @Volatile
    var player: ExoPlayer? = null
        private set

    // 约缓存 24 张预览图
    private val frameCache = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // 仅当仍聚焦在当前加载频道时才标记 ready
            val stillFocused = _focusedChannel.value?.id == loadingChannelId
            _isReady.value = playbackState == Player.STATE_READY && stillFocused
            if (playbackState == Player.STATE_READY && stillFocused) {
                Log.d(TAG, "Preview ready for ${_focusedChannel.value?.name}")
                mainHandler.postDelayed({ captureCurrentFrame() }, 800)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.w(TAG, "Preview error: ${error.message}")
            _isReady.value = false
        }
    }

    fun getCachedFrame(channelId: String): Bitmap? = frameCache.get(channelId)

    /**
     * 请求预览。立即更新焦点状态；真正加载经 debounce。
     */
    fun requestPreview(channel: Channel?) {
        if (channel == null) return

        val previous = _focusedChannel.value
        // 同一频道且已在播/已在排队 → 忽略
        if (previous?.id == channel.id) return

        // 离开旧频道时截帧
        if (previous != null) {
            captureCurrentFrame()
        }

        // 立刻更新焦点，让 UI 先显示缓存帧
        _focusedChannel.value = channel
        _isReady.value = false

        // 取消尚未执行的加载
        cancelPendingLoad()

        // 若已经在播这个频道（极少见），直接返回
        if (loadingChannelId == channel.id && previewPlayer != null) {
            _isReady.value = previewPlayer?.playbackState == Player.STATE_READY
            return
        }

        val targetId = channel.id
        val targetUrl = channel.url
        val targetName = channel.name

        val task = Runnable {
            pendingLoad = null
            // 二次确认焦点仍在目标上
            if (_focusedChannel.value?.id != targetId) {
                Log.d(TAG, "Debounced load skipped (focus moved): $targetName")
                return@Runnable
            }
            startLoad(targetId, targetUrl, targetName)
        }
        pendingLoad = task
        mainHandler.postDelayed(task, debounceMs)
        Log.d(TAG, "Preview scheduled in ${debounceMs}ms: $targetName")
    }

    private fun startLoad(channelId: String, url: String, name: String) {
        Log.d(TAG, "Preview load start: $name")
        loadingChannelId = channelId
        _isReady.value = false

        ensurePlayer()
        val p = previewPlayer ?: return
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
        p.volume = 0f
    }

    fun clearPreview() {
        cancelPendingLoad()
        captureCurrentFrame()
        Log.d(TAG, "Clear preview")
        previewPlayer?.stop()
        loadingChannelId = null
        _focusedChannel.value = null
        _isReady.value = false
    }

    private fun cancelPendingLoad() {
        pendingLoad?.let { mainHandler.removeCallbacks(it) }
        pendingLoad = null
    }

    private fun captureCurrentFrame() {
        val channelId = loadingChannelId ?: _focusedChannel.value?.id ?: return
        val view = boundPlayerView ?: return
        try {
            if (!view.isAttachedToWindow) return
            val textureView = view.videoSurfaceView
            if (textureView is android.view.TextureView) {
                val bmp = textureView.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    val scaled = Bitmap.createScaledBitmap(bmp, 320, 180, true)
                    frameCache.put(channelId, scaled)
                    Log.d(TAG, "Cached frame for $channelId")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "capture frame failed: ${e.message}")
        }
    }

    private fun ensurePlayer() {
        if (previewPlayer != null) return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 3000, 250, 500)
            .build()

        previewPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                playWhenReady = true
                addListener(listener)
            }
        player = previewPlayer
        Log.d(TAG, "Preview player created")
    }

    fun release() {
        cancelPendingLoad()
        mainHandler.removeCallbacksAndMessages(null)
        previewPlayer?.removeListener(listener)
        previewPlayer?.release()
        previewPlayer = null
        player = null
        boundPlayerView = null
        loadingChannelId = null
        frameCache.evictAll()
        _focusedChannel.value = null
        _isReady.value = false
        Log.d(TAG, "Preview player released")
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 250L
    }
}
