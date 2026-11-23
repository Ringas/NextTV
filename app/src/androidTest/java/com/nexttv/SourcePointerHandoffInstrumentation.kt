// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv
import android.app.*
import android.content.Intent
import android.graphics.Rect
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo as Node
import com.nexttv.core.App
import com.nexttv.core.tv.TvActivity

/** Debug-only controlled disabled rows; no additional network sources or media requests. */
class SourcePointerHandoffInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 private fun tree(n:Node?=uiAutomation.rootInActiveWindow):String=FreshAccessibilityNodes.text(n)
 private fun find(t:String,n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null||!n.refresh())return null;if(n.text?.toString()==t)return n;for(i in 0 until n.childCount)find(t,n.getChild(i))?.let{return it};return null}
 private fun await(label:String,test:()->Boolean){val until=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<until){if(test())return;SystemClock.sleep(80)};error("$label UI=${tree()}")}
 private fun click(t:String){var attempts=0;while(find(t)==null&&attempts++<16){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();SystemClock.sleep(120)};await(t){find(t)!=null};var n=find(t)!!;while(!n.isClickable)n=n.parent;n.performAction(Node.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);SystemClock.sleep(300);n=find(t)!!;while(!n.isClickable)n=n.parent;check(n.performAction(Node.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(250)}
 override fun onStart(){val app=App.get();val prefs=targetContext.getSharedPreferences("nexttv",0);var saved:Map<String,*>?=null;val created=mutableListOf<String>();val savedFiles=mutableMapOf<String,ByteArray?>();var a:Activity?=null;var result=Activity.RESULT_OK;var report=""
 try{
  await("real source cache ready"){app.cacheReady&&app.channels.sources.isNotEmpty()&&app.channels.channels.isNotEmpty()}
  saved=prefs.all
  (listOf("channels.json","channels.json.bak")+app.channels.sources.map{"pl_${it.id}.m3u"}).forEach{name->val file=java.io.File(targetContext.filesDir,name);savedFiles[name]=file.takeIf{it.exists()}?.readBytes()}
  val source=app.channels.sources.firstOrNull()?:error("Initialize real fixture with Debug first")
  prefs.edit().putBoolean("start_live",false).putBoolean("previews",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit()
  val nonce=System.nanoTime();repeat(24){i->val id="input-handoff-$nonce-$i";created+=id;app.channels.upsertSource(source.copy(id=id,name="输入回归临时订阅 ${i.toString().padStart(2,'0')}",enabled=false,channelCount=0))}
  a=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home"){tree().contains("频道分类：")};click("设置");click("管理播放源");await("source list"){tree().contains("输入回归临时订阅 00")}
  var first=find(source.name)!!;while(!first.isFocusable)first=first.parent;check(first.performAction(Node.ACTION_FOCUS));waitForIdleSync()
  val prop=MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE};val coords=MotionEvent.PointerCoords().apply{x=500f;y=400f;setAxisValue(MotionEvent.AXIS_VSCROLL,-18f)};val t=SystemClock.uptimeMillis();val e=MotionEvent.obtain(t,t,MotionEvent.ACTION_SCROLL,1,arrayOf(prop),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_MOUSE,0);check(uiAutomation.injectInputEvent(e,true));e.recycle();SystemClock.sleep(700);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync()
  await("keyboard resumes current visible source"){val n=FreshAccessibilityNodes.focused(uiAutomation.rootInActiveWindow);val r=Rect();n?.getBoundsInScreen(r);tree(n).contains("输入回归临时订阅")&&r.top>=150&&r.bottom<=630&&r.height()>60}
  report="PASS long source list wheel → keyboard resumes visible temporary disabled row; original source/cache restored\n"
 }catch(t:Throwable){result=Activity.RESULT_CANCELED;report=t.stackTraceToString()}finally{
  runOnMainSync{a?.finish()};created.forEach{app.channels.removeSource(it)};val edit=prefs.edit();if(saved!=null)edit.clear();saved?.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit();savedFiles.forEach{(name,bytes)->val file=java.io.File(targetContext.filesDir,name);if(bytes==null)file.delete()else file.writeBytes(bytes)};app.channels.loadCache()
 };finish(result,Bundle().apply{putString("stream",report)})
 }
}
