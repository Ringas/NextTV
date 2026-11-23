package com.nexttv.core.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class StableFocusScrollTest {
    @Test fun fractionalRemaindersStopInBothDirections() {
        for (distance in listOf(-1f, -.75f, -.01f, 0f, .01f, .75f, 1f)) {
            assertEquals(0f, settledScrollDistance(distance), 0f)
        }
    }
    @Test fun meaningfulMovementKeepsFocusTargetAndDirection() {
        for (distance in listOf(-300f, -1.1f, 1.1f, 300f)) {
            assertEquals(distance, settledScrollDistance(distance), 0f)
        }
    }
}
