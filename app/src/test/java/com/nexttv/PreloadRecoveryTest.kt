package com.nexttv

import com.nexttv.core.tv.PreloadPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PreloadRecoveryTest {
    @Test fun searchesBothDirectionsAndWrapsWithoutRevisitingCurrent() {
        assertEquals(listOf(0, 1, 2), PreloadPolicy.indices(5, 4, 1, 3).toList())
        assertEquals(listOf(4, 3, 2), PreloadPolicy.indices(5, 0, -1, 3).toList())
        assertEquals(listOf(1), PreloadPolicy.indices(2, 0, -1, 3).toList())
    }

    @Test fun prefersKnownRouteAndRetriesOnlySingleRoute() {
        assertEquals(listOf(0, 0), PreloadPolicy.routeAttempts(1, 0))
        assertEquals(listOf(2, 0, 1), PreloadPolicy.routeAttempts(4, 2))
    }
}
