// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import com.nexttv.core.data.RouteRecovery

/**
 * Owns route-exhaustion and recovery-budget bookkeeping for a [PlaybackSession].
 * Tune/attempt still lives on the session; this isolates reset/clear/route policy state.
 */
internal class PlaybackRecovery(private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    val budget = RecoveryBudget(clock)
    val liveRoutes = RouteRecovery()
    val replayRoutes = RouteRecovery()
    val exhausted = mutableSetOf<String>()
    val videoFailedRoutes = mutableSetOf<Int>()
    val audioRecoveryAttempts = mutableSetOf<String>()

    fun resetForNetworkRestore(routeIndex: Int, replayRouteIndex: Int) {
        exhausted.clear()
        budget.reset()
        videoFailedRoutes.clear()
        liveRoutes.reset(routeIndex)
        replayRoutes.reset(replayRouteIndex)
    }

    fun resetForNewTune(routeIndex: Int) {
        exhausted.clear()
        liveRoutes.reset(routeIndex)
    }

    fun resetForReplay(startIndex: Int) {
        exhausted.clear()
        replayRoutes.reset(startIndex)
        liveRoutes.reset(startIndex)
    }

    fun markVideoFailed(routeIndex: Int) {
        videoFailedRoutes += routeIndex
    }

    fun clearSessionFailures() {
        exhausted.clear()
        videoFailedRoutes.clear()
        audioRecoveryAttempts.clear()
        budget.reset()
    }
}
