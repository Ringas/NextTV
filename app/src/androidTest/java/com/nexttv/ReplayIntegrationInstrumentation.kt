@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv
import android.app.*
import android.content.Intent
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo as Node
import com.nexttv.core.data.Source
import com.nexttv.core.App
import com.nexttv.core.tv.*
import com.nexttv.core.data.replayAddress
import kotlinx.coroutines.runBlocking
import java.io.File
class ReplayIntegrationInstrumentation:Instrumentation(){
 private fun find(n:Node?):Node?{if(n==null)return null;if(n.text?.toString()=="CCTV1综合")return n;for(i in 0 until n.childCount)find(n.getChild(i))?.let{return it};return null}
 private fun text(value:String,n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null)return null;if(n.text?.toString()==value)return n;for(i in 0 until n.childCount)text(value,n.getChild(i))?.let{return it};return null}
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 override fun onStart(){waitForIdleSync();val app=App.get();val group=app.prefs.selectedGroup;var activity:Activity?=null
 try{app.prefs.selectedGroup="";if(app.channels.sources.none{it.url=="http://192.0.2.115:8787/live.m3u"})app.channels.upsertSource(Source("real-regression","真实测试源","http://192.0.2.115:8787/live.m3u"));runBlocking{app.channels.refreshAll();check(app.epg.refresh(true))};activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));val readyBy=SystemClock.uptimeMillis()+15000;while(find(uiAutomation.rootInActiveWindow)==null&&SystemClock.uptimeMillis()<readyBy)SystemClock.sleep(100);var n=find(uiAutomation.rootInActiveWindow)?:error("CCTV1 missing");while(!n.isClickable)n=n.parent?:error("No click parent");check(n.performAction(Node.ACTION_CLICK));val end=SystemClock.uptimeMillis()+15000;while(PlaybackSession.active==null&&SystemClock.uptimeMillis()<end)SystemClock.sleep(80);val engine=PlaybackSession.active?:error("No player");val channel=app.channels.channels.first{it.name=="CCTV1综合"};val candidates=app.epg.programsFor(channel).filter{p->channel.playbackRoutes.any{replayAddress(it,p,System.currentTimeMillis())!=null}}.takeLast(3);check(candidates.isNotEmpty()){ "No available archive metadata" };var successful=false;val record=StringBuilder()
 for(program in candidates.reversed()){runOnMainSync{check(engine.playReplay(channel,program))};val until=SystemClock.uptimeMillis()+35000;while(!engine.rendered&&engine.error.isBlank()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);record.append(program.title).append(" rendered=").append(engine.rendered).append(" error=").append(engine.error).append('\n');if(engine.rendered&&engine.replay==program){successful=true;break}}
 File(targetContext.filesDir,"fix13-replay.txt").writeText(record.toString());check(successful){record.toString()};var initialPosition=0L;runOnMainSync{initialPosition=engine.player.currentPosition};SystemClock.sleep(1500);var progressed=false;runOnMainSync{progressed=engine.rendered&&engine.replay!=null&&engine.player.currentPosition>initialPosition};check(progressed){"Replay frame did not advance"};waitForIdleSync();uiAutomation.takeScreenshot()?.let{b->File(targetContext.filesDir,"fix13-replay.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()};sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MENU);SystemClock.sleep(300);var live=text("返回直播")?:error("Replay lacks return-live button");while(!live.isClickable)live=live.parent?:error("Return-live not clickable");check(live.performAction(Node.ACTION_CLICK));val until=SystemClock.uptimeMillis()+35000;while(!engine.rendered&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);check(engine.replay==null&&engine.rendered);app.prefs.selectedGroup=group;runOnMainSync{activity?.finish()};finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS real XMLTV archive metadata → replay video → return to live video\n"+record)})
 }catch(t:Throwable){app.prefs.selectedGroup=group;runOnMainSync{activity?.finish()};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}finally{app.prefs.selectedGroup=group;runOnMainSync{activity?.finish()}}}
}
