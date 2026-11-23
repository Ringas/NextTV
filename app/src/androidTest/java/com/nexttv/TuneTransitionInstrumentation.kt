// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv
import android.app.*
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo as N
import com.nexttv.core.App
import com.nexttv.core.tv.*

/** Actual source video; observes captured pixels and transition lifetime, never a synthetic stream. */
class TuneTransitionInstrumentation:Instrumentation(){
 private var inlineRecord=false
 override fun onCreate(b:Bundle?){super.onCreate(b);inlineRecord=b?.getString("inlineRecord","false")?.toBoolean()?:false;start()}
 private fun await(label:String,timeout:Long=45000,p:()->Boolean){val end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(p())return;SystemClock.sleep(8)};error(label)}
 override fun onStart(){val prefs=targetContext.getSharedPreferences("nexttv",0);val saved=prefs.all.toMap();var activity:Activity?=null;var recording:RawMotionRecording?=null;var code=Activity.RESULT_OK;val lines=mutableListOf<String>()
 fun pass(s:String){lines+="PASS $s";sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})}
 try{
  prefs.edit().putBoolean("start_live",false).putBoolean("keep_last_frame",true).putBoolean("reduce_motion",false).putInt("preload_mode",0).commit()
  activity=startActivitySync(android.content.Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
  fun find(n:N?):N?{if(n==null||!n.refresh())return null;if(n.text?.toString()=="CCTV1综合")return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
  await("real home"){find(uiAutomation.rootInActiveWindow)!=null};var node=find(uiAutomation.rootInActiveWindow)!!;while(!node.isClickable)node=node.parent;check(node.performAction(N.ACTION_CLICK))
  await("actual first video"){PlaybackSession.active?.rendered==true};val engine=PlaybackSession.active!!;val host=activity as TvActivity
  fun retained()=host.videoFrameDiagnostics?.invoke()
  await("initial overlay settled"){retained()?.optBoolean("retained")==false}
  if(inlineRecord)recording=RawMotionRecording.start(this,"nexttv-1.3.39-tune-motion.mp4")
  runOnMainSync{engine.zap(1);check(retained()?.optBoolean("blackCover")!=true){"Black cover obscured working old surface during capture"}}
  await("nonblank retained video",3000){retained()?.let{it.optBoolean("retained")&&it.optInt("variation")>8}==true}
  await("new channel rendered"){engine.rendered};await("old channel overlay cleared",3000){retained()?.optBoolean("retained")==false}
  pass("channel switch captures nonblank actual video and keeps it until the new channel has rendered")
  runOnMainSync{engine.zap(1)}
  await("rendered channel with fading snapshot"){engine.rendered&&retained()?.let{it.optBoolean("retained")&&it.optDouble("opacity",1.0) in .05.. .98}==true}
  runOnMainSync{
   val before=retained()!!;val version=before.optInt("captureVersion");val opacity=before.optDouble("opacity",1.0)
   engine.zap(1)
   val after=retained()!!
   check(!after.optBoolean("blackCover")){"Mid-fade retune blacked out live surface"}
   if(after.optInt("captureVersion")==version)check(after.optDouble("opacity",1.0)<=opacity+.02){"Older snapshot was brightened before fresh capture"}
  }
  await("mid-fade next channel"){engine.rendered};await("mid-fade snapshot cleared"){retained()?.optBoolean("retained")==false}
  pass("retune during fade neither blacks out a working surface nor revives an older snapshot")
  val current=engine.current;val route=current.playbackRoutes.first();val two=current.copy(routes=listOf(route,route.copy(originalId="transition-real-route")))
  runOnMainSync{engine.tune(two)};await("real two-route video"){engine.rendered};await("settled"){retained()?.optBoolean("retained")==false}
  runOnMainSync{check(engine.switchRoute(1))};await("route holds old picture",3000){retained()?.optBoolean("retained")==true};await("route rendered"){engine.rendered&&engine.routeIndex==1};await("route overlay removed",3000){retained()?.optBoolean("retained")==false}
  pass("route change uses the same retained-picture transition and clears it after first frame")
  repeat(3){runOnMainSync{engine.zap(1)};SystemClock.sleep(75)}
  await("rapid channel sequence resolves"){engine.rendered};await("rapid sequence overlay cleared",3000){retained()?.optBoolean("retained")==false}
  pass("rapid consecutive channel changes cancel outdated transition work and settle on the final stream")
  runOnMainSync{App.get().prefs.keepLastFrame=false;App.get().prefs.reduceMotion=true;engine.zap(1)}
  await("no-retention frame"){engine.rendered};await("no stale overlay",3000){retained()?.optBoolean("retained")==false}
  pass("retention OFF and reduced-motion mode still render the next stream without a stale snapshot")
 }catch(t:Throwable){code=Activity.RESULT_CANCELED;lines+=t.stackTraceToString()}finally{
  recording?.close();runOnMainSync{activity?.finish()};val edit=prefs.edit().clear();saved.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit()
 };finish(code,Bundle().apply{putString("stream",lines.joinToString("\n"))})
 }
}
