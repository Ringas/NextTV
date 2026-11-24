// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
