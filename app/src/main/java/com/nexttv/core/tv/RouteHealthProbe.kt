// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.os.SystemClock
import com.nexttv.core.App
import com.nexttv.core.data.ChannelRoute
import com.nexttv.core.data.RouteHealth
import com.nexttv.core.data.playbackHeaders
import com.nexttv.supportsHttpPlayback
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/**
 * Background route health probe.
 *
 * It keeps running while a channel is playing: the TV is mostly used for playback,
 * so pausing on "any playback" would mean never probing. During playback it uses
 * one request at a time and skips the channel that is currently on screen; only the
 * first-frame window yields for a few seconds so a slow tune is not competing with
 * background traffic.
 *
 * Timing:
 *  - hidden routes are retried every few seconds in tiny batches, so recovery is
 *    visible while the user is still zapping rather than minutes later;
 *  - all other routes are swept over a four-hour window, a few dozen per pass;
 *    the pass is incremental, never a full-library burst;
 *  - a request gets 8s for its first byte and 25s total once bytes start flowing.
 *    A server that accepts the connection but sends nothing is cut at 8s.
 */
internal object RouteHealthProbe {
    private const val STARTUP_DELAY_MS = 1_000L
    private const val TRANSIENT_RETRY_MS = 60_000L
    private const val HIDDEN_RETRY_MS = 8_000L
    private const val HIDDEN_PASS_GAP_MS = 3_000L
    private const val FULL_SWEEP_MS = 4 * 60 * 60 * 1000L
    private const val IDLE_CONCURRENCY = 2
    private const val PLAYING_CONCURRENCY = 1
    // The first seconds after launch are a dedicated fast sweep: one request per
    // route, a 1s budget and six workers, so dead routes disappear before the
    // user starts zapping. Normal sweeps keep the gentle limits.
    private const val FAST_PASSES = 2
    private const val STARTUP_CONCURRENCY = 6
    private const val STARTUP_ROUTES_PER_PASS = 40
    private const val STARTUP_PASS_GAP_MS = 1_000L
    // Hidden recovery uses small batches so a recovered route returns to the
    // channel list within seconds, not after a 20-route burst.
    private const val IDLE_ROUTES_PER_PASS = 40
    private const val PLAYING_ROUTES_PER_PASS = 12
    private const val IDLE_HIDDEN_PER_PASS = 4
    private const val PLAYING_HIDDEN_PER_PASS = 2

    suspend fun run(app: App, priorityGroup: () -> String, visible: () -> Boolean) {
        delay(STARTUP_DELAY_MS)
        var startingSince = 0L
        var fastPasses = FAST_PASSES
        while (currentCoroutineContext().isActive) {
            val fastPass = fastPasses > 0
            if (!visible()) {
                delay(2_000)
                continue
            }
            val session = PlaybackSession.active
            val now = SystemClock.elapsedRealtime()
            if (session?.starting == true) {
                if (startingSince == 0L) startingSince = now
                // A dead stream can stay in "starting" forever; only yield the first
                // 12 seconds, then let the probe continue at low concurrency.
                if (now - startingSince < 12_000) {
                    delay(3_000)
                    continue
                }
            } else {
                startingSince = 0L
            }
            val channels = app.channels.channels
            if (channels.isEmpty()) {
                delay(30_000)
                continue
            }
            val blocked = session?.current?.playbackRoutes?.mapTo(HashSet()) { routeIdentity(it) }
                ?: emptySet()
            val hidden = RouteHealth.hiddenRoutes(channels)
                .filterNot { routeIdentity(it) in blocked }
            val hiddenLimit = if (fastPass) 0 else if (session == null) IDLE_HIDDEN_PER_PASS else PLAYING_HIDDEN_PER_PASS
            val hiddenBatch = hidden.take(hiddenLimit)
            val hiddenKeys = hidden.mapTo(HashSet()) { routeIdentity(it) }
            val targets = RouteHealth.routesNeedingProbe(
                channels = channels,
                staleBefore = now - FULL_SWEEP_MS,
                priorityGroup = priorityGroup(),
                retryBefore = now - TRANSIENT_RETRY_MS,
            ).filterNot { routeIdentity(it) in blocked || routeIdentity(it) in hiddenKeys }
            if (hiddenBatch.isEmpty() && targets.isEmpty()) {
                if (fastPass) {
                    fastPasses = 0
                    continue
                }
                delay(if (hidden.isEmpty()) 30_000L else HIDDEN_RETRY_MS)
                continue
            }
            val limit = if (fastPass) STARTUP_ROUTES_PER_PASS else if (session == null) IDLE_ROUTES_PER_PASS else PLAYING_ROUTES_PER_PASS
            val scanLimit = (limit - hiddenBatch.size).coerceAtLeast(0)
            val scanBatch = targets.take(scanLimit)
            val backlog = hidden.size > hiddenBatch.size || targets.size > scanBatch.size
            val pass = hiddenBatch + scanBatch
            var index = 0
            while (index < pass.size && currentCoroutineContext().isActive) {
                if (!visible()) break
                val active = PlaybackSession.active
                val activeNow = SystemClock.elapsedRealtime()
                if (active?.starting == true) {
                    if (startingSince == 0L) startingSince = activeNow
                    if (activeNow - startingSince < 12_000) {
                        delay(3_000)
                        continue
                    }
                } else {
                    startingSince = 0L
                }
                val blockedNow = active?.current?.playbackRoutes?.mapTo(HashSet()) { routeIdentity(it) }
                    ?: emptySet()
                val activeConcurrency = when {
                    fastPass -> STARTUP_CONCURRENCY
                    active == null -> IDLE_CONCURRENCY
                    else -> PLAYING_CONCURRENCY
                }
                val batch = pass.subList(index, minOf(index + activeConcurrency, pass.size))
                    .filterNot { routeIdentity(it) in blockedNow }
                if (batch.isEmpty()) {
                    index += activeConcurrency
                    continue
                }
                supervisorScope {
                    batch.map { route -> async { probe(app, route, fast = fastPass) } }.awaitAll()
                }
                index += batch.size
                delay(if (active == null) 120 else 700)
            }
            if (fastPass && index >= pass.size) fastPasses--
            // Hidden recovery gets a short gap between tiny batches. Normal
            // sweeps keep the old backoff so this does not become a hot loop.
            delay(when {
                fastPass -> STARTUP_PASS_GAP_MS
                hiddenBatch.isNotEmpty() -> HIDDEN_PASS_GAP_MS
                backlog -> 30_000L
                else -> HIDDEN_RETRY_MS
            })
        }
    }

    private fun routeIdentity(route: ChannelRoute): String =
        route.sourceId + "\u0000" + route.url.trim()

    private suspend fun probe(app: App, route: ChannelRoute, fast: Boolean) {
        // Hidden routes and single-route channels get a deeper check (playlist +
        // first segment), because one dead segment can hide the whole channel.
        // Multi-route channels only need the first request during the light sweep.
        val singleRoute = app.channels.channels.any { channel ->
            channel.playbackRoutes.size == 1 && routeIdentity(channel.playbackRoutes.first()) == routeIdentity(route)
        }
        val deep = singleRoute || (!fast && RouteHealth.status(route) == RouteHealth.Status.UNAVAILABLE)
        when (val result = RouteProbe.check(app, route, deep = deep, fast = fast)) {
            ProbeResult.AVAILABLE -> RouteHealth.markProbeSuccess(route)
            ProbeResult.UNAVAILABLE -> RouteHealth.markProbeFailure(route, definitive = true)
            // In the launch sweep a single 1s miss already hides the route; the
            // normal 8s probe and the hidden retry can restore a slow-but-alive route.
            ProbeResult.TEMPORARY -> RouteHealth.markProbeFailure(route, definitive = fast)
        }
    }
}

private enum class ProbeResult { AVAILABLE, UNAVAILABLE, TEMPORARY }

/**
 * Lightweight HTTP validation. It never starts a decoder; it only proves that the
 * route's current URL still yields media bytes. HLS playlists are followed one
 * level (variant → first segment) so a 200 playlist pointing at a dead segment is
 * not treated as healthy.
 */
private object RouteProbe {
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val FIRST_BYTE_WAIT_MS = 8_000L
    private const val TOTAL_WAIT_MS = 25_000L
    private const val FAST_FIRST_BYTE_WAIT_MS = 1_000L
    private const val FAST_TOTAL_WAIT_MS = 2_000L
    private const val READ_TIMEOUT_MS = 3_000
    private const val MAX_PLAYLIST_BYTES = 32 * 1024
    private const val MAX_MEDIA_BYTES = 4 * 1024

    suspend fun check(app: App, route: ChannelRoute, deep: Boolean, fast: Boolean = false): ProbeResult = withContext(Dispatchers.IO) {
        if (!supportsHttpPlayback(route.url)) return@withContext ProbeResult.UNAVAILABLE
        val source = app.channels.sources.firstOrNull { it.id == route.sourceId }
        val headers = playbackHeaders(
            source = source,
            defaultUserAgent = app.prefs.defaultUserAgent,
            defaultReferer = app.prefs.defaultReferer,
            routeHeaders = route.headers,
        )
        probeUrl(route.url, headers, deep = deep, fast = fast, depth = 0)
    }

    private fun probeUrl(url: String, headers: Map<String, String>, deep: Boolean, fast: Boolean, depth: Int): ProbeResult {
        if (depth > 2) return ProbeResult.AVAILABLE
        if (!supportsHttpPlayback(url)) return ProbeResult.UNAVAILABLE
        var connection: HttpURLConnection? = null
        return try {
            val started = SystemClock.elapsedRealtime()
            connection = URL(url).openConnection() as? HttpURLConnection
                ?: return ProbeResult.UNAVAILABLE
            connection.connectTimeout = if (fast) FAST_FIRST_BYTE_WAIT_MS.toInt() else CONNECT_TIMEOUT_MS
            connection.readTimeout = if (fast) FAST_FIRST_BYTE_WAIT_MS.toInt() else READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Range", "bytes=0-")
            headers.forEach { (key, value) -> if (key.isNotBlank() && value.isNotBlank()) connection.setRequestProperty(key, value) }
            val status = connection.responseCode
            if (status == 401 || status == 403 || status == 404 || status == 410) {
                return ProbeResult.UNAVAILABLE
            }
            if (status == 408 || status == 429 || status >= 500) return ProbeResult.TEMPORARY
            if (status !in 200..299) return ProbeResult.TEMPORARY

            val finalUrl = connection.url.toString()
            val contentType = connection.contentType.orEmpty().lowercase()
            val hls = finalUrl.substringBefore('?').endsWith(".m3u8", true) ||
                contentType.contains("mpegurl") || contentType.contains("m3u8")
            val bytes = readAdaptive(connection, started, if (hls) MAX_PLAYLIST_BYTES else MAX_MEDIA_BYTES, fast)
                ?: return ProbeResult.TEMPORARY
            if (bytes.isEmpty()) return ProbeResult.TEMPORARY
            if (!hls && looksLikeHtml(bytes)) return ProbeResult.UNAVAILABLE
            if (hls || String(bytes, 0, minOf(bytes.size, 32), StandardCharsets.UTF_8).contains("#EXTM3U")) {
                val text = String(bytes, StandardCharsets.UTF_8)
                if (!text.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")) {
                    return ProbeResult.UNAVAILABLE
                }
                // Shallow sweep: a populated playlist is enough. This is one request
                // per route, which keeps the four-hour scan light. Hidden/single-route
                // routes use the deep path so a playlist pointing at a 404 segment is
                // not treated as healthy.
                val next = firstPlaylistUri(text) ?: return ProbeResult.TEMPORARY
                if (!deep) return ProbeResult.AVAILABLE
                val resolved = resolve(finalUrl, next) ?: return ProbeResult.TEMPORARY
                return probeUrl(resolved, headers, deep = true, fast = fast, depth = depth + 1)
            }
            ProbeResult.AVAILABLE
        } catch (_: SocketTimeoutException) {
            ProbeResult.TEMPORARY
        } catch (_: Exception) {
            ProbeResult.TEMPORARY
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Returns null only when the first byte never arrived before the budget. Once
     * bytes start, the same request may continue until the total budget so a slow
     * live stream is not mistaken for a dead route. The launch sweep uses a 1s
     * budget to hide dead routes before the user starts zapping.
     */
    private fun readAdaptive(connection: HttpURLConnection, started: Long, maxBytes: Int, fast: Boolean): ByteArray? {
        val input = connection.inputStream
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        val firstByteWait = if (fast) FAST_FIRST_BYTE_WAIT_MS else FIRST_BYTE_WAIT_MS
        val totalWait = if (fast) FAST_TOTAL_WAIT_MS else TOTAL_WAIT_MS
        while (out.size() < maxBytes) {
            val now = SystemClock.elapsedRealtime()
            if (out.size() == 0 && now - started >= firstByteWait) return null
            if (now - started >= totalWait) break
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            out.write(buffer, 0, count)
            if (out.size() >= maxBytes) break
            if (maxBytes <= MAX_MEDIA_BYTES && out.size() >= 1024) break
        }
        return out.toByteArray()
    }

    private fun firstPlaylistUri(text: String): String? {
        var variant = false
        var segment = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-STREAM-INF") -> variant = true
                line.startsWith("#EXTINF") -> segment = true
                line.startsWith("#") -> Unit
                variant -> return line
                segment -> return line
            }
        }
        return null
    }

    private fun resolve(base: String, reference: String): String? =
        runCatching { URI(base).resolve(reference.trim()).toString() }.getOrNull()

    private fun looksLikeHtml(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 512), StandardCharsets.UTF_8).lowercase()
        return "<html" in head || "<!doctype" in head || "<body" in head
    }
}
