package com.nexttv
import com.nexttv.core.tv.replaySeekTarget
import com.nexttv.core.tv.replayElapsed
import com.nexttv.core.tv.replayHoldMultiplier
import org.junit.Assert.*
import org.junit.Test
class ReplaySeekFeedbackTest {
 @Test fun rewindClampsAtStart(){assertEquals(0L,replaySeekTarget(10000,-30000,60000))}
 @Test fun forwardStaysBeforeEnd(){assertEquals(59000L,replaySeekTarget(40000,30000,60000))}
 @Test fun ordinarySeekKeepsOffset(){assertEquals(70000L,replaySeekTarget(40000,30000,120000))}
 @Test fun elapsedSupportsLongProgrammes(){assertEquals("01:30",replayElapsed(90000));assertEquals("1:01:01",replayElapsed(3661000))}
 @Test fun holdRampsMultiplierWithoutFixedThirtySeconds(){
  assertEquals(1,replayHoldMultiplier(0));assertEquals(1,replayHoldMultiplier(699))
  assertEquals(2,replayHoldMultiplier(700));assertEquals(2,replayHoldMultiplier(1599))
  assertEquals(4,replayHoldMultiplier(1600));assertEquals(4,replayHoldMultiplier(2999))
  assertEquals(8,replayHoldMultiplier(3000))
 }
}
