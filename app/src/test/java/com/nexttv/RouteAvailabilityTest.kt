package com.nexttv

import com.nexttv.core.data.ChannelRoute
import com.nexttv.core.tv.RouteAvailability
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RouteAvailabilityTest {
    private fun route(n: Int) = ChannelRoute(
        url = "https://example.test/live/$n.m3u8",
        sourceId = "source",
        originalId = "$n",
    )

    @Before fun reset() {
        RouteAvailability.clear()
    }

    @Test fun hidesConfirmedDeadRouteButKeepsSlowRoutesVisible() {
        val a = route(1)
        val b = route(2)
        val c = route(3)
        RouteAvailability.markFailed(a, now = 1_000L)
        assertEquals(listOf(b, c), RouteAvailability.visible(listOf(a, b, c), true, 2_000L))
        assertEquals(listOf(1, 2), RouteAvailability.visibleIndices(listOf(a, b, c), true, 2_000L))
        assertEquals(listOf(a, b, c), RouteAvailability.visible(listOf(a, b, c), false, 2_000L))
    }

    @Test fun allDeadRoutesRemainVisibleAsFallback() {
        val routes = listOf(route(1), route(2))
        routes.forEach { RouteAvailability.markFailed(it, now = 1_000L) }
        assertEquals(routes, RouteAvailability.visible(routes, true, 2_000L))
    }

    @Test fun healthyProbeClearsFailureAndExpiryRestoresRoute() {
        val route = route(1)
        RouteAvailability.markFailed(route, now = 1_000L)
        RouteAvailability.markHealthy(route)
        assertEquals(listOf(route), RouteAvailability.visible(listOf(route), true, 2_000L))
        RouteAvailability.markFailed(route, now = 1_000L)
        assertEquals(
            listOf(route),
            RouteAvailability.visible(listOf(route), true, 1_000L + RouteAvailability.TTL_MS + 1),
        )
    }
}
