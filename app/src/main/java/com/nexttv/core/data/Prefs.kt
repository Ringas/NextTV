// SPDX-License-Identifier: GPL-3.0-only
// Next TV — Compose for TV implementation.
package com.nexttv.core.data
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 应用设置。全部用 SharedPreferences 持久化，不依赖任何账号或会员。
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("nexttv", Context.MODE_PRIVATE)

    @Volatile private var motionCache:Boolean?=null
    private var motionRevision by androidx.compose.runtime.mutableIntStateOf(0)
    // SharedPreferences stores listeners weakly. R8 must retain this owning strong field.
    @androidx.annotation.Keep
    private val motionListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,key->if(key==null||key=="light_glass")lightGlassState=sp.getBoolean("light_glass",false);if(key==null||key=="channel_previews")previewsState=sp.getBoolean("channel_previews",false);if(key==null||key=="reduce_motion"){val value=sp.getBoolean("reduce_motion",false);if(motionCache!=value){motionCache=value;motionRevision++}} }
    var reduceMotion:Boolean
        get(){motionRevision;return motionCache?:sp.getBoolean("reduce_motion",false).also{motionCache=it}}
        set(value){if(motionCache!=value){motionCache=value;motionRevision++};sp.edit().putBoolean("reduce_motion",value).apply()}
    private var lightGlassState by androidx.compose.runtime.mutableStateOf(sp.getBoolean("light_glass", false))
    var lightGlass:Boolean
        get()=lightGlassState
        set(value){lightGlassState=value;sp.edit().putBoolean("light_glass",value).apply()}
    private var previewsState by androidx.compose.runtime.mutableStateOf(sp.getBoolean("channel_previews", false))
    var channelPreviews:Boolean
        get()=previewsState
        set(value){previewsState=value;sp.edit().putBoolean("channel_previews",value).apply()}
    init {sp.registerOnSharedPreferenceChangeListener(motionListener)}
    /** 启动时自动更新播放源 */
    var autoUpdateOnStart by booleanSetting("auto_update", true)

    /** 启动时自动更新节目单 */
    var autoUpdateEpg by booleanSetting("auto_update_epg", true)

    var keepLastFrame by booleanSetting("keep_last_frame", true)

    /** 硬解优先，失败回落软解 */
    var hardwareFirst by booleanSetting("hw_first", true)

    /** 播放稳定后预热下一个频道，关闭可减少后台流量和解码开销 */
    var preloadNext by booleanSetting("preload_next", true)

    var preloadMode:Int
        get() = sp.getInt("preload_mode",if(preloadNext)1 else 0).coerceIn(0,3)
        set(value) = sp.edit().putInt("preload_mode",value).apply()
    var startLive by booleanSetting("start_live", false)

    /** 频道列表是否网格模式 */
    var showChannelLogos by booleanSetting("show_channel_logos", true)

    var gridMode by booleanSetting("grid_mode", true)

    /** 上次选中的分组（空串表示"全部"） */
    var selectedGroup by textSetting("selected_group", "")

    /** 上次播放的频道 */
    var lastChannelId by textSetting("last_channel", "")

    /** 当前生效的 EPG 地址（来自播放源或手动设置） */
    var epgUrl: String
        get() = sp.getString("manual_epg_url", "") ?: ""
        @Synchronized set(value) = sp.edit().putString("manual_epg_url", value).apply()

    @Synchronized
    fun migrateLegacyEpg(sourceUrls:Set<String>){
        if(sp.contains("manual_epg_url"))return
        val old=sp.getString("epg_url","").orEmpty()
        sp.edit().putString("legacy_epg_url_backup",old).putString("manual_epg_url",if(old in sourceUrls)""else old).apply()
    }
    val needsEpgMigration:Boolean get()=!sp.contains("manual_epg_url")

    /** 默认请求头，用于部分需要 UA/Referer 的源 */
    var defaultUserAgent by textSetting("ua", DEFAULT_UA)

    var defaultReferer by textSetting("referer", "")

    /** 播放源配置（JSON） */
    var sourcesJson by textSetting("sources", "")

    private data class FavoriteRead(val stored: Set<String>, val immutable: Set<String>)
    private var favoriteRead: FavoriteRead? = null

    val favorites: Set<String>
        @Synchronized get() {
            val stored = sp.getStringSet("favorites", emptySet()).orEmpty()
            val cached = favoriteRead
            if (cached?.stored === stored) return cached.immutable
            val immutable = java.util.Collections.unmodifiableSet(HashSet(stored))
            favoriteRead = FavoriteRead(stored, immutable)
            return immutable
        }

    @Synchronized
    fun removeFavorites(ids: Set<String>) {
        if (ids.isNotEmpty()) editFavorites { removeAll(ids) }
    }

    var mergeChannelsAcrossSources by booleanSetting("merge_channels_across_sources", false)

    var channelNumbersJson by textSetting("channel_numbers", "{}")

    fun isFavorite(channelId: String): Boolean = channelId in favorites

    @Synchronized
    fun toggleFavorite(channelId: String): Boolean = editFavorites {
        if (remove(channelId)) false else add(channelId)
    }

    fun isFavorite(channel: Channel): Boolean {
        val ids = favorites
        return channel.id in ids || channel.playbackRoutes.any { it.originalId in ids }
    }

    @Synchronized
    fun toggleFavorite(channel: Channel): Boolean = editFavorites {
        if (channel.id in this || channel.playbackRoutes.any { it.originalId in this }) {
            remove(channel.id)
            channel.playbackRoutes.forEach { remove(it.originalId) }
            false
        } else add(channel.id)
    }

    private fun editFavorites(change: MutableSet<String>.() -> Boolean): Boolean {
        val before = favorites
        val after = HashSet(before)
        val result = after.change()
        if (before != after) sp.edit().putStringSet("favorites", after).apply()
        return result
    }

    private fun booleanSetting(key: String, fallback: Boolean) = setting(
        key, { getBoolean(it, fallback) }, { name, value -> putBoolean(name, value) }
    )

    private fun textSetting(key: String, fallback: String) = setting(
        key, { getString(it, fallback) ?: fallback }, { name, value -> putString(name, value) }
    )

    private fun <T> setting(
        key: String,
        read: SharedPreferences.(String) -> T,
        write: SharedPreferences.Editor.(String, T) -> Unit
    ): ReadWriteProperty<Prefs, T> = object : ReadWriteProperty<Prefs, T> {
        override fun getValue(thisRef: Prefs, property: KProperty<*>): T = sp.read(key)

        override fun setValue(thisRef: Prefs, property: KProperty<*>, value: T) {
            sp.edit().apply { write(key, value) }.apply()
        }
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
