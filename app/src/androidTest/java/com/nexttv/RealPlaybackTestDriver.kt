package com.nexttv

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.nexttv.core.App
import com.nexttv.core.tv.*

/** Shared real-media test lifecycle. Accessibility queries never run inside a main-thread wait. */
internal class RealPlaybackTestDriver(private val instrumentation:Instrumentation):AutoCloseable {
 private val preferences=instrumentation.targetContext.getSharedPreferences("nexttv",0)
 private val saved=preferences.all.toMap()
 lateinit var activity:TvActivity;private set
 fun openHome(){
  preferences.edit().putBoolean("start_live",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("previews",false).putInt("preload_mode",0).commit()
  activity=instrumentation.startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
  check(awaitMain("cache ready"){App.get().cacheReady})
 }
 fun awaitMain(label:String,timeout:Long=20000,condition:()->Boolean):Boolean{
  val end=SystemClock.uptimeMillis()+timeout
  while(SystemClock.uptimeMillis()<end){var ready=false;instrumentation.runOnMainSync{ready=condition()};if(ready)return true;SystemClock.sleep(60)}
  return false
 }
 fun click(text:String){
  fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
  val end=SystemClock.uptimeMillis()+15000;var n:AccessibilityNodeInfo?=null
  while(n==null&&SystemClock.uptimeMillis()<end){n=find(instrumentation.uiAutomation.rootInActiveWindow);if(n==null)SystemClock.sleep(80)}
  var item=n?:error("real channel missing: $text");while(!item.isClickable)item=item.parent?:error("not clickable: $text")
  check(item.performAction(AccessibilityNodeInfo.ACTION_CLICK))
 }
 fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
 override fun close(){
  instrumentation.runOnMainSync{if(::activity.isInitialized)activity.finish()};instrumentation.waitForIdleSync()
  val edit=preferences.edit().clear();saved.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Boolean->edit.putBoolean(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit()
 }
}
