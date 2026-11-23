package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nexttv.core.App
import com.nexttv.core.tv.TvActivity
import com.nexttv.core.tv.PlaybackSession
import com.nexttv.core.ui.Ui
import java.io.File

/** Real cached subscription; validates removed-card focus, scroll reset and actual selected IDs. */
class CategoryFocusInstrumentation:Instrumentation(){
 private lateinit var activity:Activity
 private val passed=mutableListOf<String>()
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 private fun tree(n:AccessibilityNodeInfo?=uiAutomation.rootInActiveWindow):String=if(n==null)""else buildString{append(n.text?:"");append('\n');for(i in 0 until n.childCount)append(tree(n.getChild(i)))}
 private fun find(test:(AccessibilityNodeInfo)->Boolean,n:AccessibilityNodeInfo?=uiAutomation.rootInActiveWindow):AccessibilityNodeInfo?{if(n==null)return null;if(test(n))return n;for(i in 0 until n.childCount)find(test,n.getChild(i))?.let{return it};return null}
 private fun await(label:String,condition:()->Boolean){val until=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<until){if(condition())return;SystemClock.sleep(80)};error("Timeout $label: ${tree().take(1200)}")}
 private fun key(code:Int){sendKeyDownUpSync(code);waitForIdleSync();SystemClock.sleep(100)}
 private fun click(n:AccessibilityNodeInfo){var node=n;while(!node.isClickable)node=node.parent?:error("No clickable parent");check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));waitForIdleSync()}
 private fun select(group:String){
  await("category header ready"){find({it.text?.toString()?.startsWith("频道分类：")==true})!=null}
  click(find({it.text?.toString()?.startsWith("频道分类：")==true})?:error("No category header"))
  await("category dialog"){find({it.text?.toString()=="频道分类"})!=null}
  val label=group.ifBlank{"全部频道"}
  val options=listOf("")+App.get().channels.groups
  val current=options.indexOf(App.get().prefs.selectedGroup).coerceAtLeast(0)
  val target=options.indexOf(group).coerceAtLeast(0)
  await("selected category focus"){find({it.isFocused})?.let{tree(it).contains(options[current].ifBlank{"全部频道"})}==true}
  repeat(kotlin.math.abs(target-current)){key(if(target>current)KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)}
  await("category target visible"){find({it.text?.toString()==label})!=null}
  val chip=find({it.text?.toString()==label})?:error("No category $label")
  click(chip)
 }
 private fun checkFocus(group:String){
  val app=App.get();val expected=app.channels.channelsIn(group.ifBlank{null},false,"").first()
  await("new first card $group"){find({it.isFocused})?.let{tree(it).contains(Ui.displayName(expected.name))}==true}
  key(KeyEvent.KEYCODE_ENTER)
  await("selected channel"){PlaybackSession.active!=null}
  check(PlaybackSession.active!!.current.id==expected.id){"Focused card and selected channel differ"}
  key(KeyEvent.KEYCODE_MENU);await("player controls mounted"){tree().contains("选台")}
  key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK)
  await("live exit confirmation"){tree().contains("再按一次退出播放")}
  key(KeyEvent.KEYCODE_BACK)
  await("home restored"){PlaybackSession.active==null&&tree().contains("频道分类：")}
 }
 private fun pass(label:String){passed+=label;sendStatus(0,Bundle().apply{putString("stream","PASS $label\n")})}
 override fun onStart(){var original="";try{
  activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();SystemClock.sleep(2300)
  val app=App.get();original=app.prefs.selectedGroup
  val firstAll=app.channels.channels.first().id
  val groups=app.channels.groups.filter{app.channels.channelsIn(it,false,"").isNotEmpty()}
  val removedFirst=groups.first{app.channels.channelsIn(it,false,"").first().id!=firstAll}
  select("");await("all home"){tree().contains("全部频道")}
  repeat(8){key(KeyEvent.KEYCODE_DPAD_DOWN)}
  select(removedFirst);checkFocus(removedFirst);pass("scrolled all → category removes old first card; new first selected correctly")
  select(removedFirst);checkFocus(removedFirst);pass("same category reselected")
  for(g in (groups+listOf("",removedFirst,""))){select(g);checkFocus(g);pass("switch ${g.ifBlank{"全部频道"}} and select actual first ID")}
  runOnMainSync{app.prefs.selectedGroup="__removed_group_for_layout_test__";activity.finish()}
  activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(2300)
  await("empty category"){tree().contains("当前分组或筛选条件下没有频道")}
  select(removedFirst);checkFocus(removedFirst);pass("empty removed category → real category")
  runOnMainSync{app.prefs.selectedGroup=original}
  File(targetContext.filesDir,"category-focus-results.txt").writeText(passed.joinToString("\n"))
  finish(Activity.RESULT_OK,Bundle().apply{putString("stream","Category focus: ${passed.size} groups passed\n")})
 }catch(t:Throwable){runCatching{runOnMainSync{App.get().prefs.selectedGroup=original}};File(targetContext.filesDir,"category-focus-results.txt").writeText(passed.joinToString("\n")+"\nFAIL "+t.stackTraceToString());finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}}
}
