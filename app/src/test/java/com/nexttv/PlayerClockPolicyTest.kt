// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.tv.PLAYER_CLOCK_ALWAYS
import com.nexttv.core.tv.PLAYER_CLOCK_HALF_HOURLY
import com.nexttv.core.tv.PLAYER_CLOCK_HIDDEN
import com.nexttv.core.tv.PLAYER_CLOCK_HOURLY
import com.nexttv.core.tv.playerClockVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PlayerClockPolicyTest {
    private fun at(hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            set(2026, Calendar.NOVEMBER, 24, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test fun hiddenIsDefaultAndAlwaysStaysVisible() {
        assertFalse(playerClockVisible(PLAYER_CLOCK_HIDDEN, at(10, 0, 5)))
        assertTrue(playerClockVisible(PLAYER_CLOCK_ALWAYS, at(10, 15, 5)))
    }

    @Test fun hourlyOnlyShowsFirstTwentySecondsOfTheHour() {
        assertTrue(playerClockVisible(PLAYER_CLOCK_HOURLY, at(10, 0, 5)))
        assertTrue(playerClockVisible(PLAYER_CLOCK_HOURLY, at(10, 0, 19)))
        assertFalse(playerClockVisible(PLAYER_CLOCK_HOURLY, at(10, 0, 25)))
        assertFalse(playerClockVisible(PLAYER_CLOCK_HOURLY, at(10, 1, 5)))
    }

    @Test fun halfHourlyShowsAtZeroAndThirtyMinutes() {
        assertTrue(playerClockVisible(PLAYER_CLOCK_HALF_HOURLY, at(10, 0, 10)))
        assertTrue(playerClockVisible(PLAYER_CLOCK_HALF_HOURLY, at(10, 30, 10)))
        assertFalse(playerClockVisible(PLAYER_CLOCK_HALF_HOURLY, at(10, 15, 10)))
        assertFalse(playerClockVisible(PLAYER_CLOCK_HALF_HOURLY, at(10, 30, 21)))
    }
}
