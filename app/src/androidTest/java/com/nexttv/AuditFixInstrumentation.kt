@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv

import android.app.*
import android.content.*
import android.graphics.Rect
import android.media.AudioManager
import android.os.*
import android.view.KeyEvent as K
import android.view.accessibility.AccessibilityNodeInfo as Node
import com.nexttv.core.App
import com.nexttv.core.data.*
import com.nexttv.core.data.Channel
import com.nexttv.core.tv.*
import kotlinx.coroutines.*
import org.json.*
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/** Real subscription for successful video; local HTTP failures exercise deterministic recovery. */
class AuditFixInstrumentation:Instrumentation(){
 private lateinit var activity:Activity
 private val results=mutableListOf<String>()
 private fun tree(n:Node?=uiAutomation.rootInActiveWindow):String=if(n==null)""else buildString{append(n.text?:"");append('\n');for(i in 0 until n.childCount)append(tree(n.getChild(i)))}
 private fun find(text:String,n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null)return null;if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(text,n.getChild(i))?.let{return it};return null}
 private fun focused(n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null)return null;if(n.isFocused)return n;for(i in 0 until n.childCount)focused(n.getChild(i))?.let{return it};return null}
 private fun await(label:String,ms:Long=18000,condition:()->Boolean){val end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(condition())return;SystemClock.sleep(80)};error("Timeout $label UI=${tree().take(1800)}")}
 private fun key(k:Int){sendKeyDownUpSync(k);waitForIdleSync();SystemClock.sleep(120)}
 private fun click(text:String){await(text){find(text)!=null};var n=find(text)!!;while(!n.isClickable)n=n.parent?:error("not clickable $text");check(n.performAction(Node.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(150)}
 private fun main(block:()->Unit)=runOnMainSync(block)
 private fun pass(s:String){results+=s;sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})}
 private fun saveShot(name:String){val b=uiAutomation.takeScreenshot()?:return;File(targetContext.filesDir,"routes121-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 private fun mouse(text:String?=null,button:Int=1,hold:Long=50){var x=650f;var y=200f;if(text!=null){await(text){find(text)!=null};var n=find(text)!!;while(!n.isClickable)n=n.parent?:error("No click $text");val b=android.graphics.Rect();n.getBoundsInScreen(b);x=b.exactCenterX();y=b.exactCenterY()};val props=arrayOf(android.view.MotionEvent.PointerProperties().apply{id=0;toolType=3});val coords=arrayOf(android.view.MotionEvent.PointerCoords().apply{this.x=x;this.y=y;pressure=1f;size=1f});val t=SystemClock.uptimeMillis();for(action in listOf(0,1)){val e=android.view.MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,1,props,coords,0,if(action==0)button else 0,1f,1f,0,0,android.view.InputDevice.SOURCE_MOUSE,0);check(uiAutomation.injectInputEvent(e,true));e.recycle();if(action==0)SystemClock.sleep(hold)};SystemClock.sleep(200)}
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){waitForIdleSync();val app=App.get();val group=app.prefs.selectedGroup;val favorites=app.prefs.favorites;val sources=app.prefs.sourcesJson
 var cleaned=false
 fun cleanup(){if(cleaned)return;cleaned=true;FocusAudit.frame=null;PlaybackSession.stallTimeoutMillis=25000;main{app.prefs.selectedGroup=group;targetContext.getSharedPreferences("nexttv",0).edit().putStringSet("favorites",favorites).commit();app.prefs.sourcesJson=sources;app.channels.loadSources();app.channels.rebuildChannels();if(::activity.isInitialized)activity.finish()}}
 try{main{app.prefs.selectedGroup=""};runBlocking{app.channels.refreshAll()};activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1500)
 mouse("CCTV1综合");await("mouse activates real player"){PlaybackSession.active!=null};val engine=PlaybackSession.active!!;await("real video",40000){engine.rendered};pass("PRIMARY mouse activates card and real video renders")
 key(K.KEYCODE_MENU);key(K.KEYCODE_BACK);check(!InteractionStyle.pointer){"Back failed to restore keyboard focus visuals"};val t=SystemClock.uptimeMillis();sendKeySync(android.view.KeyEvent(t,t,0,23,0));sendKeySync(android.view.KeyEvent(t,SystemClock.uptimeMillis(),0,23,1,0,-1,0,android.view.KeyEvent.FLAG_LONG_PRESS));sendKeySync(android.view.KeyEvent(t,SystemClock.uptimeMillis(),1,23,0));await("long OK"){tree().contains("画面适配")};pass("long OK opens shortcuts")
 key(4);mouse(button=2);await("secondary button"){tree().contains("画面适配")};mouse(if(find("取消收藏")!=null)"取消收藏"else"添加收藏");SystemClock.sleep(4300);check(tree().contains("画面适配"));SystemClock.sleep(6400);check(!tree().contains("画面适配"));pass("SECONDARY mouse opens shortcuts; action expiry is 10s rather than 3s");mouse();await("single click after shortcut expiry opens channels"){tree().contains("频道列表")};key(4);pass("expired shortcuts clear mode; next primary click opens channels")
 mouse(hold=750);await("mouse long press"){tree().contains("画面适配")};key(4);key(23);key(22);await("EPG"){tree().contains("节目单 ·")};val currentTitle=app.epg.nowNext(engine.current).first?.title;await("actual programme focus"){val focusedText=tree(focused());if(currentTitle!=null)focusedText.contains(currentTitle)else focusedText.contains("日期：")};main{engine.tune(engine.current)};SystemClock.sleep(700);check(tree().contains("节目单 ·"));saveShot("preserved-epg");pass("mouse long press and reload preserve linked browser")
 key(4);val serial=engine.tuneSerial;main{engine.setForeground(false)};SystemClock.sleep(2200);check(engine.tuneSerial==serial);main{engine.setForeground(true)};await("resumed",40000){engine.rendered};pass("background suspends recovery and foreground resumes real playback")
 val src=app.channels.sources.first();main{app.channels.upsertSource(src.copy(enabled=false))};check(app.channels.channels.none{it.playbackRoutes.any{r->r.sourceId==src.id}});main{app.channels.upsertSource(src)};await("re-enabled source playback",40000){engine.rendered};pass("disabled source routes excluded; cached routes return on enabling")
 val server=java.net.ServerSocket(0);val thread=Thread{while(!server.isClosed){try{server.accept().use{sock->val body="#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:1\n#EXT-X-MEDIA-SEQUENCE:0\n";sock.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n"+body).toByteArray())}}catch(_:Exception){}}}.apply{isDaemon=true;start()}
 try{PlaybackSession.stallTimeoutMillis=1800;val before=engine.tuneSerial;main{engine.player.setMediaItem(androidx.media3.common.MediaItem.Builder().setUri("http://127.0.0.1:${server.localPort}/stall.m3u8").setMimeType("application/x-mpegURL").build());engine.player.prepare();engine.player.play()};await("post-first-frame BUFFERING recovery",15000){engine.tuneSerial>before};pass("post-first-frame native BUFFERING triggers watchdog recovery")}finally{server.close();thread.join(500);PlaybackSession.stallTimeoutMillis=25000}
 main{activity.finish()};await("engine disposed"){PlaybackSession.active==null};activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1500)
 val samples=java.util.concurrent.CopyOnWriteArrayList<Long>();var start=0L;var base=0;FocusAudit.frame={g,tick->if(start>0&&g>base){samples+=tick-start;start=0}}
 key(20);repeat(24){base=FocusAudit.generation;start=SystemClock.uptimeMillis();sendKeyDownUpSync(if(it<16)20 else 19);SystemClock.sleep(65)};FocusAudit.frame=null;File(targetContext.filesDir,"fix13-focus-latency.txt").writeText(samples.joinToString(","));check(samples.size>=10);pass("changed-focus draw latency recorded (${samples.size} samples)")
 File(targetContext.filesDir,"fix13-results.txt").writeText(results.joinToString("\n"));cleanup();finish(Activity.RESULT_OK,Bundle().apply{putString("stream",results.joinToString("\n")+"\n${results.size} groups passed")})
 }catch(t:Throwable){saveShot("audit-failure");cleanup();finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}finally{cleanup()}}
}
