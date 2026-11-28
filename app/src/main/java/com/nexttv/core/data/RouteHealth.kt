// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data
import androidx.core.content.edit

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
    /**
     * UNKNOWN stays visible because a route we have not checked is not dead.
     * SUSPECT also stays visible: it failed a soft probe once, but a timeout or
     * a transient 5xx must never remove a channel before playback can try it.
     */
    internal enum class Status { UNKNOWN, AVAILABLE, SUSPECT, UNAVAILABLE }

    private const val STORE = "route_health"
    private const val HIDDEN_KEY = "hidden"
    private const val MAX_PERSISTED = 512
    private const val MAX_MEMORY = 8192
    private val hashKey = Regex("[0-9a-f]{64}")

    private data class Entry(
        val status: Status,
        val failures: Int,
        val checkedAt: Long,
        val version: Long,
        val sourceId: String? = null,
    )

    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    private var store: android.content.SharedPreferences? = null
    private var versionSeq = 0L

    @Volatile
    var revision: Int = 0
        private set

    /** ChannelRepository mirrors this into Compose state; no Compose dependency here. */
    @Volatile
    var onChanged: (() -> Unit)? = null

    private fun key(route: ChannelRoute): String = route.healthKey

    /** Restore confirmed dead routes before the first home frame, without probing. */
    fun attach(context: android.content.Context) {
        val prefs = context.getSharedPreferences(STORE, android.content.Context.MODE_PRIVATE)
        synchronized(lock) {
            store = prefs
            entries.clear()
            prefs.getStringSet(HIDDEN_KEY, emptySet()).orEmpty().forEach { encoded ->
                decode(encoded)?.takeIf(hashKey::matches)?.let { entries[it] = Entry(Status.UNAVAILABLE, 2, 0L, 0L) }
            }
            // Rewriting drops pre-hash keys that could still contain raw credentials.
            persist()
        }
    }

    private fun persist() {
        val target = store ?: return
        val snapshot = synchronized(lock) {
            entries.asSequence()
                .filter { hashKey.matches(it.key) && it.value.status == Status.UNAVAILABLE }
                .sortedByDescending { it.value.checkedAt }
                .map { encode(it.key) }
                .take(MAX_PERSISTED)
                .toSet()
        }
        target.edit { putStringSet(HIDDEN_KEY, snapshot) }
    }

    private fun encode(value: String): String = android.util.Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
    )

    private fun decode(value: String): String? = runCatching {
        String(
            android.util.Base64.decode(
                value,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
            ),
            Charsets.UTF_8,
        )
    }.getOrNull()

    private fun changed() {
        revision++
        persist()
        onChanged?.invoke()
    }

    fun status(route: ChannelRoute): Status = synchronized(lock) {
        entries[key(route)]?.status ?: Status.UNKNOWN
    }

    fun lastCheckedAt(route: ChannelRoute): Long = synchronized(lock) {
        entries[key(route)]?.checkedAt ?: 0L
    }

    /**
     * Version of this route's health entry. A background probe captures it before
     * opening the network and passes it to [markProbeSuccess]/[markProbeFailure].
     * If playback changed the entry meanwhile, the old probe result is ignored.
     */
    fun version(route: ChannelRoute): Long = synchronized(lock) {
        entries[key(route)]?.version ?: 0L
    }

    fun isAvailable(route: ChannelRoute): Boolean = status(route) != Status.UNAVAILABLE

    /** Routes that may still be tried. SUSPECT is deliberately included. */
    fun attemptableRoutes(routes: List<ChannelRoute>): List<ChannelRoute> =
        routes.filter { isAvailable(it) }

    fun availableRoutes(routes: List<ChannelRoute>): List<ChannelRoute> =
        attemptableRoutes(routes)

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
            .sortedBy { entries[key(it)]?.checkedAt ?: 0L }
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
            .sortedBy { entries[key(it)]?.checkedAt ?: 0L }
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

    fun markProbeSuccess(
        route: ChannelRoute,
        now: Long = now(),
        expectedVersion: Long? = null,
    ) {
        var notify = false
        synchronized(lock) {
            val old = entries[key(route)]
            if (expectedVersion != null && (old?.version ?: 0L) != expectedVersion) return@synchronized
            entries[key(route)] = Entry(Status.AVAILABLE, 0, now, ++versionSeq, route.sourceId)
            trimMemoryLocked()
            // UNKNOWN/SUSPECT -> AVAILABLE does not change visibility; do not wake
            // the UI for every healthy route in a background sweep.
            notify = old?.status == Status.UNAVAILABLE
        }
        if (notify) changed()
    }

    fun markProbeFailure(
        route: ChannelRoute,
        definitive: Boolean,
        now: Long = now(),
        expectedVersion: Long? = null,
    ) {
        var notify = false
        synchronized(lock) {
            val k = key(route)
            val old = entries[k]
            if (expectedVersion != null && (old?.version ?: 0L) != expectedVersion) return@synchronized
            val failures = if (definitive) (old?.failures ?: 0) + 2 else (old?.failures ?: 0) + 1
            val status = when {
                definitive || failures >= 2 -> Status.UNAVAILABLE
                else -> Status.SUSPECT
            }
            entries[k] = Entry(status, failures, now, ++versionSeq, route.sourceId)
            trimMemoryLocked()
            notify = status != old?.status
        }
        if (notify) changed()
    }

    fun markPlaybackSuccess(route: ChannelRoute) = markProbeSuccess(route)

    fun markPlaybackDefinitiveFailure(route: ChannelRoute) = markProbeFailure(route, definitive = true)

    /** Drop health for one deleted source without clearing unrelated routes. */
    fun clearSource(sourceId: String) {
        val had = synchronized(lock) {
            val keys = entries.filterValues { it.sourceId == sourceId }.keys.toList()
            if (keys.isEmpty()) false else {
                keys.forEach(entries::remove)
                true
            }
        }
        if (had) changed()
    }

    /** Cap in-memory history; hidden routes are kept so they can recover. */
    private fun trimMemoryLocked() {
        if (entries.size <= MAX_MEMORY) return
        val excess = entries.size - MAX_MEMORY
        entries.entries.asSequence()
            .filter { it.value.status != Status.UNAVAILABLE }
            .sortedBy { it.value.checkedAt }
            .take(excess)
            .map { it.key }
            .toList()
            .forEach(entries::remove)
    }

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
