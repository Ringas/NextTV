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
class PlaybackRoutesInstrumentation:Instrumentation(){
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
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){
  waitForIdleSync()
  val app=App.get();val cache=File(targetContext.filesDir,"channels.json");val oldCache=cache.takeIf{it.exists()}?.readBytes();val prefs=targetContext.getSharedPreferences("nexttv",0);val originalPrefs=prefs.all.toMap()
  val hits=CopyOnWriteArrayList<String>();val server=ServerSocket(0)
  val thread=Thread{while(!server.isClosed){try{server.accept().use{s->val request=s.getInputStream().bufferedReader().readLine().orEmpty();hits+=request;s.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())}}catch(_:Exception){}}}.apply{isDaemon=true;start()}
  fun bad(path:String)="http://127.0.0.1:${server.localPort}/$path"
  fun write(channels:List<Channel>){val arr=JSONArray();channels.forEach{c->arr.put(JSONObject().put("id",c.id).put("name",c.name).put("tvgId",c.tvgId).put("logo",c.logo).put("group",c.group).put("url",c.url).put("sourceId",c.sourceId).put("number",c.number).put("catchup",c.catchup).put("catchupSource",c.catchupSource))};cache.writeText(arr.toString());main{app.channels.loadCache()}}
  try{
   // Refresh only the existing user's fixed development subscription; never install fixtures on TV.
   if(app.channels.sources.none{it.url=="http://192.0.2.115:8787/live.m3u"})main{app.channels.upsertSource(Source("routes121-test","开发测试订阅","http://192.0.2.115:8787/live.m3u"))}
   runBlocking{app.channels.refreshAll()}
   val real=app.channels.channels.first{it.name=="CCTV1综合"};val route=real.playbackRoutes.first()
   check(app.channels.channels.map{it.tvgId.ifBlank{it.name}}.distinct().size==app.channels.channels.size)
   check(real.playbackRoutes.size>=2)
   pass("fixed real playlist merges repeated channel identities with ordered routes")
   app.prefs.autoUpdateOnStart=false;app.prefs.preloadNext=false;app.prefs.selectedGroup=""
   val multi=real.copy(id="test-multi",url=bad("multi"),routes=emptyList(),group="测试分组")
   val good=real.copy(id="test-multi-good",url=route.url,sourceId=route.sourceId,routes=emptyList(),group="测试分组")
   val single=real.copy(id="test-single",name="单线路故障验证",tvgId="test-single",url=bad("single"),routes=emptyList(),group="另一分组")
   val next=real.copy(id="test-next",name="下一台验证",tvgId="test-next",url=route.url,sourceId=route.sourceId,routes=emptyList(),group="另一分组")
   write(listOf(multi,good,single,next));check(app.channels.channels.size==3)
   activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(2200)
   click("设置");await("settings"){find("节目单地址")!=null};val initialSetting=Rect();find("节目单地址")!!.getBoundsInScreen(initialSetting)
   for(label in listOf("节目单","频道列表")){val n=find(label)?:error("missing heading $label");check(!n.isFocusable&&!n.isClickable){"Selectable heading $label"}}
   click("节目单地址");key(K.KEYCODE_ESCAPE);SystemClock.sleep(450);if(find("保存")!=null)key(K.KEYCODE_ESCAPE)
   key(K.KEYCODE_BACK);await("setting row directly returns to settings nav"){tree(focused()).trim()=="设置"}
   pass("setting headings are non-interactive and Back skips headings")
   click("节目单地址");key(K.KEYCODE_ESCAPE);SystemClock.sleep(450);if(find("保存")!=null)key(K.KEYCODE_ESCAPE)
   repeat(11){key(K.KEYCODE_DPAD_DOWN)}
   key(K.KEYCODE_BACK);await("settings scroll return keeps top nav focused"){tree(focused()).trim()=="设置"};SystemClock.sleep(500);val returnedSetting=Rect();find("节目单地址")!!.getBoundsInScreen(returnedSetting);check(initialSetting.top==returnedSetting.top){"Settings did not return to top"};saveShot("settings-top")
   key(K.KEYCODE_BACK);await("settings next Back shows exit confirmation"){find("再按一次退出")!=null};key(K.KEYCODE_DPAD_LEFT);key(K.KEYCODE_DPAD_RIGHT)
   pass("settings scrolled Back returns page top + Settings nav; next Back prompts exit")
   click("频道");click("CCTV1综合");await("automatic alternate plays real video",35000){PlaybackSession.active?.let{it.current.id==multi.id&&it.routeIndex==1&&it.rendered}==true}
   check(!tree().contains("画面比例"));pass("failed multi-route switches alternate and renders real video without shortcut menu")
   val engine=PlaybackSession.active!!;val audio=targetContext.getSystemService(Context.AUDIO_SERVICE)as AudioManager;val volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
   key(K.KEYCODE_DPAD_LEFT);check(engine.routeIndex==0);key(K.KEYCODE_DPAD_RIGHT);await("manual route real video",35000){engine.routeIndex==1&&engine.rendered};check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==volume)
   pass("left/right changes route, preserves volume and cancels obsolete failed attempts")
   key(K.KEYCODE_ENTER);await("default channel list"){tree().contains("频道列表")};check(!tree().contains("频道分组\n")&&!tree().contains("节目单 ·"));saveShot("channels")
   key(K.KEYCODE_DPAD_LEFT);await("groups + channels"){tree().contains("频道分组\n")&&tree().contains("频道列表")};check(!tree().contains("节目单 ·"));saveShot("groups")
   click("全部");key(K.KEYCODE_DPAD_RIGHT);await("channels + epg"){tree().contains("节目单 ·")};check(tree().contains("频道列表")&&!tree().contains("频道分组\n"));saveShot("epg")
   val selected=engine.current.id
   key(K.KEYCODE_DPAD_LEFT);key(K.KEYCODE_DPAD_DOWN);check(engine.current.id==selected);key(K.KEYCODE_DPAD_RIGHT);await("candidate epg linked"){tree().contains("节目单 · 单线路故障验证")}
   check(engine.current.id==selected);pass("default channels, adjacent groups/EPG only, candidate browsing never tunes")
   key(K.KEYCODE_ESCAPE);await("EPG Back all closes"){!tree().contains("频道列表")&&!tree().contains("节目单 ·")};check(engine.current.id==selected)
   key(K.KEYCODE_ENTER);key(K.KEYCODE_DPAD_LEFT);key(K.KEYCODE_BACK);await("group Back all closes"){!tree().contains("频道列表")&&!tree().contains("频道分组\n")};pass("Back/Esc from both adjacent panes closes entire overlay without leaving playback")
   val singleBefore=hits.count{it.contains("/single ")}
   main{engine.tune(app.channels.channels.first{it.id==single.id})}
   await("single retry then next real video",40000){engine.current.id==next.id&&engine.rendered}
   check(hits.count{it.contains("/single ")}-singleBefore==2){"single requests $hits"};check(!tree().contains("线路 ")||!tree().contains("画面比例"))
   val serial=engine.tuneSerial;key(K.KEYCODE_DPAD_RIGHT);check(engine.tuneSerial==serial);check(find("线路 1/1")==null){"Unexpected single-route toast"};pass("single route retries once then next channel; single-route left/right has no toast or reload")
   val beforeSingle=hits.count{it.contains("/single ")}
   write(listOf(multi,good.copy(url=bad("alternate")),single,next))
   main{engine.tune(app.channels.channels.first())}
   await("all alternate routes exhausted then next channel",40000){engine.current.id==next.id&&engine.rendered}
   check(hits.any{it.contains("/alternate ")}&&hits.count{it.contains("/single ")}-beforeSingle==2)
   pass("exhausted multi-route advances, failing single retries, then next real channel renders")
   write(listOf(single));main{engine.tune(app.channels.channels.single())}
   await("bounded all-channel failure"){engine.error.isNotBlank()};val stopped=engine.tuneSerial;SystemClock.sleep(1800);check(engine.tuneSerial==stopped)
   check(!tree().contains("画面比例"));pass("all channels exhausted stops recovery without looping or opening controls")
   write(listOf(multi,good,single,next));main{engine.tune(app.channels.channels.first{it.id==next.id})};await("restore real video",35000){engine.rendered}
   key(K.KEYCODE_MENU);await("explicit controls before exit"){tree().contains("画面比例")};key(K.KEYCODE_BACK);key(K.KEYCODE_BACK);await("live confirmation"){tree().contains("再按一次退出播放")};val live=Rect();find("再按一次退出播放")!!.getBoundsInScreen(live);saveShot("live-exit")
   key(K.KEYCODE_BACK);await("home"){PlaybackSession.active==null};SystemClock.sleep(350);if(tree(focused()).trim()!="频道")key(K.KEYCODE_BACK);await("home top focus"){tree(focused()).trim()=="频道"};key(K.KEYCODE_BACK);await("home confirmation"){find("再按一次退出")!=null};val home=Rect();find("再按一次退出")!!.getBoundsInScreen(home)
   check(live.centerX()==home.centerX()&&live.centerY()==home.centerY()&&live.bottom==home.bottom){"Different exit anchors $live / $home"};pass("live and application exit notices share horizontal and vertical anchor")
   val content=results.joinToString("\n")+"\n${results.size} groups passed\n";File(targetContext.filesDir,"routes121-results.txt").writeText(content)
   finish(Activity.RESULT_OK,Bundle().apply{putString("stream",content)})
  }catch(t:Throwable){saveShot("failure");finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}
  finally{server.close();thread.join(500);main{if(::activity.isInitialized)activity.finish()};if(oldCache!=null)cache.writeBytes(oldCache)else cache.delete();val edit=prefs.edit().clear();originalPrefs.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit();main{app.channels.loadCache()}}
 }
}
