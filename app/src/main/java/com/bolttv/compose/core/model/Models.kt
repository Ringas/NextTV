package com.bolttv.compose.core.model

/**
 * 核心数据模型
 * 参考 Bolt-IPTV 的 Models.kt，保持简洁并适配 Compose 状态。
 */

data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = "未分类",
    val tvgId: String? = null,
    val number: Int? = null,
    val isFavorite: Boolean = false
)

data class PlaylistSource(
    val id: String,
    val name: String,
    val url: String? = null,
    val localPath: String? = null,
    val type: SourceType = SourceType.M3U_URL,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class SourceType {
    M3U_URL,
    LOCAL_FILE,
    XTREAM // 预留
}

data class EpgProgram(
    val channelId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val description: String? = null
)

/**
 * 播放器状态，供 Compose 观察
 */
sealed class PlaybackState {
    data object Idle : PlaybackState()
    data object Buffering : PlaybackState()
    data class Playing(val channel: Channel) : PlaybackState()
    data class Error(val message: String, val channel: Channel?) : PlaybackState()
}

/**
 * 双播放器预热状态
 */
data class DualPlayerState(
    val currentChannel: Channel? = null,
    val nextChannel: Channel? = null,
    val isPreloading: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle
)
