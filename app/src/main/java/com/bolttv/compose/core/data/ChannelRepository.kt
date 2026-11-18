package com.bolttv.compose.core.data

import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.PlaylistSource
import com.bolttv.compose.core.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 频道与多播放源仓库（带 DataStore 持久化）
 */
class ChannelRepository(
    private val prefs: AppPreferences
) {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _sources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    val sources: StateFlow<List<PlaylistSource>> = _sources.asStateFlow()

    private val _activeSourceId = MutableStateFlow<String?>(null)
    val activeSourceId: StateFlow<String?> = _activeSourceId.asStateFlow()

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups: StateFlow<List<String>> = _groups.asStateFlow()

    private val _favoriteUrls = MutableStateFlow<Set<String>>(emptySet())
    val favoriteUrls: StateFlow<Set<String>> = _favoriteUrls.asStateFlow()

    /**
     * 启动时从磁盘恢复源列表与收藏，并自动加载当前源
     */
    suspend fun bootstrap(): Result<Int> {
        val sources = prefs.getSources()
        val activeId = prefs.getActiveSourceId()
        val favorites = prefs.getFavorites()
        _sources.value = sources
        _activeSourceId.value = activeId
        _favoriteUrls.value = favorites

        val active = sources.firstOrNull { it.id == activeId } ?: sources.firstOrNull()
        return if (active?.url != null) {
            loadSource(active, persist = false)
        } else {
            Result.success(0)
        }
    }

    suspend fun addOrUpdateSource(name: String, url: String, activate: Boolean = true): Result<Int> {
        val existing = _sources.value.firstOrNull { it.url == url }
        val source = existing?.copy(name = name, lastUpdated = System.currentTimeMillis())
            ?: PlaylistSource(
                id = "src_${UUID.randomUUID().toString().take(8)}",
                name = name.ifBlank { "播放源" },
                url = url,
                type = SourceType.M3U_URL,
                lastUpdated = System.currentTimeMillis()
            )
        val newList = listOf(source) + _sources.value.filter { it.url != url }
        _sources.value = newList
        prefs.saveSources(newList)
        if (activate) {
            _activeSourceId.value = source.id
            prefs.setActiveSourceId(source.id)
        }
        return loadSource(source, persist = true)
    }

    suspend fun removeSource(sourceId: String) {
        val newList = _sources.value.filter { it.id != sourceId }
        _sources.value = newList
        prefs.saveSources(newList)
        if (_activeSourceId.value == sourceId) {
            val next = newList.firstOrNull()
            _activeSourceId.value = next?.id
            prefs.setActiveSourceId(next?.id)
            if (next?.url != null) {
                loadSource(next, persist = false)
            } else {
                clearChannels()
            }
        }
    }

    suspend fun activateSource(sourceId: String): Result<Int> {
        val source = _sources.value.firstOrNull { it.id == sourceId }
            ?: return Result.failure(IllegalArgumentException("源不存在"))
        _activeSourceId.value = sourceId
        prefs.setActiveSourceId(sourceId)
        return loadSource(source, persist = true)
    }

    suspend fun refreshActiveSource(): Result<Int> {
        val id = _activeSourceId.value
        val source = _sources.value.firstOrNull { it.id == id }
            ?: _sources.value.firstOrNull()
            ?: return Result.failure(IllegalStateException("没有可刷新的源"))
        return loadSource(source, persist = true)
    }

    private suspend fun loadSource(source: PlaylistSource, persist: Boolean): Result<Int> =
        withContext(Dispatchers.IO) {
            val url = source.url ?: return@withContext Result.failure(IllegalArgumentException("无 URL"))
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 25000
                connection.setRequestProperty("User-Agent", "NextTV/0.4")
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = M3uParser.parse(content)
                val fav = _favoriteUrls.value
                val withFav = parsed.map { ch ->
                    ch.copy(isFavorite = ch.url in fav)
                }
                _channels.value = withFav
                _groups.value = withFav.map { it.group }.distinct().sorted()
                if (persist) {
                    val updated = source.copy(lastUpdated = System.currentTimeMillis())
                    val list = listOf(updated) + _sources.value.filter { it.id != source.id }
                    _sources.value = list
                    prefs.saveSources(list)
                }
                Result.success(withFav.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 兼容旧调用 */
    suspend fun loadFromUrl(url: String, sourceName: String = "默认源"): Result<Int> =
        addOrUpdateSource(sourceName, url, activate = true)

    fun getChannelsByGroup(group: String?): List<Channel> {
        val all = _channels.value
        return when {
            group.isNullOrBlank() || group == "全部" -> all
            group == "收藏" -> all.filter { it.isFavorite }
            else -> all.filter { it.group == group }
        }
    }

    suspend fun toggleFavorite(channel: Channel) {
        val urls = _favoriteUrls.value.toMutableSet()
        if (channel.url in urls) urls.remove(channel.url) else urls.add(channel.url)
        _favoriteUrls.value = urls
        prefs.setFavorites(urls)
        _channels.value = _channels.value.map {
            if (it.url == channel.url) it.copy(isFavorite = it.url in urls) else it
        }
    }

    suspend fun setLastChannel(channel: Channel?) {
        prefs.setLastChannelUrl(channel?.url)
    }

    suspend fun findLastChannel(): Channel? {
        val url = prefs.getLastChannelUrl() ?: return null
        return _channels.value.firstOrNull { it.url == url }
    }

    fun findByNumber(number: Int): Channel? =
        _channels.value.firstOrNull { it.number == number }

    private fun clearChannels() {
        _channels.value = emptyList()
        _groups.value = emptyList()
    }
}
