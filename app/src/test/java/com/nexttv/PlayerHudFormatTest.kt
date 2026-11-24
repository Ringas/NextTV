// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PlayerHudFormatTest {
    @Test fun playbackClockUsesCompactMinutesBelowAnHour() {
        assertEquals("0:00", Ui.playbackTime(0))
        assertEquals("1:05", Ui.playbackTime(65_000))
        assertEquals("59:59", Ui.playbackTime(3_599_999))
    }

    @Test fun playbackClockAddsHoursAndClampsNegativeInput() {
        assertEquals("1:00:00", Ui.playbackTime(3_600_000))
        assertEquals("2:03:04", Ui.playbackTime(7_384_000))
        assertEquals("0:00", Ui.playbackTime(-5_000))
    }

    @Test fun playerInfoShowsChineseDateAndWeekday() {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        try {
            val now = Calendar.getInstance().apply {
                set(2026, Calendar.NOVEMBER, 24, 10, 30, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            assertEquals("11月24日", Ui.monthDay(now))
            assertEquals("星期二", Ui.weekday(now))
            assertEquals("星期二 11月24日", "${Ui.weekday(now)} ${Ui.monthDay(now)}")
            assertEquals("10:30", Ui.hhmm(now))
        } finally {
            TimeZone.setDefault(old)
        }
    }
}
