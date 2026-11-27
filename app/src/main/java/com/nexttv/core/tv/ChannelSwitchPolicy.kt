// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/**
 * Direction-aware channel fallback.
 *
 * The remote's UP/DOWN direction is a user promise: a failure after zapping down
 * must keep searching downward, and a failure after zapping up must keep searching
 * upward. We deliberately do not wrap at the end of the list and never fall back
 * to a channel behind the user.
 */
internal object ChannelSwitchPolicy {
    const val MAX_CANDIDATES = 3

    fun directionalIndices(
        size: Int,
        current: Int,
        direction: Int,
        limit: Int = MAX_CANDIDATES,
    ): List<Int> {
        if (size < 2 || current !in 0 until size || limit <= 0) return emptyList()
        val step = if (direction < 0) -1 else 1
        return (1..limit).mapNotNull { offset ->
            (current + step * offset).takeIf { it in 0 until size }
        }
    }
}
