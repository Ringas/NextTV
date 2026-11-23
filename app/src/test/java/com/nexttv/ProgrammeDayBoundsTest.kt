package com.nexttv

import com.nexttv.core.data.programmeDayBounds
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgrammeDayBoundsTest {
    @Test
    fun followsLocalMidnightThroughDaylightSavingTransitions() {
        val zone = ZoneId.of("America/New_York")
        for ((date, hours) in listOf(
            LocalDate.of(2026, 3, 8) to 23,
            LocalDate.of(2026, 11, 1) to 25,
            LocalDate.of(2026, 11, 2) to 24,
        )) {
            val (start, end) = programmeDayBounds(date, zone)
            assertEquals(hours * 3_600_000L, end - start)
            assertEquals(programmeDayBounds(date.plusDays(1), zone).first, end)
        }
    }
}
