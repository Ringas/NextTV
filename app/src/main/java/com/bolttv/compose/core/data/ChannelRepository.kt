package com.bolttv.compose.core.data

import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.PlaylistSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 频道与播放源仓库
 * 简化版，后续可接入 Room / DataStore 持久化。
 */
class ChannelRepository {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _sources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    val sources: StateFlow<List<PlaylistSource>> = _sources.asStateFlow()

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups: StateFlow<List<String>> = _groups.asStateFlow()

    suspend fun loadFromUrl(url: String, sourceName: String = "默认源") = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "BoltComposeTV/0.1")
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = M3uParser.parse(content)
            _channels.value = parsed
            _groups.value = parsed.map { it.group }.distinct().sorted()
            val source = PlaylistSource(
                id = "src_${System.currentTimeMillis()}",
                name = sourceName,
                url = url
            )
            _sources.value = listOf(source) + _sources.value.filter { it.url != url }
            Result.success(parsed.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getChannelsByGroup(group: String?): List<Channel> {
        val all = _channels.value
        return if (group.isNullOrBlank() || group == "全部") all
        else all.filter { it.group == group }
    }

    fun toggleFavorite(channelId: String) {
        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(isFavorite = !it.isFavorite) else it
        }
    }

    fun clear() {
        _channels.value = emptyList()
        _groups.value = emptyList()
    }
}
