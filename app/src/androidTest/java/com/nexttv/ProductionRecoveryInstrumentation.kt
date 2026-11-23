package com.nexttv
import android.app.*
import android.os.*
import com.nexttv.core.App
import com.nexttv.core.tv.PlaybackSession
import com.nexttv.core.data.Program
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
/** Inject a failure at the actual production orchestrator, using the user's real live routes. */
class ProductionRecoveryInstrumentation:Instrumentation(){
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){targetContext.getSharedPreferences("nexttv",0).edit().putString("selected_group","").commit();val activity=startActivitySync(android.content.Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();val app=App.get();val saved=app.channels.channels;val oldPreload=app.prefs.preloadNext;val oldLast=app.prefs.lastChannelId;val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);var engine:PlaybackSession?=null;var count=0;var result=Activity.RESULT_OK;var report=""
  fun pass(s:String){count++;sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})}
  fun await(label:String,condition:()->Boolean){val end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){var ok=false;runOnMainSync{ok=condition()};if(ok)return;SystemClock.sleep(80)};error("Timeout $label route=${engine?.routeIndex} error=${engine?.error} rendered=${engine?.rendered}")}
  fun fail(){runOnMainSync{val e=engine!!;e.javaClass.getDeclaredMethod("failed",String::class.java,Int::class.javaPrimitiveType).apply{isAccessible=true}.invoke(e,"injected production error",e.tuneSerial)}}
  val field=app.channels.javaClass.getDeclaredField("channels").apply{isAccessible=true}
  try{
   val c=saved.first{it.playbackRoutes.size>=2}.let{it.copy(routes=listOf(it.playbackRoutes.first(),it.playbackRoutes.first().copy(originalId="injected-alternate")))};val one=c.copy(routes=listOf(c.playbackRoutes.first()),id=c.id+"single")
   fun find(n:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo?{if(n==null)return null;if(n.text?.toString()==com.nexttv.core.ui.Ui.displayName(c.name))return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
   val rootEnd=SystemClock.uptimeMillis()+15000;while(find(uiAutomation.rootInActiveWindow)==null&&SystemClock.uptimeMillis()<rootEnd)SystemClock.sleep(150);var node=find(uiAutomation.rootInActiveWindow)?:error("Missing real channel card ${c.name}; root=${uiAutomation.rootInActiveWindow}");while(!node.isClickable)node=node.parent;check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(600)
   runOnMainSync{app.prefs.preloadNext=false;field.set(app.channels,listOf(c));engine=PlaybackSession.active;check(engine!=null);engine!!.tune(c)}
   await("real first frame"){engine!!.rendered};fail();await("alternate real first frame"){engine!!.routeIndex==1&&engine!!.rendered}
   for(i in 2 until c.playbackRoutes.size){fail();await("alternate $i"){engine!!.routeIndex==i&&engine!!.rendered}}
   fail();await("exhausted despite first frames"){engine!!.error.startsWith("暂无可用频道")};pass("P1-03 production first frames do not reset bounded route recovery")
   runOnMainSync{field.set(app.channels,listOf(one));engine!!.tune(one)};await("single real frame"){engine!!.rendered};val serial=engine!!.tuneSerial;fail();await("single retry frame"){engine!!.tuneSerial>serial&&engine!!.rendered};fail();await("single exhausted"){engine!!.error.startsWith("暂无可用频道")};pass("production single route retries once then stops")
   val beforeNetwork=engine!!.tuneSerial
   runOnMainSync{val network=(targetContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork?:error("No emulator network");val callback=engine!!.javaClass.getDeclaredField("networkCallback").apply{isAccessible=true}.get(engine) as android.net.ConnectivityManager.NetworkCallback;callback.onLost(network);callback.onAvailable(network)}
   await("network event bounded restart"){engine!!.tuneSerial>beforeNetwork&&engine!!.rendered};pass("P2-08 simulated network loss/available callback restarts terminal playback")
   // Resolve replay from actual guide; no fabricated programme or stream.
   runBlocking{app.epg.refresh()};val p=app.epg.programsFor(c).lastOrNull{it.stop<System.currentTimeMillis()&&c.playbackRoutes.any{r->com.nexttv.core.data.replayAddress(r,it,System.currentTimeMillis())!=null}}?:error("No real replay programme")
   runOnMainSync{field.set(app.channels,listOf(c));check(engine!!.playReplay(c,p))};await("replay prepared"){engine!!.ready||engine!!.error.isNotBlank()};val replaySerial=engine!!.tuneSerial;fail();await("replay attempt advanced"){engine!!.tuneSerial>replaySerial||engine!!.error.isNotBlank()};runOnMainSync{check(engine!!.replay==p);check(engine!!.current.id==c.id)};pass("P1-07 real replay failure retains programme/channel and retries")
   await("real replay ready after retry"){engine!!.ready&&engine!!.rendered}
   sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(250)
   var fullBefore=0L;var fullAfter=0L;var fullRoute=0
   runOnMainSync{fullBefore=engine!!.player.currentPosition;fullRoute=engine!!.routeIndex}
   sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(250)
   runOnMainSync{fullAfter=engine!!.player.currentPosition;check(engine!!.routeIndex==fullRoute)}
   check(fullAfter>fullBefore+10000){"Fullscreen replay Right did not seek: $fullBefore -> $fullAfter"}
   sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_LEFT);SystemClock.sleep(250)
   runOnMainSync{check(engine!!.player.currentPosition<fullAfter-10000);check(engine!!.routeIndex==fullRoute)}
   pass("fullscreen replay Left/Right seeks without changing route")
   sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MENU);SystemClock.sleep(500)
   fun slider(n:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo?{if(n==null)return null;if(n.rangeInfo!=null)return n;for(i in 0 until n.childCount)slider(n.getChild(i))?.let{return it};return null}
   val seek=slider(uiAutomation.rootInActiveWindow)?:error("No replay progress control");check(seek.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS));SystemClock.sleep(200)
   var prior=0L;var route=0;runOnMainSync{prior=engine!!.player.currentPosition;route=engine!!.routeIndex};sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(300)
   var actualRoute=0;var actualPosition=0L;var duration=0L;var seekable=false;runOnMainSync{duration=engine!!.player.duration;seekable=engine!!.player.isCurrentMediaItemSeekable};runOnMainSync{actualRoute=engine!!.routeIndex;actualPosition=engine!!.player.currentPosition};check(actualRoute==route){"Slider changed route $route → $actualRoute"};check(actualPosition>prior+500){"Slider did not seek $prior → $actualPosition duration=$duration seekable=$seekable"}
   val rect=android.graphics.Rect();seek.getBoundsInScreen(rect);val down=SystemClock.uptimeMillis();for(i in 0..9){val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),if(i==0)0 else if(i==9)1 else 2,rect.left+rect.width()*(.25f+.05f*i),rect.exactCenterY(),0);sendPointerSync(event);event.recycle();SystemClock.sleep(30)};SystemClock.sleep(300)
   runOnMainSync{actualRoute=engine!!.routeIndex;actualPosition=engine!!.player.currentPosition};check(actualRoute==route){"Drag changed route $route → $actualRoute"};check(actualPosition>prior+500){"Drag did not seek $prior → $actualPosition"};pass("P2-06 replay slider accepts D-pad and touch drag without switching routes")
   report="Production recovery: $count groups passed\n"
  }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=android.util.Log.getStackTraceString(t)}
  finally{runOnMainSync{activity.finish();scope.cancel();field.set(app.channels,saved);app.prefs.preloadNext=oldPreload;app.prefs.lastChannelId=oldLast}}
  finish(result,Bundle().apply{putString("stream",report)})
 }
}
