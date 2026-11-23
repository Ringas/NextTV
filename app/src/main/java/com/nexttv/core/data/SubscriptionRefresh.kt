// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import android.content.Context
import com.nexttv.core.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

internal class SubscriptionRefresh(
    private val context: Context,
    prefs: Prefs,
    private val cache: ChannelCache,
) {
    private val transport = SubscriptionTransport(context, prefs)

    data class PreparedRefresh(
        val sources: List<Source>,
        val channels: List<Channel>,
        val refreshed: Set<String>,
        val expiredFavorites: Set<String>,
        val failed: Int,
        val guide: String
    )

    /**
     * 每个订阅拥有独立的下载/解析预算，单个源失败只影响该源；
     * 已成功的源会先进入待提交结果，不再因为一个慢源或超限而整批作废。
     */
    suspend fun prepare(
        sources: List<Source>,
        previousBySource: Map<String, List<Channel>>,
        staged: MutableMap<String, File>,
        onProgress: (String) -> Unit
    ): PreparedRefresh {
        val outcomes = coroutineScope {
            val gate = Semaphore(REFRESH_CONCURRENCY)
            sources.map { source ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        fetchOutcome(source, previousBySource[source.id].orEmpty(), onProgress)
                    }
                }
            }.awaitAll()
        }

        val selection = selectRefreshOutcomes(outcomes, previousBySource)
        var stagedBytes = 0L
        outcomes.forEach { outcome ->
            val file = outcome.staged ?: return@forEach
            if (stagedBytes + file.length() > ImportPolicy.PLAYLIST_BYTES * 4) {
                file.delete()
            } else {
                staged[outcome.source.id] = file
                stagedBytes += file.length()
            }
        }
        return PreparedRefresh(selection.sources, selection.channels, selection.refreshed,
            selection.expiredFavorites, selection.failed, selection.guide)
    }

    private suspend fun fetchOutcome(
        source: Source,
        previous: List<Channel>,
        onProgress: (String) -> Unit
    ): RefreshOutcome {
        currentCoroutineContext().ensureActive()
        if (!source.enabled) return RefreshOutcome(source, previous, null, false, "", null)
        onProgress("正在更新 ${source.name} …")
        val result = fetchAndParse(source, ImportPolicy.IMPORT_MS)
        val file = result.staged
        return if (file != null) {
            val resolvedGuide = if (result.epgUrl.isNullOrBlank()) source.epgUrl else
                runCatching { java.net.URI(source.url).resolve(result.epgUrl).toString() }.getOrDefault(result.epgUrl)
            RefreshOutcome(
                source.copy(channelCount = mergeChannelRoutes(result.channels).size,
                    unsupportedCount = result.skippedUnsupported,
                    lastUpdate = System.currentTimeMillis(), lastError = "", epgUrl = resolvedGuide),
                result.channels, file, true, "", result.epgUrl
            )
        } else {
            RefreshOutcome(
                source.copy(lastError = result.error.ifBlank { "获取失败" }, lastUpdate = System.currentTimeMillis()),
                previous.ifEmpty { cache.readSourceCache(source.id, source.url).orEmpty() },
                null, false, result.error, null
            )
        }
    }

    private data class ParseOutcome(
        val channels: List<Channel>,
        val staged: File?,
        val error: String,
        val epgUrl: String?,
        val skippedUnsupported: Int = 0
    )

    private suspend fun fetchAndParse(src: Source,timeoutMs:Long=ImportPolicy.IMPORT_MS): ParseOutcome {
        val stage = File.createTempFile("playlist-", ".m3u", context.cacheDir)
        var keep = false
        return try {
            val job = currentCoroutineContext()[Job]
            ImportBudget("播放源", timeoutMs, cancelled = { job?.isActive == false }).use { budget ->
                transport.stage(src, stage, budget)
                val parsed = stage.bufferedReader().use { reader ->
                    M3uParser.parse(reader, src.id, src.url, budget::check)
                }
                keep = parsed.channels.isNotEmpty()
                ParseOutcome(parsed.channels, stage.takeIf { keep },
                    if (keep) "" else "解析到 0 个频道", parsed.epgUrl, parsed.skippedUnsupported)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ParseOutcome(emptyList(), null, e.message ?: "网络错误", null)
        } finally {
            if (!keep) stage.delete()
        }
    }

    private companion object {
        /** 同时拉取的订阅数；电视盒子网络与磁盘都有限，3 路足够抵消串行等待。 */
        const val REFRESH_CONCURRENCY = 3
    }
}

internal data class RefreshOutcome(
    val source: Source,
    val channels: List<Channel>,
    val staged: File?,
    val refreshed: Boolean,
    val error: String,
    val epg: String?,
)

/** Pure planning step: apply the global channel cap, failure counts and favorite expiry. */
internal data class RefreshSelection(
    val sources: List<Source>,
    val channels: List<Channel>,
    val refreshed: Set<String>,
    val expiredFavorites: Set<String>,
    val failed: Int,
    val guide: String,
)

internal fun selectRefreshOutcomes(
    outcomes: List<RefreshOutcome>,
    previousBySource: Map<String, List<Channel>>,
    channelCap: Int = ImportPolicy.CHANNEL_CACHE_RECORDS,
): RefreshSelection {
    val channels = ArrayList<Channel>()
    val updated = ArrayList<Source>()
    val refreshed = linkedSetOf<String>()
    var failures = 0
    var guide = ""
    outcomes.forEach { outcome ->
        val original = outcome.channels
        val room = (channelCap - channels.size).coerceAtLeast(0)
        val selected = if (original.size > room) original.take(room) else original
        val trimmed = selected.size < original.size
        val refreshedFully = outcome.refreshed && !trimmed
        if (refreshedFully) refreshed += outcome.source.id
        if (outcome.source.enabled && !refreshedFully) failures++
        if (guide.isEmpty() && !outcome.epg.isNullOrBlank()) guide = outcome.epg.orEmpty()
        val error = listOf(
            outcome.error.takeIf { it.isNotBlank() },
            if (trimmed) "频道总量超过上限，仅保留前 ${selected.size} 个" else null
        ).filterNotNull().joinToString(" · ")
        updated += outcome.source.copy(lastError = error.ifBlank { outcome.source.lastError })
        channels.addAll(selected)
    }
    val retained = channels.mapTo(hashSetOf()) { M3uParser.stableChannelId(it.sourceId, it.name, it.tvgId) }
    val expired = refreshed.asSequence().flatMap { previousBySource[it].orEmpty().asSequence() }
        .map { M3uParser.stableChannelId(it.sourceId, it.name, it.tvgId) }
        .filterNot(retained::contains).toSet()
    return RefreshSelection(updated, channels, refreshed, expired, failures, guide)
}
