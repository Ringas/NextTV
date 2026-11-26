package com.nexttv

import com.nexttv.core.tv.FirstFrameWatchdogPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirstFrameWatchdogPolicyTest {
    @Test fun productionBudgetAllowsSlowFirstFrameButCapsAtThirtySeconds() {
        assertEquals(30_000L, FirstFrameWatchdogPolicy.hardWaitMillis(45_000L))
        assertEquals(10_000L, FirstFrameWatchdogPolicy.zeroDataWaitMillis(45_000L))
        assertNull(FirstFrameWatchdogPolicy.failure(20_000L, 500L, 45_000L))
        assertEquals(
            "FIRST_FRAME_OR_BUFFER_TIMEOUT",
            FirstFrameWatchdogPolicy.failure(30_000L, 500L, 45_000L)?.code,
        )
    }

    @Test fun noDataFailsBeforeHardDeadline() {
        assertEquals(
            "FIRST_FRAME_NO_DATA",
            FirstFrameWatchdogPolicy.failure(1_000L, 10_000L, 45_000L)?.code,
        )
    }

    @Test fun shortenedInstrumentationBudgetScalesDown() {
        assertEquals(4_000L, FirstFrameWatchdogPolicy.hardWaitMillis(4_000L))
        assertEquals(1_333L, FirstFrameWatchdogPolicy.zeroDataWaitMillis(4_000L))
        assertEquals(
            "FIRST_FRAME_NO_DATA",
            FirstFrameWatchdogPolicy.failure(1_500L, 1_500L, 4_000L)?.code,
        )
    }
}
