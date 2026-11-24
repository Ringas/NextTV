// SPDX-License-Identifier: GPL-3.0-only
// Next TV — Compose for TV implementation.
package com.nexttv.core.data

import com.nexttv.core.io.*
import kotlinx.coroutines.CancellationException

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 播放源仓库：负责订阅源的增删改、拉取、解析、本地缓存与合并后的频道列表。
 * 拉取失败时保留上一次的缓存，不会出现列表被清空的情况。
 */
class ChannelRepository(private val context: Context, private val prefs: Prefs) {
    var revision by androidx.compose.runtime.mutableIntStateOf(0);private set
    internal var onChannelsChanged:(()->Unit)?=null
    private val refreshMutex = Mutex()
    private val cache = ChannelCache(context)
    private val refresh = SubscriptionRefresh(context, prefs, cache)
    @Volatile private var configVersion = 0L

    /** 合并后的频道（已过滤） */
    var channels: List<Channel> = emptyList()
        private set

    /** 分组顺序（按首次出现顺序，4K 专区固定排在最后） */
    var groups: List<String> = emptyList()
        private set

    var sources: List<Source> = emptyList()
        private set

    /** 是否存在被过滤设置隐藏的频道，用于给出准确的空列表提示 */
    val hasLoadedChannels: Boolean get() = rawChannels.isNotEmpty()

    /** 合并与编号之前的原始频道列表 */
    private var rawChannels: List<Channel> = emptyList()

    // ---------------- 源管理 ----------------

    fun loadSources() {
        val json = prefs.sourcesJson
        if (json.isBlank()) {
            sources = emptyList()
            prefs.migrateLegacyEpg(emptySet())
            return
        }
        val parsed = runCatching { SubscriptionSettingsCodec.decode(json) }.getOrDefault(emptyList())
        // 老版本自带的内置演示源已经移除，这里顺手清掉遗留项，避免它一直报错
        val cleaned = parsed.filterNot { it.id == LEGACY_BUILTIN_ID || it.url.startsWith("asset://") }
        val enriched=cleaned.map{source->if(source.epgUrl.isNotBlank())source else {
            val value=runCatching{File(context.filesDir,"pl_${source.id}.m3u").takeIf{it.exists()&&it.length()<=ImportPolicy.PLAYLIST_BYTES}?.useLines{lines->M3uParser.parse(lines.take(16).firstOrNull{it.trimStart('\uFEFF',' ').startsWith("#EXTM3U")}.orEmpty(),source.id).epgUrl}.orEmpty()}.getOrDefault("")
            source.copy(epgUrl=if(value.isBlank())""else runCatching{java.net.URI(source.url).resolve(value).toString()}.getOrDefault(value))
        }}
        sources = enriched
        if(prefs.needsEpgMigration)prefs.migrateLegacyEpg(enriched.map{it.epgUrl}.filter{it.isNotBlank()}.toSet())
        if(enriched!=cleaned)saveSources()
        if (cleaned.size != parsed.size) saveSources()
    }

    fun saveSources() {
        prefs.sourcesJson = SubscriptionSettingsCodec.encode(sources)
    }

    @Synchronized fun upsertSource(source: Source) {
        configVersion++
        // A new library starts at 1, including libraries emptied by older app versions.
        if (sources.isEmpty()) prefs.channelNumbersJson = "{}"
        val idx = sources.indexOfFirst { it.id == source.id }
        val activationChanged = idx >= 0 && sources[idx].enabled != source.enabled
        sources = if (idx >= 0) {
            sources.toMutableList().also { it[idx] = source }
        } else {
            sources + source
        }
        saveSources()
        if (activationChanged) applyChannels(rawChannels, persist = false)
    }

    /** UI 调用入口：整表重建可能涉及上万条稳定 ID，放到 Default 线程执行。 */
    suspend fun upsertSourceAsync(source: Source) = withContext(Dispatchers.Default) { upsertSource(source) }

    suspend fun removeSourceAsync(id: String) = withContext(Dispatchers.Default) { removeSource(id) }

    suspend fun rebuildChannelsAsync() = withContext(Dispatchers.Default) { rebuildChannels() }

    @Synchronized fun removeSource(id: String) {
        configVersion++
        sources = sources.filterNot { it.id == id }
        if (sources.isEmpty()) prefs.channelNumbersJson = "{}"
        saveSources()
        prefs.removeFavorites(rawChannels.filter{it.sourceId==id}.map{it.id}.toSet())
        applyChannels(rawChannels.filterNot{it.sourceId==id})
        cache.deleteSourceCache(id)
    }

    // ---------------- 缓存 ----------------

    fun loadCache() {
        loadSources()
        runCatching {
            val (list, legacy) = cache.loadChannels() ?: return
            val migrated = if(legacy)sources.flatMap{source->cache.readSourceCache(source.id, source.url).orEmpty()}else emptyList()
            applyChannels(migrated.ifEmpty{list}, persist = legacy)
        }
    }

    fun importLegacyCache(raw:String,url:String,favoriteUrls:Set<String>,lastUrl:String) {
        if(sources.isNotEmpty()||raw.isBlank()||url.isBlank())return
        val source=Source("nexttv-upgrade","家庭订阅源",url)
        val parsed=runCatching{M3uParser.parse(raw,source.id)}.getOrElse{return}
        upsertSource(source)
        raw.byteInputStream().use { cache.writeSourceCache(source.id, it) }
        applyChannels(parsed.channels)
        parsed.epgUrl?.let{upsertSource(source.copy(epgUrl=it))}
        parsed.channels.filter{it.url in favoriteUrls}.forEach{if(!prefs.isFavorite(it.id))prefs.toggleFavorite(it.id)}
        parsed.channels.firstOrNull{it.url==lastUrl}?.let{prefs.lastChannelId=it.id}
    }

    @Synchronized
    internal fun rebuildChannels() = applyChannels(rawChannels, persist = false)

    private fun applyChannels(list: List<Channel>, persist: Boolean = true) {
        val canonical=list.map { c ->
            val id=M3uParser.stableChannelId(c.sourceId,c.name,c.tvgId)
            if(c.id!=id&&prefs.isFavorite(c.id)){if(!prefs.isFavorite(id))prefs.toggleFavorite(id);prefs.toggleFavorite(c.id)}
            if(prefs.lastChannelId==c.id)prefs.lastChannelId=id
            c.copy(id=id)
        }
        // Upgrade old IDs while old cached URL/name evidence is still available.
        rawChannels.forEach { old ->
            val id=M3uParser.stableChannelId(old.sourceId,old.name,old.tvgId)
            if(old.id!=id&&prefs.isFavorite(old.id)){if(!prefs.isFavorite(id))prefs.toggleFavorite(id);prefs.toggleFavorite(old.id)}
            if(prefs.lastChannelId==old.id)prefs.lastChannelId=id
        }
        rawChannels = canonical
        val knownSources=sources.associateBy{it.id}
        val sourceChannels = mergeChannelRoutes(canonical.filter { c -> knownSources[c.sourceId]?.enabled == true && M3uParser.isPlayableUrl(c.url) })
        val merged = if (prefs.mergeChannelsAcrossSources) mergeAcrossSources(sourceChannels) else sourceChannels
        val numbering=runCatching{org.json.JSONObject(prefs.channelNumbersJson.ifBlank{"{}"})}.getOrElse{org.json.JSONObject()}
        val usedNumbers=sourceChannels.mapNotNull{numbering.optInt(it.id).takeIf{n->n>0}}.toMutableSet()
        var nextNumber=(usedNumbers.maxOrNull()?:0)+1
        sourceChannels.forEach{c->
            if(numbering.optInt(c.id)<=0){
                val number=c.number.takeIf{it>0&&it !in usedNumbers} ?: run{
                    while(nextNumber in usedNumbers)nextNumber++
                    nextNumber
                }
                numbering.put(c.id,number)
                usedNumbers.add(number)
                if(number>=nextNumber)nextNumber=number+1
            }
        }
        val numberingJson=numbering.toString();if(numberingJson!=prefs.channelNumbersJson)prefs.channelNumbersJson=numberingJson
        val filtered = merged.map { c -> c.copy(number = numbering.getInt(c.id)) }

        channels = filtered
        val ordered = LinkedHashSet<String>()
        filtered.forEach { ordered.add(it.groupName) }
        val head = ordered.filter { it != M3uParser.GROUP_4K }
        groups = if (ordered.contains(M3uParser.GROUP_4K)) head + M3uParser.GROUP_4K else head
        if (persist) runCatching { cache.saveChannels(rawChannels) }
        revision++
        onChannelsChanged?.invoke()
    }

    // ---------------- 刷新 ----------------

    data class RefreshResult(val ok: Int, val failed: Int, val updated: List<Source>, val epgUrl: String, val storageWarning: String = "")

    suspend fun refreshAll(respectAutoUpdate: Boolean = false, onProgress: (String) -> Unit = {}): RefreshResult = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            val snapshot = synchronized(this@ChannelRepository) {
                loadSources()
                RefreshSnapshot(configVersion, sources.toList(), rawChannels.groupBy(Channel::sourceId))
            }
            val staged = linkedMapOf<String, File>()
            try {
                val batch = refresh.prepare(snapshot.sources, snapshot.previousBySource, staged, onProgress, respectAutoUpdate)
                val job = currentCoroutineContext()
                synchronized(this@ChannelRepository) {
                    job.ensureActive()
                    publishRefresh(snapshot, batch, staged)
                }
            } catch (limit: ImportLimitException) {
                synchronized(this@ChannelRepository) {
                    if (snapshot.version != configVersion) return@synchronized supersededRefresh()
                    val message = limit.message ?: "导入超限，已保留原列表"
                    sources = sources.map { if (it.enabled) it.copy(lastError = message) else it }
                    saveSources()
                    RefreshResult(0, snapshot.sources.count(Source::enabled), sources, prefs.epgUrl)
                }
            } finally {
                staged.values.forEach { it.delete() }
                onProgress("")
            }
        }
    }

    private data class RefreshSnapshot(
        val version: Long,
        val sources: List<Source>,
        val previousBySource: Map<String, List<Channel>>
    )

    /** Called under the source lock after IO. A changed source invalidates the entire batch. */
    private fun publishRefresh(
        snapshot: RefreshSnapshot,
        batch: SubscriptionRefresh.PreparedRefresh,
        staged: Map<String, File>
    ): RefreshResult {
        if (configVersion != snapshot.version) return supersededRefresh()
        try {
            cache.saveChannels(batch.channels)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return RefreshResult(0, snapshot.sources.count(Source::enabled), sources, prefs.epgUrl,
                "订阅更新失败：频道缓存无法写入，已保留原列表")
        }

        val unavailable = staged.count { (id, file) ->
            runCatching { file.inputStream().use { cache.writeSourceCache(id, it) } }.getOrDefault(false).not()
        }
        sources = batch.sources
        saveSources()
        prefs.removeFavorites(batch.expiredFavorites)
        applyChannels(batch.channels, persist = false)
        val warning = if (unavailable == 0) "" else "已更新频道，${unavailable} 个订阅备份未能保存"
        return RefreshResult(batch.refreshed.size, batch.failed, sources, batch.guide, warning)
    }

    private fun supersededRefresh() = RefreshResult(0, 0, sources, prefs.epgUrl)

    // ---------------- 查询 ----------------

    fun channelsIn(group: String?, favoritesOnly: Boolean, query: String): List<Channel> {
        val fav = if (favoritesOnly) prefs.favorites else null
        var list = channels
        if (group != null && group.isNotBlank() && group != GROUP_ALL) {
            list = list.filter { it.groupName == group }
        }
        if (fav != null) list = list.filter { channel -> channel.id in fav || channel.playbackRoutes.any { it.originalId in fav } }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase(Locale.ROOT)
            list = list.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                    it.groupName.lowercase(Locale.ROOT).contains(q) ||
                    it.number.toString() == q
            }
        }
        return list
    }

    // ---------------- JSON ----------------

    companion object {
        const val GROUP_ALL = "全部频道"

        /** 老版本内置演示源的固定 id，仅用于升级时清理 */
        private const val LEGACY_BUILTIN_ID = "builtin"

        /** 解析 "Key: Value" 每行一条的自定义请求头 */
        fun parseHeaders(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()
            val out = HashMap<String, String>()
            raw.lineSequence().forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
                }
            }
            return out
        }

    }
}
