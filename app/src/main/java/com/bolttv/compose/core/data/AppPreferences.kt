package com.bolttv.compose.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bolttv.compose.core.model.PlaylistSource
import com.bolttv.compose.core.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexttv_prefs")

/**
 * DataStore 持久化：播放源列表、收藏、上次频道、当前源
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        val SOURCES_JSON = stringPreferencesKey("sources_json")
        val ACTIVE_SOURCE_ID = stringPreferencesKey("active_source_id")
        val FAVORITES = stringSetPreferencesKey("favorites") // 稳定 key: url
        val LAST_CHANNEL_URL = stringPreferencesKey("last_channel_url")
        val LAST_EPG_URL = stringPreferencesKey("last_epg_url")
    }

    val sourcesFlow: Flow<List<PlaylistSource>> = context.dataStore.data.map { prefs ->
        parseSources(prefs[Keys.SOURCES_JSON])
    }

    val activeSourceIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_SOURCE_ID] }

    val favoritesFlow: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.FAVORITES] ?: emptySet()
    }

    val lastChannelUrlFlow: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_CHANNEL_URL] }

    val lastEpgUrlFlow: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_EPG_URL] }

    suspend fun getSources(): List<PlaylistSource> =
        parseSources(context.dataStore.data.first()[Keys.SOURCES_JSON])

    suspend fun getActiveSourceId(): String? =
        context.dataStore.data.first()[Keys.ACTIVE_SOURCE_ID]

    suspend fun getFavorites(): Set<String> =
        context.dataStore.data.first()[Keys.FAVORITES] ?: emptySet()

    suspend fun getLastChannelUrl(): String? =
        context.dataStore.data.first()[Keys.LAST_CHANNEL_URL]

    suspend fun getLastEpgUrl(): String? =
        context.dataStore.data.first()[Keys.LAST_EPG_URL]

    suspend fun saveSources(sources: List<PlaylistSource>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SOURCES_JSON] = sourcesToJson(sources)
        }
    }

    suspend fun setActiveSourceId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.ACTIVE_SOURCE_ID)
            else prefs[Keys.ACTIVE_SOURCE_ID] = id
        }
    }

    suspend fun setFavorites(urls: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FAVORITES] = urls
        }
    }

    suspend fun setLastChannelUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url == null) prefs.remove(Keys.LAST_CHANNEL_URL)
            else prefs[Keys.LAST_CHANNEL_URL] = url
        }
    }

    suspend fun setLastEpgUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url == null) prefs.remove(Keys.LAST_EPG_URL)
            else prefs[Keys.LAST_EPG_URL] = url
        }
    }

    private fun sourcesToJson(sources: List<PlaylistSource>): String {
        val arr = JSONArray()
        sources.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("url", s.url ?: "")
                    .put("localPath", s.localPath ?: "")
                    .put("type", s.type.name)
                    .put("lastUpdated", s.lastUpdated)
            )
        }
        return arr.toString()
    }

    private fun parseSources(raw: String?): List<PlaylistSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PlaylistSource(
                            id = o.optString("id"),
                            name = o.optString("name", "未命名"),
                            url = o.optString("url").ifBlank { null },
                            localPath = o.optString("localPath").ifBlank { null },
                            type = runCatching {
                                SourceType.valueOf(o.optString("type", SourceType.M3U_URL.name))
                            }.getOrDefault(SourceType.M3U_URL),
                            lastUpdated = o.optLong("lastUpdated", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
