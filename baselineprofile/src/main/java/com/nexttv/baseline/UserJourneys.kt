package com.nexttv.baseline

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** Shared journeys for profile generation and future regression benchmarks. No bundled playlist. */
internal object UserJourneys {
 const val PACKAGE="com.nexttv"
 fun MacrobenchmarkScope.awaitHome(){
  check(device.wait(Until.hasObject(By.text("全部频道")),15000)==true){"Profile generation requires the configured real subscription and Home startup"}
  check(device.wait(Until.hasObject(By.text("CCTV1综合")),15000)==true){"Real test subscription not present"}
 }
 fun MacrobenchmarkScope.browse(){
  device.pressDPadDown();device.pressDPadDown()
  repeat(6){device.pressDPadDown();Thread.sleep(90)}
  repeat(6){device.pressDPadUp();Thread.sleep(90)}
  device.pressBack();Thread.sleep(700)
  device.pressDPadRight();device.pressDPadRight();device.pressDPadDown()
  repeat(5){device.pressDPadDown();Thread.sleep(90)}
  device.pressBack();Thread.sleep(700)
  device.pressDPadLeft();device.pressDPadLeft();Thread.sleep(300)
 }
 fun MacrobenchmarkScope.playback(){
  check(device.wait(Until.hasObject(By.text("CCTV1综合")),10000)==true)
  device.findObject(By.text("CCTV1综合")).click();Thread.sleep(10000)
  device.pressDPadCenter();Thread.sleep(400);device.pressDPadRight();Thread.sleep(400);device.pressBack();Thread.sleep(400)
 }
}
