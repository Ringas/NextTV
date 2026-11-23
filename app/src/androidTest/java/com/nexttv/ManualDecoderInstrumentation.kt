// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv

import android.app.*
import android.os.*
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlaybackException
import com.nexttv.core.tv.PlaybackSession

class ManualDecoderInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){val d=RealPlaybackTestDriver(this);var result=Activity.RESULT_OK;var report="";var groups=0
 fun pass(s:String){groups++;sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})}
 fun find(n:AccessibilityNodeInfo?,text:String):AccessibilityNodeInfo?{if(n==null)return null;runCatching{n.refresh()};if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(n.getChild(i),text)?.let{return it};return null}
 fun click(text:String){var n=find(uiAutomation.rootInActiveWindow,text)?:error("Missing $text");while(!n.isClickable)n=n.parent?:error("No clickable $text");check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(350)}
 try{
  d.openHome();d.click("CCTV1综合");check(d.awaitMain("real video",45000){PlaybackSession.active?.rendered==true});val e=PlaybackSession.active!!;val channel=e.current.id;val route=e.routeIndex
  sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);SystemClock.sleep(400);uiAutomation.takeScreenshot()?.let{bitmap->java.io.File(targetContext.filesDir,"decoder-quick.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}};click("解码");waitForIdleSync();SystemClock.sleep(600);uiAutomation.takeScreenshot()?.let{bitmap->java.io.File(targetContext.filesDir,"decoder-dialog.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}};check(find(uiAutomation.rootInActiveWindow,"解码方式")!=null);check(find(uiAutomation.rootInActiveWindow,"自动")!=null)
  sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(200);check(find(uiAutomation.rootInActiveWindow,"解码方式")==null);check(e.current.id==channel);pass("quick-menu decoder dialog opens, navigates and Back closes without changing channel")
  var choices=emptyList<PlaybackSession.DecoderChoice>();var before=0
  d.main{val all=e.decoderChoices();check(all.map{it.id}==listOf("hardware","software"));if(!all.first().enabled){val s=e.tuneSerial;check(!e.selectDecoder("hardware"));check(e.tuneSerial==s)};choices=all.filter{it.enabled};check(choices.isNotEmpty());check(!e.selectDecoder("missing-codec"));before=e.tuneSerial;check(e.selectDecoder(choices.first().id))}
  check(d.awaitMain("manual real first frame",45000){e.tuneSerial>before&&e.rendered&&e.current.id==channel&&e.routeIndex==route});check(e.selectedDecoderId==choices.first().id);pass("supported manual decoder resumes actual same-channel video: ${choices.first().detail}; ${e.diagnostics()}")
  d.main{before=e.tuneSerial;check(e.selectDecoder("auto"))};check(d.awaitMain("automatic real first frame",45000){e.tuneSerial>before&&e.rendered});check(e.selectedDecoderId=="auto");pass("automatic mode restores actual playback after manual selection")
  val original=e.current
  val linked=original.copy(routes=listOf(original.playbackRoutes.first(),original.playbackRoutes.first().copy(originalId="decoder-test-alternate")))
  d.main{e.tune(linked)};check(d.awaitMain("linked real frame",45000){e.rendered});var injectedAt=0L
  d.main{
   val target=e.player;val listener=e.javaClass.declaredMethods.first{it.name=="listen"}.apply{isAccessible=true}.invoke(e,target,e.tuneSerial)as Player.Listener
   injectedAt=SystemClock.elapsedRealtime()
   listener.onPlayerError(ExoPlaybackException.createForRenderer(IllegalStateException("injected video rejection"),"video",0,target.videoFormat!!,C.FORMAT_HANDLED,false,PlaybackException.ERROR_CODE_DECODING_FAILED))
  }
  check(d.awaitMain("route before software",45000){e.routeIndex==1&&e.rendered});check(e.diagnostics().contains("softwareAttempts=0"));pass("injected video failure selects alternate real route before forced software; recoveredMs=${SystemClock.elapsedRealtime()-injectedAt}")
  d.main{e.tune(original)};check(d.awaitMain("restored real frame",45000){e.rendered})
  val p=e.app.epg.programsFor(e.current).lastOrNull{it.stop<System.currentTimeMillis()&&e.current.playbackRoutes.any{r->com.nexttv.core.data.replayAddress(r,it,System.currentTimeMillis())!=null}}?:error("No real replay")
  d.main{check(e.playReplay(e.current,p))};check(d.awaitMain("real replay",45000){e.ready&&e.rendered});var position=0L
  d.main{e.seekReplay(45000);position=e.player.currentPosition;before=e.tuneSerial;check(e.selectDecoder(e.decoderChoices().first{it.enabled}.id))}
  check(d.awaitMain("manual replay first frame",45000){e.tuneSerial>before&&e.rendered});d.main{check(e.replay==p&&e.current.id==channel);check(e.player.currentPosition>=position-2000)};pass("manual video decoder switch retains real replay programme and seek position")
  d.main{e.player.pause();before=e.tuneSerial;check(e.selectDecoder("auto"))}
  check(d.awaitMain("paused decoder prepared",45000){e.tuneSerial>before&&e.ready});d.main{check(!e.player.playWhenReady);check(e.replay==p);e.player.play()}
  check(d.awaitMain("resumed real replay",45000){e.rendered&&e.player.isPlaying});pass("manual decoder switch preserves explicit pause and later resumes actual replay")
  report="Manual decoder: $groups groups passed\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{d.close()}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
