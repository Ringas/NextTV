package com.nexttv

import android.app.*
import android.content.Intent
import android.os.*
import android.view.KeyEvent
import android.view.Choreographer
import android.view.accessibility.AccessibilityNodeInfo as Node
import com.nexttv.core.tv.TvActivity
import java.io.File
import java.util.concurrent.*

class BrowserStabilityInstrumentation: Instrumentation() {
 private lateinit var activity:TvActivity
 private fun tree(n:Node?=uiAutomation.rootInActiveWindow):String=if(n==null)""else buildString{append(n.text?:"");append('\n');for(i in 0 until n.childCount)append(tree(n.getChild(i)))}
 private fun find(label:String,n:Node?=uiAutomation.rootInActiveWindow):Node? {if(n==null)return null;if(n.text?.toString()==label)return n;for(i in 0 until n.childCount)find(label,n.getChild(i))?.let{return it};return null}
 private fun key(k:Int){sendKeyDownUpSync(k);waitForIdleSync();SystemClock.sleep(150)}
 private fun click(label:String){val until=SystemClock.uptimeMillis()+15000;while(find(label)==null&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);var n=find(label)?:error("Missing $label: ${tree()}");while(!n.isClickable)n=n.parent?:error("No action");check(n.performAction(Node.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(200)}
 private fun focusedNode(n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null)return null;if(n.isFocused)return n;for(i in 0 until n.childCount)focusedNode(n.getChild(i))?.let{return it};return null}
 // One device pixel is the production settling deadband; ongoing frame-to-frame movement is never allowed.
 private fun sameViewport(a:String,b:String):Boolean {val x=a.split(":").map{it.toInt()};val y=b.split(":").map{it.toInt()};return x[0]==y[0]&&kotlin.math.abs(x[1]-y[1])<=1}
 private fun position():String {var p="";runOnMainSync{val j=activity.browserDiagnostics!!();p="${j.getInt("epgFirst")}:${j.getInt("epgOffset")}"};return p}
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){val prefs=targetContext.getSharedPreferences("nexttv",0);val saved=prefs.all;var groups=0
  fun cleanup(){if(::activity.isInitialized){runOnMainSync{activity.finish()};waitForIdleSync()};val e=prefs.edit();for(k in listOf("selected_group","previews","auto_update","auto_update_epg")){when(val v=saved[k]){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);else->e.remove(k)}};e.commit()}
  fun pass(t:String){groups++;sendStatus(0,Bundle().apply{putString("stream","PASS $t\n")})}
  try{
   prefs.edit().putString("selected_group","").putBoolean("previews",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit()
   activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TvActivity;SystemClock.sleep(2000);val guideFresh=kotlinx.coroutines.runBlocking{com.nexttv.core.App.get().epg.refresh(true)};sendStatus(0,Bundle().apply{putString("stream","GUIDE_REFRESH real subscription result=$guideFresh\n")});click("CCTV1综合");SystemClock.sleep(1200);key(KeyEvent.KEYCODE_ENTER)
   var wait=0;while(activity.browserDiagnostics==null&&wait++<20)SystemClock.sleep(100)
   check(activity.browserDiagnostics!=null){"Browser not opened after OK: ${tree()}"}
   fun capture(expected:String,label:String,open:Boolean=true){
    if(open)key(KeyEvent.KEYCODE_DPAD_RIGHT)
    var mountWait=0;while(mountWait++<100){var mounted=false;runOnMainSync{mounted=activity.browserDiagnostics?.invoke()?.let{it.optString("pane")=="epg"&&it.optString("region")=="epg"}==true};if(mounted&&Regex("[0-9]{2}:[0-9]{2}").containsMatchIn(tree(focusedNode())))break;SystemClock.sleep(30)}
    val samples=CopyOnWriteArrayList<String>();val done=CountDownLatch(1)
    runOnMainSync{Choreographer.getInstance().postFrameCallback(object:Choreographer.FrameCallback{var left=40;override fun doFrame(t:Long){val j=activity.browserDiagnostics?.invoke();if(j?.optString("pane")=="epg")samples+="${j.getInt("epgFirst")}:${j.getInt("epgOffset")}";if(--left>0)Choreographer.getInstance().postFrameCallback(this)else done.countDown()}})}
    check(done.await(12,TimeUnit.SECONDS)){"Frame capture timeout $label"};check(samples.size>=10){"Too few EPG samples $label count=${samples.size} UI=${tree()}"};File(targetContext.filesDir,"epg-stability-$label.txt").writeText(samples.joinToString("\n"));check(samples.distinct().size==1&&samples.all{sameViewport(it,expected)}){"Jump $label expected=$expected observed=${samples.distinct()}"};pass("$label ${samples.size} frames remain stationary; viewport $expected within 1px rounding")
   }
   key(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(500);capture(position(),"initial-entry-settled",false);repeat(9){key(KeyEvent.KEYCODE_DPAD_DOWN);var state="";var owner="";runOnMainSync{owner=activity.browserDiagnostics?.invoke()?.optString("region").orEmpty();state="current=${com.nexttv.core.tv.PlaybackSession.active?.current?.name} tune=${com.nexttv.core.tv.PlaybackSession.active?.tuneSerial} browser=${activity.browserDiagnostics?.invoke()}"};check(owner=="epg"){"Programme navigation lost ownership: $state"};sendStatus(0,Bundle().apply{putString("stream","TRACE down $it $state\n")})};SystemClock.sleep(500);val held=position();check(held!="0:0"){"No actual EPG scroll occurred; insufficient guide content"};key(KeyEvent.KEYCODE_DPAD_LEFT);capture(held,"reentry-after-scrolling")
   key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_ENTER);SystemClock.sleep(350);check(sameViewport(position(),held)){"Same date resets viewport"};pass("same-date confirmation retains programme viewport")
   cleanup();finish(Activity.RESULT_OK,Bundle().apply{putString("stream","EPG stability: $groups groups passed\n")})
  }catch(t:Throwable){cleanup();finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",android.util.Log.getStackTraceString(t))})}
 }
}
