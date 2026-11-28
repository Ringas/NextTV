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

    @Test fun hardFailureCanWalkSmallLineupAndStaysBoundedOnLargeOne() {
        assertTrue(AutoRecoveryPolicy.canHop(0, 0, definitive = true, visibleCount = 7))
        assertTrue(AutoRecoveryPolicy.canHop(5, 0, definitive = true, visibleCount = 7))
        assertFalse(AutoRecoveryPolicy.canHop(6, 0, definitive = true, visibleCount = 7))
        assertTrue(AutoRecoveryPolicy.canHop(0, 0, definitive = true, visibleCount = 90))
        assertTrue(AutoRecoveryPolicy.canHop(6, 0, definitive = true, visibleCount = 90))
        assertFalse(AutoRecoveryPolicy.canHop(8, 0, definitive = true, visibleCount = 90))
        assertTrue(AutoRecoveryPolicy.canHop(0, 0, definitive = false, visibleCount = 7))
        assertFalse(AutoRecoveryPolicy.canHop(2, 1, definitive = false, visibleCount = 7))
    }

    @Test fun channelHopLimitNeverExceedsVisibleCandidates() {
        assertEquals(6, AutoRecoveryPolicy.channelHopLimit(7))
        assertEquals(8, AutoRecoveryPolicy.channelHopLimit(90))
        assertEquals(1, AutoRecoveryPolicy.channelHopLimit(1))
    }

    @Test fun searchPausedOnlyWhenCapLeavesUntriedChannels() {
        assertFalse(AutoRecoveryPolicy.searchPaused(6, 7))
        assertTrue(AutoRecoveryPolicy.searchPaused(8, 90))
        assertFalse(AutoRecoveryPolicy.searchPaused(0, 90))
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
