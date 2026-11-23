// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import android.content.Context
import com.nexttv.core.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    suspend fun prepare(
        sources: List<Source>,
        previousBySource: Map<String, List<Channel>>,
        staged: MutableMap<String, File>,
        onProgress: (String) -> Unit
    ): PreparedRefresh {
        val channels = ArrayList<Channel>()
        val updated = ArrayList<Source>()
        val refreshed = linkedSetOf<String>()
        val started = System.nanoTime()
        var stagedBytes = 0L
        var failures = 0
        var guide = ""
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            val previous = previousBySource[source.id].orEmpty()
            val selected = if (!source.enabled) {
                updated += source
                previous
            } else {
                onProgress("正在更新 ${source.name} …")
                val remaining = 90_000 - (System.nanoTime() - started) / 1_000_000
                if (remaining <= 0) throw ImportLimitException("订阅刷新总耗时超过上限，已保留原列表")
                val result = fetchAndParse(source, minOf(remaining, ImportPolicy.IMPORT_MS))
                val file = result.staged
                if (file != null) {
                    staged[source.id] = file
                    stagedBytes += file.length()
                    if (stagedBytes > ImportPolicy.PLAYLIST_BYTES * 2) throw ImportLimitException("订阅文本总量超过上限，已保留原列表")
                    refreshed += source.id
                    val epg = result.epgUrl.orEmpty()
                    if (guide.isEmpty()) guide = epg
                    val resolvedGuide = if (epg.isBlank()) source.epgUrl else
                        runCatching { java.net.URI(source.url).resolve(epg).toString() }.getOrDefault(epg)
                    updated += source.copy(
                        channelCount = mergeChannelRoutes(result.channels).size,
                        lastUpdate = System.currentTimeMillis(), lastError = "", epgUrl = resolvedGuide
                    )
                    result.channels
                } else {
                    failures++
                    updated += source.copy(
                        lastError = result.error.ifBlank { "获取失败" }, lastUpdate = System.currentTimeMillis()
                    )
                    previous.ifEmpty { cache.readSourceCache(source.id, source.url).orEmpty() }
                }
            }
            if (channels.size + selected.size > ImportPolicy.CHANNEL_CACHE_RECORDS) {
                throw ImportLimitException("所有订阅的频道/线路总量超过上限")
            }
            channels.addAll(selected)
        }

        val retained = channels.mapTo(hashSetOf()) { M3uParser.stableChannelId(it.sourceId, it.name, it.tvgId) }
        val expired = refreshed.asSequence().flatMap { previousBySource[it].orEmpty().asSequence() }
            .map { M3uParser.stableChannelId(it.sourceId, it.name, it.tvgId) }
            .filterNot(retained::contains).toSet()
        return PreparedRefresh(updated, channels, refreshed, expired, failures, guide)
    }

    private data class ParseOutcome(
        val channels: List<Channel>,
        val staged: File?,
        val error: String,
        val epgUrl: String?
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
                    if (keep) "" else "解析到 0 个频道", parsed.epgUrl)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ParseOutcome(emptyList(), null, e.message ?: "网络错误", null)
        } finally {
            if (!keep) stage.delete()
        }
    }

}
