// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.os.SystemClock
import com.nexttv.core.App
import com.nexttv.core.data.ChannelRoute
import com.nexttv.core.data.playbackHeaders
import com.nexttv.supportsHttpPlayback
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/**
 * Small, on-demand latency probe used only while the route picker is open.
 * It measures the time from request start to the first body byte, using at most
 * two concurrent connections so an open dialog cannot compete with playback.
 * Unreachable or non-media replies return null; the picker then shows no value.
 */
internal object RouteLatency {
    private const val CONNECT_TIMEOUT_MS = 2_500
    private const val READ_TIMEOUT_MS = 2_500
    private const val MAX_CONCURRENCY = 2
    private const val MAX_BYTES = 64

    internal enum class Tone { GOOD, FAIR, SLOW }

    suspend fun measure(app: App, routes: List<ChannelRoute>): Map<ChannelRoute, Long?> =
        withContext(Dispatchers.IO) {
            val result = LinkedHashMap<ChannelRoute, Long?>(routes.size)
            routes.chunked(MAX_CONCURRENCY).forEach { batch ->
                val values = supervisorScope {
                    batch.map { route -> async { route to check(app, route) } }.awaitAll()
                }
                values.forEach { (route, latency) -> result[route] = latency }
            }
            result
        }

    /** 150ms is local/regional, 300ms still usable, above that the route feels slow. */
    internal fun tone(latencyMs: Long): Tone = when {
        latencyMs <= 150 -> Tone.GOOD
        latencyMs <= 300 -> Tone.FAIR
        else -> Tone.SLOW
    }

    private fun check(app: App, route: ChannelRoute): Long? {
        if (!supportsHttpPlayback(route.url)) return null
        val source = app.channels.sources.firstOrNull { it.id == route.sourceId }
        val headers = playbackHeaders(
            source = source,
            defaultUserAgent = app.prefs.defaultUserAgent,
            defaultReferer = app.prefs.defaultReferer,
            routeHeaders = route.headers,
        )
        var connection: HttpURLConnection? = null
        return try {
            val started = SystemClock.elapsedRealtime()
            connection = URL(route.url).openConnection() as? HttpURLConnection ?: return null
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Range", "bytes=0-")
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) connection.setRequestProperty(key, value)
            }
            val status = connection.responseCode
            if (status !in 200..299) return null
            val bytes = ByteArray(MAX_BYTES)
            val count = connection.inputStream.read(bytes)
            if (count <= 0) return null
            val head = String(bytes, 0, minOf(count, MAX_BYTES), StandardCharsets.UTF_8).lowercase()
            if ("<html" in head || "<!doctype" in head || "<body" in head) return null
            SystemClock.elapsedRealtime() - started
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
