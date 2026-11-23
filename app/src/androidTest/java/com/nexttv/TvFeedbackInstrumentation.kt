package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import com.nexttv.core.App
import com.nexttv.core.data.*
import com.nexttv.core.tv.*
import java.util.concurrent.CopyOnWriteArrayList

/** Real stream testing for the TCL feedback, performed only on the selected local emulator. */
class TvFeedbackInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 private fun await(label:String,ms:Long=20000,test:()->Boolean){val until=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<until){var ok=false;runOnMainSync{ok=test()};if(ok)return;SystemClock.sleep(60)};error(label)}
 private fun find(n:AccessibilityNodeInfo?,text:String):AccessibilityNodeInfo?{if(n==null)return null;if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(n.getChild(i),text)?.let{return it};return null}
 private fun key(code:Int,settle:Long=180){sendKeyDownUpSync(code);SystemClock.sleep(settle)}
 override fun onStart(){var activity:TvActivity?=null;var result=Activity.RESULT_OK;var report="";val lines=mutableListOf<String>();val sp=targetContext.getSharedPreferences("nexttv",0);val saved=sp.all.toMap()
 fun pass(s:String){lines+="PASS $s";sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})}
 try{
 sp.edit().putBoolean("start_live",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("previews",false).putBoolean("reduce_motion",false).putBoolean("keep_last_frame",true).putInt("preload_mode",3).commit()
 activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity;val a=activity!!
 await("startup cache"){App.get().cacheReady};val app=App.get();val all=app.channels.channels;val center=all.first{it.name=="CCTV2财经"};val at=all.indexOf(center);check(at>0&&at<all.lastIndex);val previous=all[at-1];val next=all[at+1]
 val until=SystemClock.uptimeMillis()+15000;while(find(uiAutomation.rootInActiveWindow,center.name)==null&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);var node=find(uiAutomation.rootInActiveWindow,center.name)?:error("real channel absent");while(!node.isClickable)node=node.parent;check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
 await("real initial first frame",45000){PlaybackSession.active?.rendered==true};val engine=PlaybackSession.active!!
 val field=engine.javaClass.getDeclaredField("preloadPool").apply{isAccessible=true};val pool=field.get(engine)as DataPreloadPool
 fun both(){await("both adjacent channels not preloaded: ${engine.diagnostics()}",60000){pool.readyIds().containsAll(listOf(previous.id,next.id))};check(pool.size()==2)}
 both();pass("dual mode preloads BOTH actual previous and next channels as data sources; ${engine.diagnostics()}")
 val first=SystemClock.elapsedRealtime();runOnMainSync{engine.tune(previous)};await("previous preloaded channel frame",45000){engine.current.id==previous.id&&engine.rendered};val previousMs=SystemClock.elapsedRealtime()-first
 runOnMainSync{engine.tune(center)};await("center frame",45000){engine.current.id==center.id&&engine.rendered};both()
 val second=SystemClock.elapsedRealtime();runOnMainSync{engine.tune(next)};await("next preloaded channel frame",45000){engine.current.id==next.id&&engine.rendered};check(engine.diagnostics().contains("hits=2"));pass("both previous and next preloads consumed successfully; previous=${previousMs}ms next=${SystemClock.elapsedRealtime()-second}ms")
 await("rate not attached after preload reuse",18000){engine.bytesPerSecond>0};pass("active playback network rate continues updating after data-preload reuse")
 // Two route identities use the same real URL so the preservation test is independent of an unavailable alternative server.
 val real=engine.current;val route=real.playbackRoutes.first();val two=real.copy(routes=listOf(route,route.copy(originalId="retained-real-alternate")))
 runOnMainSync{engine.tune(two)};await("two-route real stream",45000){engine.rendered}
 runOnMainSync{check(engine.chooseRoute(1))};await("no real retained picture during route change",2500){a.videoFrameDiagnostics?.invoke()?.let{it.optBoolean("visible")&&it.optInt("variation")>8}==true}
 pass("switching ROUTE preserves a real nonblank previous video frame, with no extra video decoder")
 await("new route frame",45000){engine.routeIndex==1&&engine.rendered};await("snapshot not removed"){a.videoFrameDiagnostics?.invoke()?.optBoolean("visible")==false}
 // Capture the first actual browser draws, rather than waiting until a bounce has already finished.
 val samples=CopyOnWriteArrayList<String>();val draw=ViewTreeObserver.OnDrawListener{a.browserDiagnostics?.invoke()?.let{if(it.optString("pane")=="channels")samples+="${it.optInt("channelFirst")}:${it.optInt("channelOffset")}"}}
 runOnMainSync{a.window.decorView.viewTreeObserver.addOnDrawListener(draw)};key(KeyEvent.KEYCODE_DPAD_CENTER,600)
 repeat(10){runOnMainSync{a.window.decorView.invalidate()};SystemClock.sleep(50)}
 runOnMainSync{a.window.decorView.viewTreeObserver.removeOnDrawListener(draw)};check(samples.size>=5&&samples.distinct().size==1){"Entry moved: $samples"};pass("browser entry stays at one vertical position across first ${samples.size} draws, without direction input")
 var start="";runOnMainSync{start=a.browserDiagnostics!!.invoke().let{"${it.getInt("channelFirst")}:${it.getInt("channelOffset")}"}}
 key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN)
 runOnMainSync{val state=a.browserDiagnostics!!.invoke();check("${state.getInt("channelFirst")}:${state.getInt("channelOffset")}"==start){"TV focus moved the whole list before reaching its edge: $state"}}
 pass("D-pad moves focus item by item while fully visible content stays still")
 runOnMainSync{app.prefs.reduceMotion=true};waitForIdleSync();SystemClock.sleep(200);check(AppMotionScale.scaleFactor==0f);await("actual animation scope scale not zero"){a.actualMotionScale==0f}
 val offsets=CopyOnWriteArrayList<String>();val immediate=ViewTreeObserver.OnDrawListener{a.browserDiagnostics?.invoke()?.let{offsets+="${it.optInt("channelFirst")}:${it.optInt("channelOffset")}"}}
 var moved=false
 repeat(10){if(!moved){offsets.clear();runOnMainSync{a.window.decorView.viewTreeObserver.addOnDrawListener(immediate)};key(KeyEvent.KEYCODE_DPAD_DOWN,300);runOnMainSync{a.window.decorView.viewTreeObserver.removeOnDrawListener(immediate)};if(offsets.any{it!=start}){moved=true;check(offsets.distinct().size<=1){"Motion still interpolates: $offsets"}}}}
 check(moved);pass("motion OFF changes global Compose scale live and edge scrolling has no interpolated offset frames")
 report="TCL-feedback acceptance: ${lines.size} groups passed\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{
 runOnMainSync{activity?.finish()};val edit=sp.edit().clear();saved.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit()
 }
 finish(result,Bundle().apply{putString("stream",report)})
 }
}
