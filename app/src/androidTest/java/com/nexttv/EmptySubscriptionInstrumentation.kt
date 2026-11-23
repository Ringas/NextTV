package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import android.view.KeyEvent as K
import android.view.accessibility.AccessibilityNodeInfo as Node
import android.view.accessibility.AccessibilityWindowInfo
import android.accessibilityservice.AccessibilityServiceInfo
import com.nexttv.core.App
import com.nexttv.core.tv.TvActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class EmptySubscriptionInstrumentation:Instrumentation(){
 private lateinit var activity:Activity
 private val passed=mutableListOf<String>()
 private fun tree(n:Node?=uiAutomation.rootInActiveWindow):String=if(n==null)""else buildString{append(n.text?:"");append('\n');for(i in 0 until n.childCount)append(tree(n.getChild(i)))}
 private fun find(test:(Node)->Boolean,n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null)return null;if(test(n))return n;for(i in 0 until n.childCount)find(test,n.getChild(i))?.let{return it};return null}
 private fun await(label:String,ms:Long=15000,condition:()->Boolean){val end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(condition())return;SystemClock.sleep(80)};error("Timeout $label: ${tree().take(1600)}")}
 private fun key(k:Int){sendKeyDownUpSync(k);waitForIdleSync();SystemClock.sleep(150)}
 private fun click(text:String){var steps=0;while(find({it.text?.toString()==text})==null&&steps++<12){key(K.KEYCODE_DPAD_DOWN)};await(text){find({it.text?.toString()==text})!=null};var n=find({it.text?.toString()==text})!!;while(!n.isClickable)n=n.parent?:error("No clickable $text");n.performAction(Node.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);waitForIdleSync();SystemClock.sleep(400);n=find({it.text?.toString()==text})?:error("Control detached: $text");while(!n.isClickable)n=n.parent?:error("No clickable $text");if(text !in listOf("XMLTV 节目单地址","M3U 订阅地址")){check(n.performAction(Node.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(150);return};n.refresh();val bounds=android.graphics.Rect();n.getBoundsInScreen(bounds);val now=SystemClock.uptimeMillis();uiAutomation.injectInputEvent(android.view.MotionEvent.obtain(now,now,0,bounds.exactCenterX(),bounds.exactCenterY(),0).apply{source=android.view.InputDevice.SOURCE_TOUCHSCREEN},true);uiAutomation.injectInputEvent(android.view.MotionEvent.obtain(now,SystemClock.uptimeMillis(),1,bounds.exactCenterX(),bounds.exactCenterY(),0).apply{source=android.view.InputDevice.SOURCE_TOUCHSCREEN},true);waitForIdleSync();SystemClock.sleep(150)}
 private fun ime()=uiAutomation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}
 private fun shot(name:String){uiAutomation.takeScreenshot()?.let{b->File(targetContext.filesDir,"empty134-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun pass(label:String){passed+=label;sendStatus(0,Bundle().apply{putString("stream","PASS $label\n")})}
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 private fun centered(label:String){val text=find({it.text?.toString()==label})?:error("Missing $label");val t=android.graphics.Rect();text.getBoundsInScreen(t);var button=text;while(!button.isClickable)button=button.parent?:error("No button");val b=android.graphics.Rect();button.getBoundsInScreen(b);check(kotlin.math.abs(t.exactCenterX()-b.exactCenterX())<=2&&kotlin.math.abs(t.exactCenterY()-b.exactCenterY())<=2){"Button text not centered $label $t $b"};check(b.width()>=80&&b.height()>=44){"Button too small $label $b"}}
 override fun onStart(){waitForIdleSync();val app=App.get();val prefs=targetContext.getSharedPreferences("nexttv",0);val saved=prefs.all;val file=File(targetContext.filesDir,"channels.json");val cache=if(file.exists())file.readBytes()else null;val ids=app.channels.sources.map{it.id}.toSet();var cleaned=false
 fun cleanup(){if(cleaned)return;cleaned=true;if(::activity.isInitialized){runOnMainSync{activity.finish()};waitForIdleSync()};app.channels.sources.filter{it.id !in ids}.forEach{app.channels.removeSource(it.id)};val e=prefs.edit().clear();saved.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v);is Set<*> ->@Suppress("UNCHECKED_CAST") e.putStringSet(k,v as Set<String>)}};e.commit();if(cache!=null)file.writeBytes(cache)else file.delete();app.channels.loadCache()}
 try{
  val info=uiAutomation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;uiAutomation.serviceInfo=info
  prefs.edit().putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("previews",false).putString("selected_group","").commit();app.prefs.sourcesJson="[]";file.writeText("[]");app.channels.loadCache()
  activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("empty home"){tree().contains("尚未添加订阅")};SystemClock.sleep(1500)
  check(!tree().contains("管理播放源"));key(K.KEYCODE_DPAD_DOWN);await("keyboard reaches add button"){find({it.isFocused})?.let{tree(it).contains("添加订阅")}==true};centered("添加订阅");shot("focused-add");pass("empty home Down directly focuses Add subscription, no management detour")
  key(K.KEYCODE_ENTER);await("combined dialog"){tree().contains("M3U 订阅地址")&&tree().contains("确认添加")};check(!ime());centered("确认添加");shot("combined-dialog");key(K.KEYCODE_ESCAPE);await("cancel closes dialog"){!tree().contains("确认添加")};await("cancel restores Add focus"){find({it.isFocused})?.let{tree(it).contains("添加订阅")}==true};pass("Enter opens shared QR/input without IME; Esc restores Add focus")
  key(K.KEYCODE_ENTER);await("reopen"){tree().contains("确认添加")};key(K.KEYCODE_ENTER);await("explicit keyboard"){ime()};val edit=find({it.isEditable})?:error("No input");check(edit.performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"测试订阅，http://192.0.2.115:8787/live.m3u")}));key(K.KEYCODE_ESCAPE);await("hide keyboard first"){!ime()};check(tree().contains("确认添加"));click("确认添加");await("real channels automatically loaded",60000){tree().contains("CCTV1综合")&&!tree().contains("确认添加")};check(app.channels.sources.size==1&&app.channels.sources.first().name=="测试订阅");shot("refreshed-home");pass("named source confirmed through same parser; real playlist automatically refreshes homepage")
  key(K.KEYCODE_BACK);await("Back to Channels menu"){find({it.isFocused})?.let{tree(it).trim()=="频道"}==true};key(K.KEYCODE_DPAD_DOWN);await("normal category control after add"){find({it.isFocused})?.let{tree(it).contains("频道分类：")}==true};pass("after adding, normal menu/content navigation is restored")
  cleanup();finish(Activity.RESULT_OK,Bundle().apply{putString("stream",passed.joinToString("\n",postfix="\nEmpty subscription: ${passed.size} groups passed\n"))})
 }catch(t:Throwable){cleanup();finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",android.util.Log.getStackTraceString(t))})}
 }
}
