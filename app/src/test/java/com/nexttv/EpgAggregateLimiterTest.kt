// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.data.EpgAggregateLimiter
import com.nexttv.core.data.Program
import com.nexttv.core.data.epgTextWeight
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The aggregate cap must be reserved atomically under concurrent refresh. */
class EpgAggregateLimiterTest {
    @Test fun concurrentAdmissionsNeverOverrunTheCap() {
        val limiter = EpgAggregateLimiter(maxProgrammes = 100, maxText = 100_000)
        val threads = 8
        val attemptsPerThread = 5
        val programmesPerFeed = 5
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val admitted = AtomicInteger()
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) { t ->
            pool.execute {
                start.await()
                repeat(attemptsPerThread) { i ->
                    if (limiter.admit("feed-$t-$i", programmesPerFeed, 10)) admitted.incrementAndGet()
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertEquals(100, limiter.totalProgrammes())
        assertEquals(20, admitted.get())
    }

    @Test fun admittingSameUrlReplacesPreviousReservation() {
        val limiter = EpgAggregateLimiter(maxProgrammes = 100, maxText = 10_000)
        assertTrue(limiter.admit("u1", 60, 100))
        assertTrue(limiter.admit("u2", 20, 100))
        assertEquals(80, limiter.totalProgrammes())

        assertTrue(limiter.admit("u1", 10, 100))
        assertEquals(30, limiter.totalProgrammes())
        assertTrue(limiter.admit("u3", 60, 100))
        assertEquals(90, limiter.totalProgrammes())
        assertFalse(limiter.admit("u4", 20, 100))
        assertEquals(90, limiter.totalProgrammes())
    }

    @Test fun textBudgetIsIndependentFromProgrammeBudget() {
        val limiter = EpgAggregateLimiter(maxProgrammes = 1_000, maxText = 500)
        assertTrue(limiter.admit("u1", 10, 300))
        assertFalse(limiter.admit("u2", 10, 300))
        assertEquals(300, limiter.totalText())
        assertTrue(limiter.admit("u1", 10, 100))
        assertTrue(limiter.admit("u2", 10, 400))
        assertEquals(500, limiter.totalText())
    }

    @Test fun retainDropsInactiveFeeds() {
        val limiter = EpgAggregateLimiter(maxProgrammes = 100, maxText = 10_000)
        assertTrue(limiter.admit("keep", 20, 0))
        assertTrue(limiter.admit("drop", 30, 0))
        limiter.retain(setOf("keep"))
        assertEquals(20, limiter.totalProgrammes())
        assertTrue(limiter.admit("new", 80, 0))
        assertFalse(limiter.admit("extra", 1, 0))
    }

    @Test fun textWeightCountsTitlesDescriptionsAliasesAndNames() {
        val feed = mapOf(
            "c1" to listOf(Program("c1", "title", "desc", 0, 0)),
            "c2" to listOf(Program("c2", "abc", "", 0, 0))
        )
        assertEquals(
            33L,
            epgTextWeight(feed, mapOf("alias" to "value"), mapOf("name" to "display"))
        )
    }
}
