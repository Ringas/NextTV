package com.nexttv
import com.nexttv.core.tv.replaySeekTarget
import com.nexttv.core.tv.replayElapsed
import org.junit.Assert.*
import org.junit.Test
class ReplaySeekFeedbackTest {
 @Test fun rewindClampsAtStart(){assertEquals(0L,replaySeekTarget(10000,-30000,60000))}
 @Test fun forwardStaysBeforeEnd(){assertEquals(59000L,replaySeekTarget(40000,30000,60000))}
 @Test fun ordinarySeekKeepsOffset(){assertEquals(70000L,replaySeekTarget(40000,30000,120000))}
 @Test fun elapsedSupportsLongProgrammes(){assertEquals("01:30",replayElapsed(90000));assertEquals("1:01:01",replayElapsed(3661000))}
}
