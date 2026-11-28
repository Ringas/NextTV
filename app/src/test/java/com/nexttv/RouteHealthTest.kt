package com.nexttv

import com.nexttv.core.data.Channel
import com.nexttv.core.data.ChannelRoute
import com.nexttv.core.data.RouteHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RouteHealthTest {
    private fun route(n: Int, source: String = "source") = ChannelRoute(
        url = "https://example.test/live/$n.m3u8",
        sourceId = source,
        originalId = "$n",
    )

    private fun channel(id: String, vararg routes: ChannelRoute) = Channel(
        id = id,
        name = id,
        tvgId = id,
        logo = "",
        group = "影音文娱",
        url = routes.first().url,
        sourceId = routes.first().sourceId,
        routes = routes.toList(),
    )

    @Before fun reset() {
        RouteHealth.clear()
    }

    @Test fun singleRouteChannelDisappearsWhenItsOnlyRouteIsDead() {
        val only = route(1)
        val ch = channel("english", only)
        assertTrue(RouteHealth.isChannelAvailable(ch))
        RouteHealth.markProbeFailure(only, definitive = true, now = 1_000L)
        assertFalse(RouteHealth.isChannelAvailable(ch))
        assertTrue(RouteHealth.displayChannels(listOf(ch), hideUnavailable = true).isEmpty())
        assertTrue(RouteHealth.availableRoutes(listOf(only)).isEmpty())
    }

    @Test fun multiRouteChannelKeepsHealthyRoutesAndHidesOnlyDeadOnes() {
        val dead = route(1)
        val alive = route(2)
        val ch = channel("multi", dead, alive)
        RouteHealth.markProbeFailure(dead, definitive = true, now = 1_000L)
        assertTrue(RouteHealth.isChannelAvailable(ch))
        assertEquals(listOf(alive), RouteHealth.availableRoutes(ch.playbackRoutes))
        assertEquals(listOf(ch), RouteHealth.displayChannels(listOf(ch), hideUnavailable = true))
    }

    @Test fun temporaryFailureNeedsTwoProbesButRecoveryRestoresImmediately() {
        val only = route(1)
        val ch = channel("slow", only)
        RouteHealth.markProbeFailure(only, definitive = false, now = 1_000L)
        assertTrue(RouteHealth.isChannelAvailable(ch))
        RouteHealth.markProbeFailure(only, definitive = false, now = 2_000L)
        assertFalse(RouteHealth.isChannelAvailable(ch))
        RouteHealth.markProbeSuccess(only, now = 3_000L)
        assertTrue(RouteHealth.isChannelAvailable(ch))
    }

    @Test fun unknownRoutesStayVisibleUntilAProbeHidesThem() {
        val only = route(1)
        val ch = channel("unknown", only)
        assertEquals(RouteHealth.Status.UNKNOWN, RouteHealth.status(only))
        assertTrue(RouteHealth.isChannelAvailable(ch))
        assertEquals(listOf(ch), RouteHealth.displayChannels(listOf(ch), hideUnavailable = true))
        assertEquals(listOf(ch), RouteHealth.displayChannels(listOf(ch), hideUnavailable = false))
    }

    @Test fun aSingleTimeoutIsRetriedBeforeTheThirtyMinuteSweep() {
        val only = route(1)
        val ch = channel("slow-retry", only)
        RouteHealth.markProbeFailure(only, definitive = false, now = 1_000L)
        val targets = RouteHealth.routesNeedingProbe(
            channels = listOf(ch),
            staleBefore = 100_000L,
            priorityGroup = "",
            retryBefore = 2_000L,
        )
        assertEquals(listOf(only), targets)
    }

    @Test fun hiddenRoutesAreProbedBeforeUnknownOnes() {
        val dead = route(1)
        val unknown = route(2)
        val ch = channel("one", dead, unknown)
        RouteHealth.markProbeFailure(dead, definitive = true, now = 1_000L)
        val targets = RouteHealth.routesNeedingProbe(listOf(ch), staleBefore = 10_000L, priorityGroup = "影音文娱")
        assertEquals(dead, targets.first())
        assertEquals(unknown, targets.last())
    }
    @Test fun deletingSourceClearsOnlyItsHealthEntries() {
        val removed = route(1, "old")
        val kept = route(2, "new")
        RouteHealth.markProbeFailure(removed, definitive = true, now = 1_000L)
        RouteHealth.markProbeFailure(kept, definitive = true, now = 2_000L)
        RouteHealth.clearSource("old")
        assertEquals(RouteHealth.Status.UNKNOWN, RouteHealth.status(removed))
        assertEquals(RouteHealth.Status.UNAVAILABLE, RouteHealth.status(kept))
    }

    @Test fun firstSoftProbeIsVisibleSuspectNotDead() {
        val only = route(1)
        val ch = channel("suspect", only)
        RouteHealth.markProbeFailure(only, definitive = false, now = 1_000L)
        assertEquals(RouteHealth.Status.SUSPECT, RouteHealth.status(only))
        assertTrue(RouteHealth.isChannelAvailable(ch))
        assertEquals(listOf(only), RouteHealth.availableRoutes(listOf(only)))
    }

    @Test fun fastProbeTimeoutStaysVisibleEvenWhenRepeated() {
        val only = route(1)
        val ch = channel("cold-start", only)
        // Two fast passes both exceed the 2s budget. Neither may count as a
        // hide-worthy failure; the route remains visible as SUSPECT.
        RouteHealth.markProbeSuspect(only, now = 1_000L)
        RouteHealth.markProbeSuspect(only, now = 2_500L)
        assertEquals(RouteHealth.Status.SUSPECT, RouteHealth.status(only))
        assertTrue(RouteHealth.isChannelAvailable(ch))

        // A normal probe with a 5s budget is the first real confirmation. One
        // timeout there keeps it visible; the second consecutive one hides it.
        RouteHealth.markProbeFailure(only, definitive = false, now = 8_000L)
        assertTrue(RouteHealth.isChannelAvailable(ch))
        RouteHealth.markProbeFailure(only, definitive = false, now = 20_000L)
        assertFalse(RouteHealth.isChannelAvailable(ch))
    }

    @Test fun lateProbeCannotOverridePlaybackSuccess() {
        val only = route(1)
        val staleVersion = RouteHealth.version(only)
        assertTrue(RouteHealth.isChannelAvailable(channel("late", only)))
        RouteHealth.markProbeSuccess(only, now = 1_000L)
        RouteHealth.markProbeFailure(only, definitive = true, now = 2_000L, expectedVersion = staleVersion)
        assertEquals(RouteHealth.Status.AVAILABLE, RouteHealth.status(only))
        assertTrue(RouteHealth.isChannelAvailable(channel("late", only)))
    }

    @Test fun playbackSuccessClearsSuspectAndDefinitiveFailureStillWins() {
        val only = route(1)
        RouteHealth.markProbeFailure(only, definitive = false, now = 1_000L)
        assertEquals(RouteHealth.Status.SUSPECT, RouteHealth.status(only))
        RouteHealth.markProbeSuccess(only, now = 2_000L)
        assertEquals(RouteHealth.Status.AVAILABLE, RouteHealth.status(only))
        RouteHealth.markProbeFailure(only, definitive = true, now = 3_000L)
        assertEquals(RouteHealth.Status.UNAVAILABLE, RouteHealth.status(only))
        assertFalse(RouteHealth.isChannelAvailable(channel("clear", only)))
    }

    @Test fun definitiveFailureHidesAtOnceButTimeoutsNeedTwoProbes() {
        val hard = route(1)
        val slow = route(2)
        val channel = channel("mix", hard, slow)
        RouteHealth.markProbeFailure(hard, definitive = true, now = 1_000L)
        RouteHealth.markProbeFailure(slow, definitive = false, now = 1_000L)
        assertEquals(RouteHealth.Status.UNAVAILABLE, RouteHealth.status(hard))
        assertEquals(RouteHealth.Status.SUSPECT, RouteHealth.status(slow))
        assertEquals(listOf(slow), RouteHealth.attemptableRoutes(listOf(hard, slow)))
        assertEquals(listOf(slow), RouteHealth.availableRoutes(channel.playbackRoutes))
        assertTrue(RouteHealth.isChannelAvailable(channel))

        RouteHealth.markProbeFailure(slow, definitive = false, now = 2_000L)
        assertEquals(RouteHealth.Status.UNAVAILABLE, RouteHealth.status(slow))
        assertFalse(RouteHealth.isChannelAvailable(channel))
    }

}
