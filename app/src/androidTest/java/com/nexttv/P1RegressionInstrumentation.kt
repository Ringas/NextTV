package com.nexttv

import android.app.*
import android.content.*
import android.os.*
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.nexttv.core.App
import com.nexttv.core.data.*
import com.nexttv.core.tv.*
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Isolated metadata/IO fixtures; playback URLs are copied from the user's real subscription. */
class P1RegressionInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 private fun tree(n:AccessibilityNodeInfo?=uiAutomation.rootInActiveWindow):String=if(n==null)"" else buildString{append(n.text?:"");for(i in 0 until n.childCount){append('\n');append(tree(n.getChild(i)))}}
 private fun focused(n:AccessibilityNodeInfo?=uiAutomation.rootInActiveWindow):String{if(n==null)return "";if(n.isFocused)return tree(n);for(i in 0 until n.childCount){val v=focused(n.getChild(i));if(v.isNotBlank())return v};return ""}
 private fun await(label:String,timeoutMs:Long=15000,condition:()->Boolean){val end=SystemClock.uptimeMillis()+timeoutMs;while(SystemClock.uptimeMillis()<end){if(condition())return;SystemClock.sleep(80)};error("Timeout $label focus=${focused()} UI=${tree()}")}
 private fun pass(s:String)=sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})
 private fun key(k:Int){sendKeyDownUpSync(k);waitForIdleSync();SystemClock.sleep(150)}
 private fun field(o:Any,n:String,v:Any){o.javaClass.getDeclaredField(n).apply{isAccessible=true}.set(o,v)}
 private fun launch()=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TvActivity
 private fun click(text:String){fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo?{if(n==null)return null;if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null};var n=find(uiAutomation.rootInActiveWindow)?:error("Missing $text ${tree()}");while(!n.isClickable)n=n.parent;check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(250)}
 override fun onStart(){var activity:TvActivity?=null;var status=Activity.RESULT_OK;var report="";val originalSp=targetContext.getSharedPreferences("nexttv",0);val originalValues=originalSp.all.toMap()
 originalSp.edit().putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("start_live",false).commit()
 val bootstrap=launch();waitForIdleSync();SystemClock.sleep(1200);runOnMainSync{bootstrap.finish()};waitForIdleSync()
 val app=App.get();val oldPrefs=app.prefs;val oldChannels=app.channels;val oldEpg=app.epg
 val dir=File(targetContext.cacheDir,"p1-${System.nanoTime()}").apply{mkdirs()}
 val isolated=object:ContextWrapper(targetContext){override fun getApplicationContext():Context=this;override fun getFilesDir()=dir;override fun getCacheDir()=dir;override fun getSharedPreferences(n:String,m:Int)=baseContext.getSharedPreferences("p1-${dir.name}-$n",m)}
 val prefs=Prefs(isolated).apply{autoUpdateOnStart=false;autoUpdateEpg=false;startLive=false;preloadNext=false}
 val repo=ChannelRepository(isolated,prefs);val epg=EpgRepository(isolated,prefs)
 try{
  val real=oldChannels.channels.firstOrNull()?:error("User's real subscription cache required")
  runOnMainSync{field(app,"prefs",prefs);field(app,"channels",repo);field(app,"epg",epg)}
  // F02: details/selection refresh does not steal focus; removing the focused row recovers.
  activity=launch();val a=activity!!
  val options=mutableStateOf(listOf(ChoiceOption("1","P1线路一","加载中"),ChoiceOption("2","P1线路二","加载中")))
  val selected=mutableStateOf("1")
  runOnMainSync{a.setContent{ChoiceDialog("P1焦点验证",options.value,selected.value,onDismiss={}){}}}
  await("initial choice"){focused().contains("P1线路一")};key(KeyEvent.KEYCODE_DPAD_DOWN);await("second choice"){focused().contains("P1线路二")}
  repeat(4){i->runOnMainSync{options.value=options.value.map{it.copy(detail="异步详情 $i")};selected.value=if(i%2==0)"2"else"1"};SystemClock.sleep(180);check(focused().contains("P1线路二")){"Details stole focus"}}
  pass("F02 async option detail and selected-ID updates preserve actual second-row focus (4 rounds)")
  runOnMainSync{options.value=options.value.filter{it.id!="2"}};await("removed choice fallback"){focused().contains("P1线路一")};pass("F02 removal of focused option restores a valid enabled row")
  runOnMainSync{options.value=listOf(options.value.first().copy(enabled=false),ChoiceOption("3","P1线路三"))};await("disabled choice fallback"){focused().contains("P1线路三")};pass("F02 disabling the focused option restores another enabled option")
  runOnMainSync{a.finish()};waitForIdleSync();activity=null
  // F06: a valid cache must survive rejection of oversized playlist and XML fields.
  val playlist=File(dir,"input.m3u");playlist.writeText("#EXTM3U\n#EXTINF:-1 tvg-id=\"p1\" group-title=\"P1\",P1真实源\n${real.url}\n")
  repo.upsertSource(Source("p1","P1订阅",playlist.absolutePath,kind=Source.KIND_FILE))
  check(runBlocking{repo.refreshAll()}.ok==1);val before=File(dir,"channels.json").readBytes();val beforeIds=repo.channels.map{it.id}
  playlist.outputStream().use{out->out.write("#EXTM3U\n".toByteArray());repeat(600){out.write(ByteArray(16384){32});out.write(10)}}
  check(runBlocking{repo.refreshAll()}.failed==1);check(repo.channels.map{it.id}==beforeIds);check(File(dir,"channels.json").readBytes().contentEquals(before));pass("F06 oversized playlist rejected, previous in-memory channels and atomic cache retained")
  val xml=File(dir,"guide.xml");prefs.epgUrl=xml.absolutePath
  val now=System.currentTimeMillis();val fmt=SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")}
  fun guide(title:String,start:Long=now-60000,stop:Long=now+60000,id:String="p1")="<tv><channel id=\"$id\"><display-name>P1真实源</display-name></channel><programme channel=\"$id\" start=\"${fmt.format(Date(start))}\" stop=\"${fmt.format(Date(stop))}\"><title>$title</title></programme></tv>"
  xml.writeText(guide("P1旧节目"));check(runBlocking{epg.refresh(true)});val channel=repo.channels.first();check(epg.nowNext(channel).first?.title=="P1旧节目")
  val epgFile=File(dir,"epg_feeds").listFiles()!!.first{it.extension=="json"};val oldCache=epgFile.readBytes()
  xml.writeText(guide("A".repeat(9000)));check(!runBlocking{epg.refresh(true)});check(epg.nowNext(channel).first?.title=="P1旧节目");check(epgFile.readBytes().contentEquals(oldCache));pass("F06 oversized EPG field rejected without replacing usable programme/cache")
  // F03 actual Android XML parser + repository, future-only and ended-only data.
  xml.writeText(guide("P1未来节目",now+3600000,now+7200000));check(runBlocking{epg.refresh(true)});check(epg.nowNext(channel).first==null);check(epg.nowNext(channel).second?.title=="P1未来节目")
  xml.writeText(guide("P1已结束",now-7200000,now-3600000));check(runBlocking{epg.refresh(true)});check(epg.nowNext(channel)==(null to null));pass("F03 Android XML/repository future-only and past-only guides never report a live programme")
  // F01/F05 actual player. No fake media: both fixture identities retain the user's actual URL/headers.
  val ca=real.copy(id="p1-a",name="P1频道A",tvgId="p1-a",sourceId="p1-a",group="分组A",number=1,routes=emptyList())
  val cb=real.copy(id="p1-b",name="P1频道B",tvgId="p1-b",sourceId="p1-b",group="分组B",number=2,routes=emptyList())
  runOnMainSync{field(repo,"channels",listOf(ca,cb));field(repo,"sources",listOf(Source("p1-a","P1订阅A",playlist.absolutePath),Source("p1-b","P1订阅B",playlist.absolutePath)));field(repo,"groups",listOf("分组A","分组B"))}
  xml.writeText(guide("P1-B当前节目",id="p1-b"));check(runBlocking{epg.refresh(true)})
  activity=launch();await("A card"){tree().contains(ca.name)};click(ca.name);await("actual player"){PlaybackSession.active!=null};val engine=PlaybackSession.active!!
  await("real stream first frame",45000){engine.rendered};pass("real user's source URL decoded first frame in production player")
  key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_2);key(KeyEvent.KEYCODE_MENU);await("shortcuts"){tree().contains("音轨")};SystemClock.sleep(2100);check(engine.current.id==ca.id);key(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(1800);check(engine.current.id==ca.id);pass("F01 pending numeric tune is cancelled by shortcut menu and never resumes after dismissal")
  key(KeyEvent.KEYCODE_2);key(KeyEvent.KEYCODE_DPAD_CENTER);await("browser"){activity!!.browserDiagnostics!=null};SystemClock.sleep(1900);check(engine.current.id==ca.id);pass("F01 pending numeric tune cannot execute behind channel browser")
  fun fail(){runOnMainSync{engine.javaClass.getDeclaredMethod("failed",String::class.java,Int::class.javaPrimitiveType).apply{isAccessible=true}.invoke(engine,"P1 injected decoder failure",engine.tuneSerial)}}
  var serial=engine.tuneSerial;fail();await("single route retry"){engine.tuneSerial>serial};serial=engine.tuneSerial;fail();await("auto next source"){engine.current.sourceId==cb.sourceId&&engine.tuneSerial>serial};await("browser source B refreshed"){tree().contains("分组B")&&tree().contains(cb.name)&&!tree().contains(ca.name)}
  pass("F05 actual failure recovery A→B recreates open browser with source-B group and channels")
  key(KeyEvent.KEYCODE_DPAD_RIGHT);await("B EPG"){tree().contains("P1-B当前节目")};key(KeyEvent.KEYCODE_ESCAPE);await("all browser layers closed"){activity!!.browserDiagnostics==null};pass("F05 recovered source-B guide is linked and Back closes every browser layer once")
  key(KeyEvent.KEYCODE_1);await("numeric A setup"){engine.current.id==ca.id};key(KeyEvent.KEYCODE_1);key(KeyEvent.KEYCODE_DPAD_UP);await("explicit zap wins"){engine.current.id==cb.id};SystemClock.sleep(1800);check(engine.current.id==cb.id);pass("F01 directional channel change cancels an earlier numeric timer")
  key(KeyEvent.KEYCODE_1);await("ordinary numeric tune"){engine.current.id==ca.id};pass("F01 plain full-screen numeric tuning remains available")
  key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_2);check(uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME));SystemClock.sleep(1800);uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n com.nexttv/com.nexttv.core.tv.TvActivity").use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()}};SystemClock.sleep(1800);check(engine.current.id==ca.id);pass("F01 pending numeric tune is discarded on background entry and cannot resume later")
  key(KeyEvent.KEYCODE_MENU);await("Home action"){tree().contains("主页")};click("主页");await("Home restored"){PlaybackSession.active==null&&tree().contains("频道分类：")};key(KeyEvent.KEYCODE_2);check(uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME));SystemClock.sleep(1800);uiAutomation.executeShellCommand("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n com.nexttv/com.nexttv.core.tv.TvActivity").use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()}};SystemClock.sleep(1800);check(PlaybackSession.active==null);pass("F01 Home pending numeric tune also cancels on background entry")
  key(KeyEvent.KEYCODE_2);key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(1800);check(PlaybackSession.active==null);pass("F01 Home Back discards pending numeric input without starting playback")
  key(KeyEvent.KEYCODE_2);key(KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(1800);check(PlaybackSession.active==null);pass("F01 Home directional navigation cancels pending numeric input")
  report="All P1 targeted Android groups passed; isolated metadata fixtures cleaned, no original subscription/cache overwritten.\n"
 }catch(t:Throwable){status=Activity.RESULT_CANCELED;report=t.stackTraceToString()}
 finally{runOnMainSync{activity?.finish()};waitForIdleSync();runOnMainSync{field(app,"prefs",oldPrefs);field(app,"channels",oldChannels);field(app,"epg",oldEpg)};isolated.getSharedPreferences("nexttv",0).edit().clear().commit();dir.deleteRecursively()
 val edit=originalSp.edit().clear();for((k,v) in originalValues)when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())};edit.commit()
 }
 finish(status,Bundle().apply{putString("stream",report)})
 }
}
