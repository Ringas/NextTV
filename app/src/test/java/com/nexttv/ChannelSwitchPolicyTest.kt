package com.nexttv

import com.nexttv.core.tv.AutoRecoveryPolicy
import com.nexttv.core.tv.ChannelSwitchPolicy
import com.nexttv.core.tv.ProbeGatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSwitchPolicyTest {
    @Test fun downStaysDownAndNeverWrapsAtTheEnd() {
        assertEquals(listOf(4, 5, 6), ChannelSwitchPolicy.directionalIndices(7, 3, 1))
        assertTrue(ChannelSwitchPolicy.directionalIndices(7, 6, 1).isEmpty())
        assertTrue(ChannelSwitchPolicy.directionalIndices(7, 0, -1, 3).isEmpty())
    }

    @Test fun upStaysUpAndNeverWrapsAtTheStart() {
        assertEquals(listOf(2, 1, 0), ChannelSwitchPolicy.directionalIndices(7, 3, -1))
        assertEquals(listOf(0), ChannelSwitchPolicy.directionalIndices(7, 1, -1))
        assertTrue(ChannelSwitchPolicy.directionalIndices(7, 0, -1).isEmpty())
    }

    @Test fun candidateLimitPreventsWalkingTheWholeLineup() {
        assertEquals(3, ChannelSwitchPolicy.directionalIndices(90, 0, 1).size)
        assertEquals(1, ChannelSwitchPolicy.directionalIndices(90, 88, 1).size)
    }

    @Test fun hardFailureMayHopTwiceButSoftFailureOnlyOnce() {
        assertTrue(AutoRecoveryPolicy.canHop(0, 0, definitive = true))
        assertTrue(AutoRecoveryPolicy.canHop(1, 0, definitive = true))
        assertFalse(AutoRecoveryPolicy.canHop(2, 0, definitive = true))
        assertTrue(AutoRecoveryPolicy.canHop(0, 0, definitive = false))
        assertFalse(AutoRecoveryPolicy.canHop(2, 1, definitive = false))
    }

    @Test fun probeGateOnlyAllowsFullScanWhenIdle() {
        assertEquals(ProbeGatePolicy.Mode.IDLE, ProbeGatePolicy.mode(false, false, false))
        assertEquals(ProbeGatePolicy.Mode.PLAYING, ProbeGatePolicy.mode(true, false, false))
        assertEquals(ProbeGatePolicy.Mode.RECOVERING, ProbeGatePolicy.mode(true, true, false))
        assertEquals(ProbeGatePolicy.Mode.RECOVERING, ProbeGatePolicy.mode(true, false, true))
        assertTrue(ProbeGatePolicy.allowsFullScan(ProbeGatePolicy.Mode.IDLE))
        assertFalse(ProbeGatePolicy.allowsFullScan(ProbeGatePolicy.Mode.PLAYING))
        assertTrue(ProbeGatePolicy.allowsBackgroundSweep(ProbeGatePolicy.Mode.IDLE))
        assertTrue(ProbeGatePolicy.allowsBackgroundSweep(ProbeGatePolicy.Mode.PLAYING))
        assertFalse(ProbeGatePolicy.allowsBackgroundSweep(ProbeGatePolicy.Mode.RECOVERING))
        assertEquals(0, ProbeGatePolicy.concurrency(ProbeGatePolicy.Mode.RECOVERING, 8, 1))
    }
}
