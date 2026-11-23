@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo as Node
import com.nexttv.core.App
import com.nexttv.core.data.Source
import com.nexttv.core.tv.*

class SettingsActionInstrumentation:Instrumentation(){
 private lateinit var activity:Activity
 private fun tree(n:Node?=uiAutomation.rootInActiveWindow):String=if(n==null)""else buildString{append(n.text?:"");for(i in 0 until n.childCount)append('\n').append(tree(n.getChild(i)))}
 private fun find(text:String,n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null)return null;if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(text,n.getChild(i))?.let{return it};return null}
 private fun await(label:String,timeout:Long=15000,condition:()->Boolean){val until=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<until){if(condition())return;SystemClock.sleep(60)};error("$label: ${tree().take(1000)}")}
 private fun click(text:String){await(text){find(text)!=null};var n=find(text)!!;while(!n.isClickable)n=n.parent?:error("Not clickable $text");n.performAction(Node.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);check(n.performAction(Node.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(100)}
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){waitForIdleSync();val app=App.get();val prefs=targetContext.getSharedPreferences("nexttv",0);val saved=prefs.all;val sources=app.prefs.sourcesJson;val ids=app.channels.sources.map{it.id}.toSet();val fixture="delete-ui-"+SystemClock.uptimeMillis();var cleaned=false
 fun cleanup(){if(cleaned)return;cleaned=true;if(::activity.isInitialized){runOnMainSync{activity.finish()};waitForIdleSync()};app.channels.sources.filter{it.id==fixture}.forEach{app.channels.removeSource(it.id)};app.prefs.sourcesJson=sources;app.channels.loadSources();val e=prefs.edit();for(k in listOf("selected_group","previews","auto_update","auto_update_epg")){when(val v=saved[k]){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);else->e.remove(k)}};e.commit()}
 try{prefs.edit().putString("selected_group","").putBoolean("previews",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit();app.channels.upsertSource(Source(fixture,"删除验证临时订阅","http://127.0.0.1/unused-fixture",enabled=false));activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1500);click("设置");click("管理播放源");click("删除验证临时订阅");click("删除");click("删除订阅");await("fixture actually removed"){app.channels.sources.none{it.id==fixture}};check(app.channels.sources.map{it.id}.toSet()==ids);sendStatus(0,Bundle().apply{putString("stream","PASS confirmed delete removes only disabled temporary subscription; original source IDs unchanged\n")});sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);click("频道");click("CCTV1综合");await("real video",40000){PlaybackSession.active?.rendered==true};sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);click("画面 · 适应");await("Compose video crop"){PlaybackSession.active?.aspect==1};click("画面 · 填充");await("Compose video stretch"){PlaybackSession.active?.aspect==2};click("画面 · 拉伸");await("Compose video fit"){PlaybackSession.active?.aspect==0};cleanup();finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS temporary-subscription deletion and Compose video aspect selection; user subscriptions restored\n")})
 }catch(t:Throwable){cleanup();finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}
 }
}
