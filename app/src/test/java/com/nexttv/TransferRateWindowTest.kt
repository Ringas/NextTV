package com.nexttv
import com.nexttv.core.tv.TransferRateWindow
import org.junit.Assert.assertEquals
import org.junit.Test
class TransferRateWindowTest {
 @Test fun largerRatesKeepAReadableUnit(){assertEquals("1.0 GB/s",transferRateLabel(1073741824));assertEquals("1.0 MB/s",transferRateLabel(1048576))}
 @Test fun burstDownloadsDoNotImmediatelyBecomeAZeroRate(){
  val window=TransferRateWindow()
  assertEquals(9000L,window.add(9000,1000))
  assertEquals(4500L,window.add(0,1000))
  assertEquals(3000L,window.add(0,1000))
  assertEquals(0L,window.add(0,1000))
 }
 @Test fun sampleRateUsesElapsedTimeRatherThanAssumingOneSecond(){
  val window=TransferRateWindow()
  assertEquals(2000L,window.add(1000,500))
  assertEquals(500L,window.add(0,1500))
 }
 @Test fun changingRoutesDoesNotKeepThePreviousRouteSpeed(){
  val window=TransferRateWindow()
  window.add(10000,1000);window.clear()
  assertEquals(0L,window.add(0,1000))
 }
}
