@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import com.nexttv.core.App
import com.nexttv.core.data.Source
import com.nexttv.core.tv.PlaybackSession
import kotlinx.coroutines.runBlocking
/** Debug diagnostics: validates actual decoded frames; Release UI compatibility is measured separately. */
class CompatibilityPlaybackInstrumentation:Instrumentation(){
 private var soak=10L
 private var channelName="CCTV1综合"
 override fun onCreate(b:Bundle?){super.onCreate(b);channelName=b?.getString("channel")?:"CCTV1综合";soak=b?.getString("soakSeconds")?.toLongOrNull()?.coerceIn(10,300)?:10L;start()}
 override fun onStart(){var activity:Activity?=null;var result=Activity.RESULT_OK;var report="";val original=targetContext.getSharedPreferences("nexttv",0).all.toMap()
 try{
 val sp=targetContext.getSharedPreferences("nexttv",0);sp.edit().putBoolean("start_live",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("previews",false).putInt("preload_mode",0).commit()
 activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();val app=App.get()
 if(app.channels.sources.none{it.url=="http://192.0.2.115:8787/live.m3u"})app.channels.upsertSource(Source("compat-real","兼容测试","http://192.0.2.115:8787/live.m3u"))
 check(runBlocking{app.channels.refreshAll()}.ok>0);check(app.channels.channels.isNotEmpty());val guideStart=SystemClock.uptimeMillis();check(runBlocking{app.epg.refresh(true)}){"Real guide refresh failed: ${app.epg.lastError}"};check(app.epg.hasData);val guideMs=SystemClock.uptimeMillis()-guideStart
 fun find(n:AccessibilityNodeInfo?,text:String=channelName):AccessibilityNodeInfo?{if(n==null)return null;if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(n.getChild(i),text)?.let{return it};return null}
 var prompt=find(uiAutomation.rootInActiveWindow,"Got it");if(prompt!=null){while(!prompt!!.isClickable)prompt=prompt!!.parent;check(prompt!!.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(400)}
 var end=SystemClock.uptimeMillis()+20000;while(find(uiAutomation.rootInActiveWindow)==null&&SystemClock.uptimeMillis()<end)SystemClock.sleep(150);var n=find(uiAutomation.rootInActiveWindow)?:error("Real channel card not displayed");while(!n.isClickable)n=n.parent;check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK))
 val expected=app.channels.channels.first{it.name==channelName}.id
 end=SystemClock.uptimeMillis()+45000;var rendered=false;while(!rendered&&SystemClock.uptimeMillis()<end){runOnMainSync{rendered=PlaybackSession.active?.let{it.current.id==expected&&it.rendered}==true};SystemClock.sleep(100)};check(rendered){"No decoded frame: ${PlaybackSession.active?.error}"}
 val memorySamples=mutableListOf<Int>();val frameSamples=mutableListOf<String>()
 repeat(3){SystemClock.sleep(soak*1000/3);val sample=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(sample);memorySamples+=sample.totalPss;runOnMainSync{val playing=PlaybackSession.active?:error("Player closed during soak");check(playing.current.id==expected&&playing.rendered&&playing.error.isBlank()){"Requested channel changed during soak: ${playing.current.name}"};val c=playing.player.videoDecoderCounters;c?.ensureUpdated();frameSamples+=if(c==null)"none"else"rendered=${c.renderedOutputBufferCount},dropped=${c.droppedBufferCount},maxConsecutiveDropped=${c.maxConsecutiveDroppedBufferCount}"}}
 var zapReport=""
 if(soak>10){
  val candidates=app.channels.channels.filter{it.name=="CCTV1综合"||it.name=="CCTV2财经"}.take(2);check(candidates.size==2)
  app.prefs.preloadMode=3;runOnMainSync{PlaybackSession.active!!.applyPreloadSetting()};SystemClock.sleep(1800)
  val times=mutableListOf<Long>()
  repeat(6){index->val target=candidates[index%2];val started=SystemClock.elapsedRealtime();runOnMainSync{PlaybackSession.active!!.tune(target)}
   var ok=false;val until=SystemClock.uptimeMillis()+45000
   while(!ok&&SystemClock.uptimeMillis()<until){runOnMainSync{ok=PlaybackSession.active!!.current.id==target.id&&PlaybackSession.active!!.rendered};SystemClock.sleep(60)}
   check(ok){"No frame after real zap ${target.name}"};times+=SystemClock.elapsedRealtime()-started;SystemClock.sleep(1200)
  }
  runOnMainSync{zapReport=" zapFirstFrameMs=$times runtime=${PlaybackSession.active!!.diagnostics()} pssSamplesKB=$memorySamples"}
 }
 runOnMainSync{check(PlaybackSession.active?.rendered==true);if(soak<=10)check(PlaybackSession.active?.current?.id==expected);check(PlaybackSession.active?.error.isNullOrBlank())}
 val mem=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(mem);val am=targetContext.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager;val ram=android.app.ActivityManager.MemoryInfo();am.getMemoryInfo(ram)
 val e=PlaybackSession.active!!;var format="";runOnMainSync{format="video=${e.player.videoFormat?.sampleMimeType} ${e.player.videoSize.width}x${e.player.videoSize.height} audio=${e.player.audioFormat?.sampleMimeType} guideRefreshMs=$guideMs guideLoaded=${app.epg.hasData} soakFrameCounters=$frameSamples$zapReport"}
 report="PASS API${Build.VERSION.SDK_INT} Debug requested=$channelName actual=${e.current.name} real-source decoded first frame and remained error-free for ${soak}s; $format\nMEMORY pssKB=${mem.totalPss} heapLimit=${Runtime.getRuntime().maxMemory()} memoryClassMB=${am.memoryClass} physicalRAM=${ram.totalMem} cores=${Runtime.getRuntime().availableProcessors()} lowRam=${am.isLowRamDevice}\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{runOnMainSync{activity?.finish()};val edit=targetContext.getSharedPreferences("nexttv",0).edit().clear();original.forEach{(key,v)->when(v){is Boolean->edit.putBoolean(key,v);is Int->edit.putInt(key,v);is Long->edit.putLong(key,v);is Float->edit.putFloat(key,v);is String->edit.putString(key,v);is Set<*>->edit.putStringSet(key,v.filterIsInstance<String>().toSet())}};edit.commit()}
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
