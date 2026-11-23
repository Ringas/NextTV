package com.nexttv
import com.nexttv.core.tv.advanceFocusScroll
import com.nexttv.core.tv.advanceReturnScroll
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
class FocusScrollMotionTest {
 @Test fun approachesBothDirectionsWithoutOvershoot(){
  for(sign in listOf(-1f,1f)){
   var remaining=219f*sign;var velocity=0f
   repeat(50){val next=advanceFocusScroll(remaining,velocity,.016f,450f,210f);assertTrue(abs(next.delta)<=abs(remaining)+.001f);assertTrue(next.delta*remaining>=0f);remaining-=next.delta;velocity=next.velocity}
   assertTrue(abs(remaining)<=1f)
  }
 }
 @Test fun longFramesNeverJumpAWholeRow(){
  for(frame in listOf(.016f,.05f,.2f,1f))for(direction in listOf(-1f,1f)){
   val next=advanceFocusScroll(5000f*direction,100000f*direction,frame,450f,210f)
   assertTrue(abs(next.delta)<=84f+.001f);assertTrue(abs(next.velocity)<=4050f+.001f)
  }
 }
 @Test fun retargetCarriesMomentumAndReverseRespondsImmediately(){
  val moving=advanceFocusScroll(300f,1000f,.016f,450f,210f)
  val restarted=advanceFocusScroll(300f,0f,.016f,450f,210f)
  assertTrue(moving.delta>=restarted.delta)
  val reversed=advanceFocusScroll(-200f,1000f,.016f,450f,210f)
  assertTrue(reversed.delta<0f);assertTrue(reversed.velocity<0f)
 }
 @Test fun repeatedLongFramesCatchUpWithoutSkippingWholeRows(){
  var remaining=1800f;var velocity=0f;var frames=0
  while(remaining>1f&&frames<30){
   val step=advanceReturnScroll(remaining,velocity,.09f,450f,210f)
   assertTrue(step.delta in 0f..168.001f)
   remaining-=step.delta;velocity=step.velocity;frames++
  }
  assertTrue("bounded return settles without an unbounded tween jump",remaining<=1f)
 }
 @Test fun wheelFixtureDistanceSettlesWithinFourHundredMsAtSixtyHz(){
  var remaining=1919f;var velocity=0f;var frames=0
  while(remaining>1f&&frames<24){
   val step=advanceReturnScroll(remaining,velocity,1f/60f,452f,219f)
   remaining-=step.delta;velocity=step.velocity;frames++
  }
  assertTrue("1919px wheel return must not add time versus the old 400ms budget",remaining<=1f)
 }
 @Test fun returnDirectionAndTinyRemainderRemainBounded(){
  for(dt in listOf(.016f,.09f,1f)){
   val left=advanceReturnScroll(-1900f,-100000f,dt,450f,219f)
   val right=advanceReturnScroll(1900f,100000f,dt,450f,219f)
   assertEquals(-left.delta,right.delta,.001f)
   assertTrue(abs(left.delta)<219f)
  }
  assertEquals(0f,advanceReturnScroll(-.8f,-1000f,.016f,450f,219f).delta,0f)
 }
 @Test fun ignoresSubpixelNoiseAndInvalidState(){
  assertEquals(0f,advanceFocusScroll(.9f,1000f,.016f,450f,210f).delta,0f)
  assertEquals(0f,advanceFocusScroll(Float.NaN,1f,.016f,450f,210f).delta,0f)
  assertTrue(advanceFocusScroll(200f,Float.NaN,Float.NaN,450f,210f).delta.isFinite())
 }
}
