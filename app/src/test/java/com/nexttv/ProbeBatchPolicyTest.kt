package com.nexttv

import com.nexttv.core.data.ChannelRoute
import com.nexttv.core.tv.ProbeBatchPolicy
import com.nexttv.core.tv.ProbeGatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeBatchPolicyTest {
    private fun route(n: Int) = ChannelRoute(
        url = "https://example.test/live/$n.m3u8",
        sourceId = "source",
        originalId = "$n",
    )

    @Test fun idleFastPassKeepsOneHiddenSlotAndThreeWorkers() {
        val budget = ProbeBatchPolicy.budget(ProbeGatePolicy.Mode.IDLE, fastPass = true, activePreloads = 0)
        assertEquals(1, budget.hiddenLimit)
        assertEquals(23, budget.scanLimit)
        assertEquals(24, budget.total)
        assertEquals(3, budget.concurrency)
        assertTrue(budget.allowFastPass)
    }

    @Test fun preloadingDisablesFastPassAndShrinksTheBatch() {
        val one = ProbeBatchPolicy.budget(ProbeGatePolicy.Mode.IDLE, fastPass = true, activePreloads = 1)
        assertFalse(one.allowFastPass)
        assertEquals(2, one.hiddenLimit)
        assertEquals(8, one.total)
        assertEquals(2, one.concurrency)

        val two = ProbeBatchPolicy.budget(ProbeGatePolicy.Mode.PLAYING, fastPass = false, activePreloads = 2)
        assertEquals(1, two.hiddenLimit)
        assertEquals(4, two.total)
        assertEquals(1, two.concurrency)
    }

    @Test fun threePreloadsDeferAllProbes() {
        val budget = ProbeBatchPolicy.budget(ProbeGatePolicy.Mode.PLAYING, fastPass = false, activePreloads = 3)
        assertTrue(budget.deferred)
        assertEquals(0, budget.concurrency)
    }

    @Test fun hiddenRotationCoversEveryRouteWithoutStarvation() {
        val routes = (1..5).map(::route)
        var cursor = 0
        val seen = mutableListOf<ChannelRoute>()
        repeat(5) {
            val (batch, next) = ProbeBatchPolicy.rotateHidden(routes, cursor, 2)
            seen += batch
            cursor = next
        }
        assertEquals(routes.toSet(), seen.toSet())
        assertEquals(routes, seen.distinct())
    }

    @Test fun rotationWrapsAfterTheTail() {
        val routes = (1..3).map(::route)
        val (batch, next) = ProbeBatchPolicy.rotateHidden(routes, cursor = 2, limit = 2)
        assertEquals(listOf(routes[2], routes[0]), batch)
        assertEquals(1, next)
    }
}
