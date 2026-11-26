package com.nexttv

import com.nexttv.core.tv.RouteLatency
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteLatencyToneTest {
    @Test fun thresholdsSeparateFastUsableAndSlowRoutes() {
        assertEquals(RouteLatency.Tone.GOOD, RouteLatency.tone(0))
        assertEquals(RouteLatency.Tone.GOOD, RouteLatency.tone(150))
        assertEquals(RouteLatency.Tone.FAIR, RouteLatency.tone(151))
        assertEquals(RouteLatency.Tone.FAIR, RouteLatency.tone(300))
        assertEquals(RouteLatency.Tone.SLOW, RouteLatency.tone(301))
    }
}
