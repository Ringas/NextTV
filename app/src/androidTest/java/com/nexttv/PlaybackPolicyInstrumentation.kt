package com.nexttv
import android.app.*
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import com.nexttv.core.App
import com.nexttv.core.tv.*

/** Real playback and lifecycle policy; uses only the configured user's stream for media. */
class PlaybackPolicyInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 private fun await(label:String,ms:Long=15000,test:()->Boolean){val end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){var ready=false;runOnMainSync{ready=test()};if(ready)return;SystemClock.sleep(60)};error(label+" runtime="+PlaybackSession.active?.let{"${it.diagnostics()} wants=${it.player.playWhenReady} playing=${it.player.isPlaying}"})}
 private fun find(n:AccessibilityNodeInfo?,text:String):AccessibilityNodeInfo?{if(n==null)return null;if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(n.getChild(i),text)?.let{return it};return null}
 private fun tree(n:AccessibilityNodeInfo?):String=if(n==null)""else buildString{append(n.text?:"");for(i in 0 until n.childCount){append('\n');append(tree(n.getChild(i)))}}
 private fun click(text:String){val end=SystemClock.uptimeMillis()+15000;var n:AccessibilityNodeInfo?=null;while(n==null&&SystemClock.uptimeMillis()<end){n=find(uiAutomation.rootInActiveWindow,text);if(n==null)SystemClock.sleep(80)};var node=n?:error("missing $text");while(!node.isClickable)node=node.parent?:error("not clickable");check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))}
 private fun key(activity:Activity,code:Int){runOnMainSync{val t=SystemClock.uptimeMillis();activity.window.callback.dispatchKeyEvent(KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0));activity.window.callback.dispatchKeyEvent(KeyEvent(t,t+10,KeyEvent.ACTION_UP,code,0))};waitForIdleSync();SystemClock.sleep(150)}
 private fun awake(view:View):Boolean= view.keepScreenOn||(view is ViewGroup&&(0 until view.childCount).any{awake(view.getChildAt(it))})
 override fun onStart(){val sp=targetContext.getSharedPreferences("nexttv",0);val saved=sp.all.toMap();var a:TvActivity?=null;var result=Activity.RESULT_OK;val report=StringBuilder()
 fun pass(s:String){report.append("PASS ").append(s).append('\n');sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})}
 try{
  sp.edit().putBoolean("start_live",false).putBoolean("previews",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putInt("preload_mode",0).commit()
  a=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity;val activity=a!!
  await("startup cache"){App.get().cacheReady};check(targetContext.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP==0);await("Home leaking screen awake"){!awake(activity.window.decorView)}
  click("CCTV1综合");await("real first frame",45000){PlaybackSession.active?.rendered==true};await("playing did not keep screen on"){awake(activity.window.decorView)};pass("actual live playback keeps screen on; backup is explicitly disabled")
  key(activity,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);await("pause kept screen on"){!awake(activity.window.decorView)};key(activity,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);await("resume did not restore keep awake"){awake(activity.window.decorView)};pass("media pause/resume updates keep-awake without leaking a wake lock")
  check(uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME));await("background kept screen on"){!awake(activity.window.decorView)}
  ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n com.nexttv/com.nexttv.core.tv.TvActivity")).use{it.readBytes()};await("resume foreground awake"){awake(activity.window.decorView)};pass("background clears keep-awake; mobile/emulator resume restores ongoing playback")
  val engine=PlaybackSession.active!!;await("foreground real first frame",45000){engine.rendered}
  java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress()).use{server->
   server.soTimeout=1200;val channel=engine.current;val route=channel.playbackRoutes.first();val two=channel.copy(routes=listOf(route,route.copy(url="http://127.0.0.1:${server.localPort}/must-not-probe",originalId="no-probe")))
   runOnMainSync{engine.tune(two)};await("real route setup",45000){engine.rendered};key(activity,KeyEvent.KEYCODE_MENU);click("线路 1")
   val end=SystemClock.uptimeMillis()+10000;while(!tree(uiAutomation.rootInActiveWindow).contains("线路选择")&&SystemClock.uptimeMillis()<end)SystemClock.sleep(60)
   val text=tree(uiAutomation.rootInActiveWindow);check(text.contains("线路选择")&&!text.contains("测量中")&&!text.contains(" ms"))
   try{server.accept().use{};error("opening route menu issued an unnecessary HTTP request")}catch(_:java.net.SocketTimeoutException){}
   pass("route menu shows metadata without latency text or probing an alternate stream")
  }
  key(activity,KeyEvent.KEYCODE_ESCAPE);waitForIdleSync();SystemClock.sleep(250);key(activity,KeyEvent.KEYCODE_MENU);waitForIdleSync();click("主页");await("return Home leaked screen awake"){PlaybackSession.active==null&&!awake(activity.window.decorView)};pass("return Home releases the playback keep-awake policy")
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report.append(t.stackTraceToString())}finally{runOnMainSync{a?.finish()};val e=sp.edit().clear();saved.forEach{(k,v)->when(v){is String->e.putString(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Boolean->e.putBoolean(k,v);is Float->e.putFloat(k,v);is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())}};e.commit()}
 finish(result,Bundle().apply{putString("stream",report.toString())})
 }
}
