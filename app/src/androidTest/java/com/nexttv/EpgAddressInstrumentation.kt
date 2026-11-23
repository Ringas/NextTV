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

class EpgAddressInstrumentation:Instrumentation(){
 private lateinit var activity:Activity
 private val passed=mutableListOf<String>()
 private fun tree(n:Node?=uiAutomation.rootInActiveWindow):String=FreshAccessibilityNodes.text(n)
 private fun find(test:(Node)->Boolean,n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null||!n.refresh())return null;if(test(n))return n;for(i in 0 until n.childCount)find(test,n.getChild(i))?.let{return it};return null}
 private fun await(label:String,ms:Long=15000,condition:()->Boolean){val end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(condition())return;SystemClock.sleep(80)};error("Timeout $label: ${tree().take(1600)}")}
 private fun key(k:Int){sendKeyDownUpSync(k);waitForIdleSync();SystemClock.sleep(150)}
 private fun click(text:String){var steps=0;while(find({it.text?.toString()==text})==null&&steps++<12){key(K.KEYCODE_DPAD_DOWN)};await(text){find({it.text?.toString()==text})!=null};var n=find({it.text?.toString()==text})!!;while(!n.isClickable)n=n.parent?:error("No clickable $text");n.performAction(Node.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);waitForIdleSync();SystemClock.sleep(400);n=find({it.text?.toString()==text})?:error("Control detached: $text");while(!n.isClickable)n=n.parent?:error("No clickable $text");if(text !in listOf("XMLTV 节目单地址","M3U 订阅地址")){check(n.performAction(Node.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(150);return};n.refresh();val bounds=android.graphics.Rect();n.getBoundsInScreen(bounds);val now=SystemClock.uptimeMillis();uiAutomation.injectInputEvent(android.view.MotionEvent.obtain(now,now,0,bounds.exactCenterX(),bounds.exactCenterY(),0).apply{source=android.view.InputDevice.SOURCE_TOUCHSCREEN},true);uiAutomation.injectInputEvent(android.view.MotionEvent.obtain(now,SystemClock.uptimeMillis(),1,bounds.exactCenterX(),bounds.exactCenterY(),0).apply{source=android.view.InputDevice.SOURCE_TOUCHSCREEN},true);waitForIdleSync();SystemClock.sleep(150)}
 private fun ime()=uiAutomation.windows.any{it.type==AccessibilityWindowInfo.TYPE_INPUT_METHOD}
 private fun shot(name:String){uiAutomation.takeScreenshot()?.let{b->File(targetContext.filesDir,"epg121-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun pass(label:String){passed+=label;sendStatus(0,Bundle().apply{putString("stream","PASS $label\n")})}
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){waitForIdleSync();val app=App.get();val readyUntil=SystemClock.uptimeMillis()+20000;while(!app.cacheReady&&SystemClock.uptimeMillis()<readyUntil)SystemClock.sleep(50);check(app.cacheReady){"App cache did not initialize before test snapshot"};val original=app.prefs.epgUrl;val originalSources=app.prefs.sourcesJson;val originalIds=app.channels.sources.map{it.id}.toSet();var cleaned=false
  val originalFiles=(listOf("channels.json","channels.json.bak")+originalIds.map{"pl_${it}.m3u"}).associateWith{name->File(targetContext.filesDir,name).takeIf{it.exists()}?.readBytes()}
 fun cleanup(){if(cleaned)return;cleaned=true;if(::activity.isInitialized){runOnMainSync{activity.finish()};waitForIdleSync()}
   // Do not let an in-flight source refresh write edited test metadata after restoration.
   val jobs=app.workScope.coroutineContext[kotlinx.coroutines.Job]?.children?.toList().orEmpty()
   kotlinx.coroutines.runBlocking{jobs.forEach{it.cancel()};kotlinx.coroutines.withTimeout(20000){jobs.forEach{it.join()}}}
   app.prefs.epgUrl=original;app.channels.sources.filter{it.id !in originalIds}.forEach{app.channels.removeSource(it.id)};app.prefs.sourcesJson=originalSources
   originalFiles.forEach{(name,bytes)->val file=File(targetContext.filesDir,name);if(bytes==null)file.delete()else file.writeBytes(bytes)};app.channels.loadCache()
  }
 try{
  val info=uiAutomation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;uiAutomation.serviceInfo=info
  activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1800)
  click("设置");shot("settings-large");val titleBounds=android.graphics.Rect();find({it.text?.toString()=="立即更新节目单"})!!.getBoundsInScreen(titleBounds);check(titleBounds.width()>90&&titleBounds.height()<70){"Title compressed $titleBounds"};click("节目单地址");await("combined QR + input dialog"){tree().contains("节目单地址")&&tree().contains("XMLTV 节目单地址")};await("QR server"){find({it.text?.toString()?.startsWith("http://")==true})!=null}
  check(!ime());shot("combined");pass("EPG address opens QR and input together without automatically opening keyboard")
  val link=find({it.text?.toString()?.startsWith("http://")==true})!!.text.toString()
  check(java.net.URI(link).path.isNullOrEmpty()){ "LAN address is not IP:port" };val web=URL(link).openConnection()as HttpURLConnection;web.connectTimeout=3000;web.readTimeout=3000;val html=web.inputStream.bufferedReader().use{it.readText()};web.disconnect();check(html.contains("XMLTV 节目单地址")&&html.contains("guide.xml"))
  val sent="http://192.0.2.115:8787/guide.xml?nexttv-test=1";val connection=URL(link).openConnection()as HttpURLConnection;connection.connectTimeout=3000;connection.readTimeout=3000;connection.requestMethod="POST";connection.doOutput=true;connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");connection.outputStream.use{it.write(("session="+Regex("name=\"session\" value=\"([^\"]+)\"").find(html)!!.groupValues[1]+"&url="+URLEncoder.encode(sent,"UTF-8")).toByteArray())};check(connection.responseCode==200);connection.inputStream.close();connection.disconnect()
  await("phone address received"){tree().contains(sent)&&tree().contains("已收到手机输入")};check(app.prefs.epgUrl==original);pass("phone web form sends XMLTV URL to draft only; television confirmation required")
  click("XMLTV 节目单地址");await("editable field"){find({it.isEditable})!=null};await("keyboard explicitly shown"){ime()};shot("keyboard")
  key(K.KEYCODE_ESCAPE);await("first Esc hides keyboard"){!ime()};check(tree().contains("保存并更新"));key(K.KEYCODE_ESCAPE);await("second Esc closes dialog"){!tree().contains("保存并更新")};await("originating settings row focus"){tree(find({it.isFocused})).contains("节目单地址")};check(app.prefs.epgUrl==original)
  pass("input activation shows keyboard; Esc hides IME then closes dialog and restores exact setting row")
  val beforeUpdate=app.epg.updatedAt;click("节目单地址");click("XMLTV 节目单地址");await("manual address field"){find({it.isEditable})!=null};find({it.isEditable})!!.performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"http://192.0.2.115:8787/guide.xml")});await("manual keyboard"){ime()};key(K.KEYCODE_ESCAPE);await("manual keyboard hidden"){!ime()};click("保存并更新");await("confirmation closes dialog"){!tree().contains("保存并更新")};await("real EPG refresh completes",60000){app.epg.updatedAt>beforeUpdate};check(app.epg.lastError.isBlank()){app.epg.lastError};pass("confirm uses current XMLTV URL and completes real EPG refresh")
  val updated=app.epg.updatedAt;click("立即更新节目单");check(!tree().contains("保存并更新"));await("direct refresh without input popup",60000){app.epg.updatedAt>updated};pass("Immediate update refreshes directly; address entry alone opens QR input")
  click("管理播放源");await("two subscription actions"){tree().contains("添加订阅")&&tree().contains("重新拉取")};check(!tree().contains("导入文件")&&!tree().contains("扫码添加")&&!tree().contains("添加地址"));shot("subscriptions")
  click("添加订阅");await("same combined input for subscriptions"){tree().contains("M3U 订阅地址")&&tree().contains("确认添加")};check(!ime());shot("add-subscription")
  click("M3U 订阅地址");await("subscription keyboard"){ime()};key(K.KEYCODE_ESCAPE);await("subscription keyboard hidden"){!ime()};key(K.KEYCODE_ESCAPE);await("subscription popup closed"){!tree().contains("确认添加")};await("add action focus restored"){tree(find({it.isFocused})).contains("添加订阅")}
  pass("source toolbar has only add/refresh; shared QR input and IME → dialog → list Back hierarchy")
  click("添加订阅");click("M3U 订阅地址");await("subscription edit field"){find({it.isEditable})!=null};val edit=find({it.isEditable})!!;check(edit.performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"http://192.0.2.115:8787/live.m3u")}));await("subscription keyboard ready before back"){ime()};key(K.KEYCODE_ESCAPE);await("keyboard hidden before add"){!ime()};click("确认添加");await("subscription confirmed"){app.channels.sources.any{it.url=="http://192.0.2.115:8787/live.m3u"}&&!tree().contains("确认添加")};await("subscription refreshed"){tree().contains("个成功")};pass("confirmed subscription saves address and refreshes real playlist")
  val source=app.channels.sources.first{it.url=="http://192.0.2.115:8787/live.m3u"}
  click(source.name);click("编辑");await("edit uses shared QR + address"){tree().contains("编辑订阅")&&tree().contains("保存并刷新")};check(tree().contains("${source.name},${source.url}"));check(!ime());shot("edit-subscription");key(K.KEYCODE_ESCAPE);await("edit Back returns source operations"){find({it.text?.toString()=="删除"})!=null};key(K.KEYCODE_ESCAPE);await("source operations closes to list"){find({it.text?.toString()=="重新拉取"})!=null}
  click(source.name);click("编辑");click("M3U 订阅地址");await("edit source text field"){find({it.isEditable})!=null};check(find({it.isEditable})!!.performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"名称测试，${source.url}")}));await("source edit keyboard ready"){ime()};key(K.KEYCODE_ESCAPE);await("edit keyboard hidden"){!ime()};click("保存并刷新");await("same source renamed"){app.channels.sources.any{it.id==source.id&&it.name=="名称测试"}}
  val changed=app.channels.sources.first{it.id==source.id};check(changed.userAgent==source.userAgent&&changed.headers==source.headers&&changed.referer==source.referer&&changed.epgUrl==source.epgUrl)
  pass("editing reuses combined QR input; Chinese comma updates original source without losing request metadata; Back returns by level")
  click("名称测试");click("编辑");click("M3U 订阅地址");await("address-only edit field"){find({it.isEditable})!=null};check(find({it.isEditable})!!.performAction(Node.ACTION_SET_TEXT,Bundle().apply{putCharSequence(Node.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,source.url)}));await("address-only keyboard ready"){ime()};key(K.KEYCODE_ESCAPE);await("address-only keyboard hidden"){!ime()};click("保存并刷新");await("name retained when omitted"){!tree().contains("保存并刷新")&&app.channels.sources.any{it.id==source.id&&it.name=="名称测试"}};pass("new and existing subscriptions accept the same address-only rule; omitted edit name is retained")
  key(K.KEYCODE_BACK);shot("after-source-back");await("source list returns to settings row"){tree(find({it.isFocused})).contains("管理播放源")};pass("source page returns to originating settings item")
  val result=passed.joinToString("\n")+"\n${passed.size} groups passed\n";File(targetContext.filesDir,"epg121-results.txt").writeText(result);cleanup();finish(Activity.RESULT_OK,Bundle().apply{putString("stream",result)})
 }catch(t:Throwable){shot("failure");cleanup();finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}finally{cleanup()}}
}
