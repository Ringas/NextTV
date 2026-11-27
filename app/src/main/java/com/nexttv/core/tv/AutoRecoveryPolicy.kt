// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/**
 * Limits automatic channel hops so a source/CDN outage cannot walk through the
 * whole lineup. A hard failure may move two channels; a soft failure may move
 * only one, then the UI waits for the user or for the network to recover.
 */
internal object AutoRecoveryPolicy {
    const val MAX_CHANNEL_HOPS = 2
    const val MAX_SOFT_CHANNEL_HOPS = 1

    fun canHop(channelHops: Int, softHops: Int, definitive: Boolean): Boolean =
        if (definitive) channelHops.coerceAtLeast(0) < MAX_CHANNEL_HOPS
        else softHops.coerceAtLeast(0) < MAX_SOFT_CHANNEL_HOPS
}
