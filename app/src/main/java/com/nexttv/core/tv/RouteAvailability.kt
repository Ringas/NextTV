// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.os.SystemClock
import androidx.media3.datasource.HttpDataSource
import com.nexttv.StreamHttpStatusException
import com.nexttv.core.data.ChannelRoute
import java.net.MalformedURLException

/**
 * Session-scoped route health learned from playback and preload probes.
 *
 * Only structurally unavailable routes are hidden: HTTP 404/410 and unsupported
 * or malformed URLs. Slow first frames, timeouts and temporary network failures
 * are deliberately not hidden, because those routes may still play.
 */
internal object RouteAvailability {
    const val TTL_MS = 10 * 60 * 1000L

    private val failed = mutableMapOf<String, Long>()

    @Synchronized
    fun markFailed(route: ChannelRoute, now: Long = SystemClock.elapsedRealtime()) {
        failed[key(route)] = now + TTL_MS
    }

    @Synchronized
    fun markHealthy(route: ChannelRoute) {
        failed.remove(key(route))
    }

    @Synchronized
    fun clear() {
        failed.clear()
    }

    @Synchronized
    fun isUnavailable(route: ChannelRoute, now: Long = SystemClock.elapsedRealtime()): Boolean {
        val expiresAt = failed[key(route)] ?: return false
        if (expiresAt > now) return true
        failed.remove(key(route))
        return false
    }

    fun visible(
        routes: List<ChannelRoute>,
        hideUnavailable: Boolean,
        now: Long = SystemClock.elapsedRealtime(),
    ): List<ChannelRoute> {
        if (!hideUnavailable) return routes
        val available = routes.filterNot { isUnavailable(it, now) }
        // Never hide the last route: an all-failed channel must remain switchable.
        return available.ifEmpty { routes }
    }

    fun visibleIndices(
        routes: List<ChannelRoute>,
        hideUnavailable: Boolean,
        now: Long = SystemClock.elapsedRealtime(),
    ): List<Int> {
        if (!hideUnavailable) return routes.indices.toList()
        val available = routes.indices.filterNot { isUnavailable(routes[it], now) }
        return available.ifEmpty { routes.indices.toList() }
    }

    fun isDefinitiveFailure(error: Throwable): Boolean {
        val causes = generateSequence(error) { it.cause }.take(12).toList()
        val status = causes.firstNotNullOfOrNull {
            when (it) {
                is HttpDataSource.InvalidResponseCodeException -> it.responseCode
                is StreamHttpStatusException -> it.status
                else -> null
            }
        }
        if (status == 404 || status == 410) return true
        return causes.any { it is UnsupportedPlaybackProtocol || it is MalformedURLException }
    }

    private fun key(route: ChannelRoute): String =
        route.sourceId + "\n" + route.url + "\n" + route.headers.toSortedMap()
}
