package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nexttv.core.App
import com.nexttv.core.data.Source
import com.nexttv.core.tv.TvActivity
import com.nexttv.core.tv.PlaybackSession
import com.nexttv.core.ui.Ui
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Legacy pre-1.3.3 hierarchy probe. Superseded by StyleNavigationInstrumentation.
 * Its group-title focus and first-card Back assertions intentionally do not define current acceptance. */
@Deprecated("Use StyleNavigationInstrumentation for current Back rules")
class NavigationBackInstrumentation:Instrumentation(){
 private lateinit var activity:Activity
 private val passed=mutableListOf<String>();private val prefs by lazy{targetContext.getSharedPreferences("nexttv",0)}
 private var originalGroup="";private var originalFavorites:Set<String> = emptySet();private var endpoint:String?=null
 override fun onCreate(args:Bundle?){super.onCreate(args);endpoint=args?.getString("realSource");start()}
 private fun tree(n:AccessibilityNodeInfo?=uiAutomation.rootInActiveWindow):String=if(n==null)""else buildString{append(n.text?:"");append('\n');for(i in 0 until n.childCount)append(tree(n.getChild(i)))}
 private fun find(test:(AccessibilityNodeInfo)->Boolean,n:AccessibilityNodeInfo?=uiAutomation.rootInActiveWindow):AccessibilityNodeInfo?{if(n==null)return null;if(test(n))return n;for(i in 0 until n.childCount)find(test,n.getChild(i))?.let{return it};return null}
 private fun await(label:String,time:Long=10000,condition:()->Boolean){val end=SystemClock.uptimeMillis()+time;while(SystemClock.uptimeMillis()<end){if(condition())return;SystemClock.sleep(70)};error("Timeout $label: focused=${focused()} UI=${tree().take(1800)}")}
 private fun text(s:String)=await(s){tree().contains(s)}
 private fun imeVisible():Boolean{var visible=false;runOnMainSync{visible=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true};return visible}
 private fun closeEditor(){key(KeyEvent.KEYCODE_ESCAPE);await("IME closed"){!imeVisible()};SystemClock.sleep(350);if(tree().contains("保存"))key(KeyEvent.KEYCODE_ESCAPE)}
 private fun key(code:Int){sendKeyDownUpSync(code);waitForIdleSync();SystemClock.sleep(100)}
 private fun click(label:String){var n=find({it.text?.toString()==label})?:error("No $label");while(!n.isClickable)n=n.parent?:error("No clickable $label");n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);SystemClock.sleep(100);check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));waitForIdleSync();SystemClock.sleep(100)}
 private fun focus(label:String){var n=find({it.text?.toString()==label})?:error("No focus $label");while(!n.isFocusable)n=n.parent?:error("No focusable $label");check(n.performAction(AccessibilityNodeInfo.ACTION_FOCUS));await("focused $label"){focused().lineSequence().any{it==label}}}
 private fun focused():String=find({it.isFocused})?.let{tree(it).trim()}.orEmpty()
 private fun launch(){activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();text("频道分类：");SystemClock.sleep(2300)}
 private fun shot(name:String){uiAutomation.takeScreenshot()?.let{b->File(targetContext.filesDir,"back12-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun test(name:String,body:()->Unit){body();passed+=name;sendStatus(0,Bundle().apply{putString("stream","PASS $name\n")})}
 private fun restore(){runOnMainSync{MotionProbe.frame=null};prefs.edit().putString("selected_group",originalGroup).putStringSet("favorites",originalFavorites).commit()}
 override fun onStart(){try{
  originalGroup=prefs.getString("selected_group","").orEmpty();originalFavorites=prefs.getStringSet("favorites",emptySet()).orEmpty().toSet();prefs.edit().putString("selected_group","").commit()
  launch();val app=App.get()
  if(app.channels.channels.isEmpty()&&endpoint!=null){runOnMainSync{app.channels.upsertSource(Source("navigation-test","测试订阅",endpoint!!))};runBlocking{app.channels.refreshAll()};runOnMainSync{activity.finish()};launch()}
  check(app.channels.channels.isNotEmpty())
  test("three menus and no standalone guide"){check(find({it.text?.toString()=="节目表"})==null);focus("收藏");text("我的收藏");focus("设置");text("节目单地址");key(KeyEvent.KEYCODE_DPAD_RIGHT);check(focused()=="设置");shot("three-menus")}
  test("settings editor returns to its row then group then settings nav"){
   click("节目单地址");text("保存");closeEditor();await("settings row focus"){focused().contains("节目单地址")};key(KeyEvent.KEYCODE_BACK);await("settings group"){focused()=="节目单"};key(KeyEvent.KEYCODE_BACK);await("settings top"){focused()=="设置"};check(!activity.isFinishing)
  }
  test("source add editor and source operations return by level"){
   click("管理播放源");text("添加地址");click("添加地址");text("订阅地址");closeEditor();text("扫码添加");check(!tree().contains("保存"))
   var source=find({it.isClickable&&tree(it).contains("已启用 ›")})?:error("No source row");check(source.performAction(AccessibilityNodeInfo.ACTION_CLICK));text("编辑");click("编辑");text("保存");closeEditor();text("删除");text("编辑");click("删除");text("确认删除");key(KeyEvent.KEYCODE_BACK);text("编辑");check(!tree().contains("确认删除"));key(KeyEvent.KEYCODE_BACK);text("添加地址");key(KeyEvent.KEYCODE_BACK);await("source parent row"){focused().contains("管理播放源")};key(KeyEvent.KEYCODE_BACK);await("channel settings group"){focused()=="频道列表"};key(KeyEvent.KEYCODE_BACK);await("settings nav"){focused()=="设置"}
  }
  test("top favorites and settings use double back and interactions cancel it"){
   key(KeyEvent.KEYCODE_BACK);text("再按一次退出");check(!activity.isFinishing);key(KeyEvent.KEYCODE_DPAD_LEFT);text("我的收藏");check(!tree().contains("再按一次退出"));key(KeyEvent.KEYCODE_ESCAPE);text("再按一次退出");check(!activity.isFinishing);SystemClock.sleep(2100);key(KeyEvent.KEYCODE_ESCAPE);text("再按一次退出");check(!activity.isFinishing);key(KeyEvent.KEYCODE_ESCAPE);await("application finished"){activity.isFinishing}
  }
  launch()
  val first=Ui.displayName(app.channels.channels.first().name)
  test("scrolled channel grid returns continuously before top navigation"){
   val frames=CopyOnWriteArrayList<Pair<Int,Int>>();runOnMainSync{MotionProbe.frame={_,_,i,o->frames.add(i to o)}}
   focus(first);repeat(10){key(KeyEvent.KEYCODE_DPAD_DOWN)};await("scrolled grid"){frames.lastOrNull()?.first?.let{it>0}==true};val start=frames.last();frames.clear();key(KeyEvent.KEYCODE_BACK)
   await("top reached"){frames.lastOrNull()==(0 to 0)&&focused().lineSequence().any{it==first}};check(!activity.isFinishing);check(!tree().contains("再按一次退出"));val positions=frames.distinct();check(positions.size>=3&&positions.any{it!=(0 to 0)&&it!=start}){"Back jumped without intermediate scroll positions: $positions"};File(targetContext.filesDir,"back12-scroll-frames.txt").writeText(positions.joinToString("\n"));runOnMainSync{MotionProbe.frame=null};shot("grid-top");key(KeyEvent.KEYCODE_BACK);await("channel nav"){focused()=="频道"}
  }
  test("favorites scroll has the same back hierarchy"){
   prefs.edit().putStringSet("favorites",app.channels.channels.take(40).map{it.id}.toSet()).commit();focus("收藏");text("我的收藏");focus(first);repeat(7){key(KeyEvent.KEYCODE_DPAD_DOWN)};key(KeyEvent.KEYCODE_ESCAPE);await("favorites first focus"){focused().lineSequence().any{it==first}};key(KeyEvent.KEYCODE_ESCAPE);await("favorites nav"){focused()=="收藏"};check(!activity.isFinishing)
  }
  test("top channel double back exits without switching menus"){
   focus("频道");key(KeyEvent.KEYCODE_BACK);text("再按一次退出");check(!activity.isFinishing);key(KeyEvent.KEYCODE_BACK);await("channel exit"){activity.isFinishing}
  }
  launch()
  test("live source and overlay Back does not arm exit"){
   focus(first);key(KeyEvent.KEYCODE_ENTER);await("player session"){PlaybackSession.active!=null};key(KeyEvent.KEYCODE_1);await("real video",30000){var ready=false;runOnMainSync{ready=PlaybackSession.active?.rendered==true};ready};key(KeyEvent.KEYCODE_MENU);text("更多");key(KeyEvent.KEYCODE_ESCAPE);await("all overlays closed"){!tree().contains("频道列表")};check(!tree().contains("再按一次退出播放"));check(PlaybackSession.active!=null)
  }
  test("player double back expires and other input cancels confirmation"){
   key(KeyEvent.KEYCODE_BACK);text("再按一次退出播放");key(KeyEvent.KEYCODE_DPAD_RIGHT);text("音量");key(KeyEvent.KEYCODE_BACK);text("再按一次退出播放");check(PlaybackSession.active!=null);SystemClock.sleep(2100);key(KeyEvent.KEYCODE_BACK);text("再按一次退出播放");check(PlaybackSession.active!=null);shot("live-confirm");key(KeyEvent.KEYCODE_BACK);text("全部频道");check(PlaybackSession.active==null&&!activity.isFinishing)
  }
  test("live exit cannot carry its confirmation into app exit"){
   key(KeyEvent.KEYCODE_BACK);await("home top focus"){focused()=="频道"};key(KeyEvent.KEYCODE_BACK);text("再按一次退出");check(!activity.isFinishing);shot("app-confirm");key(KeyEvent.KEYCODE_BACK);await("app exit after live exit"){activity.isFinishing}
  }
  restore();File(targetContext.filesDir,"back12-results.txt").writeText(passed.joinToString("\n"));finish(Activity.RESULT_OK,Bundle().apply{putString("stream","Navigation: ${passed.size} groups passed\n")})
 }catch(t:Throwable){runCatching{restore()};shot("failure");File(targetContext.filesDir,"back12-results.txt").writeText(passed.joinToString("\n")+"\nFAIL "+t.stackTraceToString());finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}}
}
