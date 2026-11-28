// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import com.nexttv.core.data.ChannelRoute

/**
 * Sizes one background probe pass. The budget is deliberately tiny: preloading
 * already holds live streams, and a background sweep must never push the TV's
 * uplink into a burst that delays the foreground tune.
 */
internal object ProbeBatchPolicy {
    internal data class Budget(
        val hiddenLimit: Int,
        val scanLimit: Int,
        val concurrency: Int,
        val allowFastPass: Boolean,
        val deferred: Boolean = false,
    ) {
        val total: Int get() = hiddenLimit + scanLimit
    }

    private const val MAX_BACKGROUND_REQUESTS = 3

    fun budget(
        mode: ProbeGatePolicy.Mode,
        fastPass: Boolean,
        activePreloads: Int,
    ): Budget {
        val room = (MAX_BACKGROUND_REQUESTS - activePreloads).coerceAtLeast(0)
        if (room == 0) return Budget(0, 0, 0, allowFastPass = false, deferred = true)
        // A fast pass is a launch-time burst; never run it while preloading owns
        // connections, and never let it starve hidden recovery (one slot always).
        val fast = fastPass && activePreloads == 0 && mode == ProbeGatePolicy.Mode.IDLE
        val preloadLimited = activePreloads > 0
        val hidden = when {
            fast -> 1
            mode == ProbeGatePolicy.Mode.IDLE -> if (preloadLimited) 2 else 4
            else -> if (preloadLimited) 1 else 2
        }
        val total = when {
            fast -> if (preloadLimited) 8 else 24
            mode == ProbeGatePolicy.Mode.IDLE -> if (preloadLimited) 8 else 40
            else -> if (preloadLimited) 4 else 12
        }
        val normalConcurrency = when {
            fast -> 3
            mode == ProbeGatePolicy.Mode.IDLE -> 2
            else -> 1
        }
        return Budget(
            hiddenLimit = hidden,
            scanLimit = (total - hidden).coerceAtLeast(0),
            concurrency = minOf(normalConcurrency, room),
            allowFastPass = fast,
        )
    }

    /** Rotates hidden routes so an interrupted pass cannot starve the tail. */
    fun rotateHidden(
        hidden: List<ChannelRoute>,
        cursor: Int,
        limit: Int,
    ): Pair<List<ChannelRoute>, Int> {
        if (hidden.isEmpty() || limit <= 0) return emptyList<ChannelRoute>() to 0
        val start = ((cursor % hidden.size) + hidden.size) % hidden.size
        val take = minOf(limit, hidden.size)
        val batch = (0 until take).map { offset -> hidden[(start + offset) % hidden.size] }
        return batch to ((start + take) % hidden.size)
    }
}
