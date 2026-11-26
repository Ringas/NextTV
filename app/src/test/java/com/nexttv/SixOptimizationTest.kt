package com.nexttv
import com.nexttv.core.data.XmltvTimeParser
import com.nexttv.core.ui.logoDiskKey
import com.nexttv.core.tv.softwareVideoBudget
import com.nexttv.core.tv.PreloadPolicy
import org.junit.Assert.*
import org.junit.Test
class SixOptimizationTest {
 @Test fun xmltvOffsets(){val p=XmltvTimeParser();assertEquals(p.parse("20261120120000 +0800"),p.parse("20261120040000 Z"));assertEquals(p.parse("20261120040000 +0000"),p.parse("20261119230000 -0500"))}
 @Test fun parserDoesNotLeakPreviousTimezone(){val p=XmltvTimeParser();val expected=p.parse("20261120120000");p.parse("20261120120000 +0900");assertEquals(expected,p.parse("20261120120000"))}
 @Test fun invalidCalendarDatesAreRejected(){val p=XmltvTimeParser();assertNull(p.parse("20260230000000 +0000"));assertNull(p.parse("20261120120000 +2460"));assertNull(p.parse("2026112012000x +0800"));assertNull(p.parse("20261120120000 rubbish"))}
 @Test fun leapYear(){val p=XmltvTimeParser();assertNotNull(p.parse("20240229000000 +0000"));assertNull(p.parse("20230229000000 +0000"))}
 @Test fun oldHashCollisionsRemainDistinct(){assertEquals("Aa".hashCode(),"BB".hashCode());assertNotEquals(logoDiskKey("Aa"),logoDiskKey("BB"));assertEquals(64,logoDiskKey("Aa").length)}
 @Test fun keyIsDeterministic(){assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",logoDiskKey("abc"))}
 @Test fun lowMemoryReducesBufferNotDirection(){assertEquals(listOf(1,-1),PreloadPolicy.directions(3));assertEquals(listOf(-1),PreloadPolicy.directions(2));assertEquals(listOf(1),PreloadPolicy.directions(1));assertTrue(PreloadPolicy.directions(0).isEmpty());assertEquals(0L,PreloadPolicy.bufferMillis(96));assertEquals(1200L,PreloadPolicy.bufferMillis(128));assertEquals(2500L,PreloadPolicy.bufferMillis(192))}

 @Test fun lowRamPreparesSourcesWithoutRepeatedDataLoads() {
  assertEquals(0L,PreloadPolicy.bufferMillis(256,lowRam=true))
  assertEquals(24_000L,PreloadPolicy.refreshIntervalMillis(256,lowRam=true))
  assertEquals(24_000L,PreloadPolicy.refreshIntervalMillis(96,lowRam=false))
  assertEquals(8_000L,PreloadPolicy.refreshIntervalMillis(256,lowRam=false))
  assertEquals(8_000L,PreloadPolicy.deepTtlMillis(256,lowRam=false))
  assertEquals(30_000L,PreloadPolicy.shallowTtlMillis(256,lowRam=false))
 }

 @Test fun preloadPlanDeepensOnlyFirstCandidate() {
  assertEquals(
   listOf(
    PreloadPolicy.Target(0,0,true),
    PreloadPolicy.Target(1,0,false),
    PreloadPolicy.Target(2,0,false),
   ),
   PreloadPolicy.plan(5,4,1,1),
  )
  assertEquals(
   listOf(
    PreloadPolicy.Target(3,0,true),
    PreloadPolicy.Target(2,0,false),
    PreloadPolicy.Target(1,0,false),
   ),
   PreloadPolicy.plan(5,4,1,2),
  )
 }

 @Test fun bothDirectionsFollowLastZapAndKeepOppositeShallow() {
  assertEquals(
   listOf(
    PreloadPolicy.Target(0,0,true),
    PreloadPolicy.Target(1,0,false),
    PreloadPolicy.Target(2,0,false),
    PreloadPolicy.Target(3,1,false),
   ),
   PreloadPolicy.plan(5,4,1,3),
  )
  assertEquals(
   listOf(
    PreloadPolicy.Target(3,0,true),
    PreloadPolicy.Target(2,0,false),
    PreloadPolicy.Target(1,0,false),
    PreloadPolicy.Target(0,1,false),
   ),
   PreloadPolicy.plan(5,4,-1,3),
  )
 }

 @Test fun smallChannelListDoesNotDuplicateCandidates() {
  assertEquals(listOf(PreloadPolicy.Target(1,0,true)),PreloadPolicy.plan(2,0,1,3))
  assertTrue(PreloadPolicy.plan(5,4,1,0).isEmpty())
 }

 @Test fun softwareFallbackBudget(){assertFalse(softwareVideoBudget("video/hevc",3840,2160,30f,512,8,false));assertFalse(softwareVideoBudget("video/hevc",1920,1080,30f,128,8,false));assertFalse(softwareVideoBudget("video/hevc",1920,1080,30f,256,2,false));assertTrue(softwareVideoBudget("video/hevc",1920,1080,30f,256,4,false));assertTrue(softwareVideoBudget("video/avc",1280,720,30f,192,2,false))}
}
