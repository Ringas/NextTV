// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import java.util.Calendar

internal const val PLAYER_CLOCK_HIDDEN = 0
internal const val PLAYER_CLOCK_ALWAYS = 1
internal const val PLAYER_CLOCK_HOURLY = 2
internal const val PLAYER_CLOCK_HALF_HOURLY = 3
internal const val PLAYER_CLOCK_WINDOW_MS = 20_000L

internal fun playerClockTitle(mode: Int): String = when (mode) {
    PLAYER_CLOCK_ALWAYS -> "始终显示"
    PLAYER_CLOCK_HOURLY -> "每小时显示"
    PLAYER_CLOCK_HALF_HOURLY -> "每半小时显示"
    else -> "隐藏"
}

/** Hourly / half-hourly modes show the wall clock briefly at the top of the interval. */
internal fun playerClockVisible(mode: Int, now: Long): Boolean {
    if (mode == PLAYER_CLOCK_ALWAYS) return true
    if (mode != PLAYER_CLOCK_HOURLY && mode != PLAYER_CLOCK_HALF_HOURLY) return false
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    if (second * 1000L >= PLAYER_CLOCK_WINDOW_MS) return false
    return when (mode) {
        PLAYER_CLOCK_HOURLY -> minute == 0
        PLAYER_CLOCK_HALF_HOURLY -> minute == 0 || minute == 30
        else -> false
    }
}
