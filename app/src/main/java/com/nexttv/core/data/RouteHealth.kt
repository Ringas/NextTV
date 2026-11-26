// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

/**
 * Session-wide route health. Unknown routes stay visible; only a route we have
 * positively failed is removed. A background probe owns recovery: it rechecks
 * hidden routes every few minutes and flips them back to AVAILABLE.
 *
 * Failure policy:
 *  - definitive (404/410/401/403, unsupported protocol, malformed URL, HTML payload)
 *    hides immediately.
 *  - temporary (timeout, connection reset, 5xx/429) hides after two consecutive
 *    probe failures, so a slow-but-healthy channel is not killed by one hiccup.
 */
internal object RouteHealth {
    internal enum class Status { UNKNOWN, AVAILABLE, UNAVAILABLE }

    private data class Entry(val status: Status, val failures: Int, val checkedAt: Long)

    private val lock = Any()
    private val entries = HashMap<String, Entry>()

    @Volatile
    var revision: Int = 0
        private set

    /** ChannelRepository mirrors this into Compose state; no Compose dependency here. */
    @Volatile
    var onChanged: (() -> Unit)? = null

    private fun key(route: ChannelRoute): String {
        val headers = route.headers.entries.sortedBy { it.key.lowercase() }
            .joinToString("\u0001") { "${it.key.lowercase()}=${it.value}" }
        return "${route.sourceId}\u0000${route.url.trim()}\u0000$headers"
    }

    private fun changed() {
        revision++
        onChanged?.invoke()
    }

    fun status(route: ChannelRoute): Status = synchronized(lock) {
        entries[key(route)]?.status ?: Status.UNKNOWN
    }

    fun lastCheckedAt(route: ChannelRoute): Long = synchronized(lock) {
        entries[key(route)]?.checkedAt ?: 0L
    }

    fun isAvailable(route: ChannelRoute): Boolean = status(route) != Status.UNAVAILABLE

    fun availableRoutes(routes: List<ChannelRoute>): List<ChannelRoute> =
        routes.filter { isAvailable(it) }

    /** A channel is visible only while at least one of its routes is not marked dead. */
    fun isChannelAvailable(channel: Channel): Boolean {
        val routes = channel.playbackRoutes
        return routes.isNotEmpty() && routes.any { isAvailable(it) }
    }

    fun displayChannels(channels: List<Channel>, hideUnavailable: Boolean): List<Channel> =
        if (!hideUnavailable) channels else channels.filter { isChannelAvailable(it) }

    fun hiddenRoutes(channels: List<Channel>): List<ChannelRoute> = synchronized(lock) {
        channels.asSequence()
            .flatMap { it.playbackRoutes.asSequence() }
            .distinctBy { key(it) }
            .filter { entries[key(it)]?.status == Status.UNAVAILABLE }
            .toList()
    }

    /**
     * Routes to probe now. Hidden routes first (recovery), then the active group,
     * then single-route channels (where one dead route removes the whole channel),
     * then everything else. Only routes not checked since [staleBefore] are included
     * for the non-hidden buckets.
     */
    fun routesNeedingProbe(
        channels: List<Channel>,
        staleBefore: Long,
        priorityGroup: String,
        retryBefore: Long = staleBefore,
    ): List<ChannelRoute> = synchronized(lock) {
        val all = channels.asSequence()
            .flatMap { it.playbackRoutes.asSequence() }
            .distinctBy { key(it) }
            .toList()
        val channelByRoute = HashMap<String, Channel>(all.size)
        val singleKeys = HashSet<String>()
        channels.forEach { channel ->
            val routes = channel.playbackRoutes
            routes.forEach { route -> channelByRoute.putIfAbsent(key(route), channel) }
            if (routes.size == 1) singleKeys.add(key(routes.first()))
        }
        val hidden = all.filter { entries[key(it)]?.status == Status.UNAVAILABLE }
        val hiddenKeys = hidden.mapTo(HashSet()) { key(it) }
        // A single timeout is not hidden yet. Retry it sooner than the 30-minute
        // sweep so two consecutive failures can hide a no-data stream promptly.
        val transient = all.filter {
            val entry = entries[key(it)]
            entry != null && entry.status != Status.UNAVAILABLE && entry.failures > 0 &&
                entry.checkedAt < retryBefore && key(it) !in hiddenKeys
        }
        val transientKeys = transient.mapTo(HashSet()) { key(it) }
        val stale = all.filter {
            val entry = entries[key(it)]
            key(it) !in hiddenKeys && key(it) !in transientKeys &&
                (entry == null || entry.checkedAt < staleBefore)
        }
        val priority = if (priorityGroup.isBlank()) emptyList() else stale.filter {
            channelByRoute[key(it)]?.groupName == priorityGroup
        }
        val priorityKeys = priority.mapTo(HashSet()) { key(it) }
        val single = stale.filter { key(it) in singleKeys && key(it) !in priorityKeys }
        val singleKeysSet = single.mapTo(HashSet()) { key(it) }
        val rest = stale.filterNot { key(it) in priorityKeys || key(it) in singleKeysSet }
        hidden + transient + priority + single + rest
    }

    fun markProbeSuccess(route: ChannelRoute, now: Long = now()) {
        var notify: Boolean
        synchronized(lock) {
            val old = entries[key(route)]
            entries[key(route)] = Entry(Status.AVAILABLE, 0, now)
            // UNKNOWN -> AVAILABLE does not change visibility; do not wake the UI
            // for every healthy route in a background sweep.
            notify = old?.status == Status.UNAVAILABLE
        }
        if (notify) changed()
    }

    fun markProbeFailure(route: ChannelRoute, definitive: Boolean, now: Long = now()) {
        var notify: Boolean
        synchronized(lock) {
            val k = key(route)
            val old = entries[k]
            val failures = if (definitive) (old?.failures ?: 0) + 2 else (old?.failures ?: 0) + 1
            val status = if (definitive || failures >= 2) Status.UNAVAILABLE else old?.status ?: Status.UNKNOWN
            entries[k] = Entry(status, failures, now)
            notify = status != old?.status
        }
        if (notify) changed()
    }

    fun markPlaybackSuccess(route: ChannelRoute) = markProbeSuccess(route)

    fun markPlaybackDefinitiveFailure(route: ChannelRoute) = markProbeFailure(route, definitive = true)

    /** Tests and source refresh use this to forget stale URLs/tokens. */
    fun clear() {
        val had = synchronized(lock) {
            val nonEmpty = entries.isNotEmpty()
            entries.clear()
            nonEmpty
        }
        if (had) changed()
    }

    private fun now(): Long = android.os.SystemClock.elapsedRealtime()
}
