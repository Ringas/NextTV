@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import com.nexttv.core.App
import com.nexttv.core.tv.PlaybackSession
import com.nexttv.core.tv.DataPreloadPool
/** Actual real-source data preparation must complete before claiming a cache hit. */
class WarmReadyInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){var activity:Activity?=null;var result=Activity.RESULT_OK;var report="";val prefs=targetContext.getSharedPreferences("nexttv",0);val mode=prefs.getInt("preload_mode",0)
 try{
 activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));val app=App.get()
 fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(n.text?.toString()=="CCTV1综合")return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
 var end=SystemClock.uptimeMillis()+20000;while(find(uiAutomation.rootInActiveWindow)==null&&SystemClock.uptimeMillis()<end)SystemClock.sleep(100);var n=find(uiAutomation.rootInActiveWindow)?:error("CCTV1 absent");while(!n.isClickable)n=n.parent;check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK))
 fun await(label:String,condition:()->Boolean){val until=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<until){var ok=false;runOnMainSync{ok=condition()};if(ok)return;SystemClock.sleep(80)};error(label)}
 await("initial real frame"){PlaybackSession.active?.rendered==true};val engine=PlaybackSession.active!!;val target=app.channels.channels[app.channels.channels.indexOfFirst{it.id==engine.current.id}+1]
 val field=engine.javaClass.getDeclaredField("preloadPool").apply{isAccessible=true};val start=SystemClock.elapsedRealtime()
 runOnMainSync{app.prefs.preloadMode=3;engine.applyPreloadSetting()}
 await("real next channel never completed data preparation"){val pool=field.get(engine)as DataPreloadPool;check(pool.size()<=2);target.id in pool.readyIds()}
 val readyMs=SystemClock.elapsedRealtime()-start;val at=SystemClock.elapsedRealtime();runOnMainSync{engine.tune(target)};await("warm surface did not render"){engine.current.id==target.id&&engine.rendered}
 var stats="";runOnMainSync{stats=engine.diagnostics()};check(Regex("hits=([1-9]\\d*)").containsMatchIn(stats))
 report="PASS real next-channel data preparation completed after ${readyMs}ms; reuse produced real first frame in ${SystemClock.elapsedRealtime()-at}ms; $stats\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{prefs.edit().putInt("preload_mode",mode).commit();runOnMainSync{activity?.finish()}}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
