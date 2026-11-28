// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/**
 * Limits automatic channel hops so a source/CDN outage cannot walk through the
 * whole lineup. A hard failure may move through the visible list, but never more
 * than [MAX_CHANNEL_HOPS] channels. A soft failure may move only one, then the UI
 * waits for the user or for the network to recover.
 *
 * Small subscriptions are exhausted completely; a 70-channel lineup stays
 * bounded. This avoids the old "two hops, then report no channels" behavior
 * when a healthy channel sits just behind two dead neighbors.
 */
internal object AutoRecoveryPolicy {
    const val MAX_CHANNEL_HOPS = 8
    const val MAX_SOFT_CHANNEL_HOPS = 1

    /** Never hop past the visible list; at least one candidate remains possible. */
    fun channelHopLimit(visibleCount: Int): Int =
        minOf(MAX_CHANNEL_HOPS, (visibleCount - 1).coerceAtLeast(1))

    fun canHop(channelHops: Int, softHops: Int, definitive: Boolean, visibleCount: Int): Boolean =
        if (definitive) channelHops.coerceAtLeast(0) < channelHopLimit(visibleCount)
        else softHops.coerceAtLeast(0) < MAX_SOFT_CHANNEL_HOPS

    /** True only when the safety cap stopped a list that still has untried channels. */
    fun searchPaused(channelHops: Int, visibleCount: Int): Boolean =
        channelHops >= channelHopLimit(visibleCount) &&
            (visibleCount - 1).coerceAtLeast(0) > channelHopLimit(visibleCount)
}
