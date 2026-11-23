package com.nexttv.core.data
import org.junit.Test
import org.junit.Assert.*
class ProgrammeWindowTest {
 private fun p(start:Long,stop:Long)=Program("id","$start","",start,stop)
 @Test fun futureOnlyIsNextNotLive(){val f=p(200,300);assertEquals(null to f,programmeWindow(listOf(f),100))}
 @Test fun pastOnlyHasNoCurrentOrNext(){assertEquals(null to null,programmeWindow(listOf(p(1,50)),100))}
 @Test fun gapsHaveFutureButNoCurrent(){val next=p(200,300);assertEquals(null to next,programmeWindow(listOf(p(1,50),next),100))}
 @Test fun stopIsExclusiveAndStartInclusive(){val a=p(1,100);val b=p(100,200);assertEquals(b to null,programmeWindow(listOf(a,b),100))}
 @Test fun overlapsUseLatestCurrentAndEarliestFuture(){val a=p(50,150);val b=p(80,120);val next=p(130,200);assertEquals(b to next,programmeWindow(listOf(next,a,b,p(300,400)),100))}
 @Test fun invalidIntervalsAreIgnored(){assertEquals(null to null,programmeWindow(listOf(p(150,130),p(100,100)),100))}
 @Test fun midnightHasNoPreviousDayLeak(){val midnight=24*3600000L;val next=p(midnight+3600000,midnight+7200000);assertEquals(null to next,programmeWindow(listOf(p(midnight-60000,midnight),next),midnight))}
}
