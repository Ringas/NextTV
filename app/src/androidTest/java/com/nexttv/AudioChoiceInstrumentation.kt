@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv

import android.app.*
import android.content.Intent
import android.media.*
import android.os.*
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo as Node
import com.nexttv.core.App
import com.nexttv.core.tv.*
import java.io.File
import java.nio.ByteBuffer

/** Generates a local two-AAC-track fixture. Never adds or edits any subscription. */
class AudioChoiceInstrumentation:Instrumentation(){
 private lateinit var activity:Activity
 private fun tree(n:Node?=uiAutomation.rootInActiveWindow):String=if(n==null)""else buildString{append(n.text?:"");for(i in 0 until n.childCount)append('\n').append(tree(n.getChild(i)))}
 private fun find(text:String,n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null)return null;if(n.text?.toString()==text)return n;for(i in 0 until n.childCount)find(text,n.getChild(i))?.let{return it};return null}
 private fun await(label:String,timeout:Long=15000,condition:()->Boolean){val until=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<until){if(condition())return;SystemClock.sleep(60)};error("$label: ${tree().take(1000)}")}
 private fun click(text:String){await(text){find(text)!=null};var n=find(text)!!;while(!n.isClickable)n=n.parent?:error("Not clickable $text");check(n.performAction(Node.ACTION_CLICK));waitForIdleSync()}
 private fun choices(engine:PlaybackSession):List<PlaybackSession.AudioChoice>{var result=emptyList<PlaybackSession.AudioChoice>();runOnMainSync{result=engine.audioChoices()};return result}
 private fun fixture(file:File){
  val codec=MediaCodec.createEncoderByType("audio/mp4a-latm")
  val format=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,2).apply{setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);setInteger(MediaFormat.KEY_BIT_RATE,96000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,4096)}
  val samples=mutableListOf<Pair<Long,ByteArray>>();var output:MediaFormat?=null
  try{codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();var frames=0;var eos=false;var finished=false;val info=MediaCodec.BufferInfo();val until=SystemClock.uptimeMillis()+15000
   while(!finished&&SystemClock.uptimeMillis()<until){
    if(!eos){val i=codec.dequeueInputBuffer(1000);if(i>=0){val count=minOf(1024,44100*3-frames);val b=codec.getInputBuffer(i)!!;b.clear();repeat(count*4){b.put(0)};codec.queueInputBuffer(i,0,count*4,frames*1000000L/44100,if(count==0)MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0);frames+=count;eos=count==0}}
    val index=codec.dequeueOutputBuffer(info,1000)
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)output=codec.outputFormat
    else if(index>=0){if(info.size>0&&(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.presentationTimeUs>=0){val b=codec.getOutputBuffer(index)!!;b.position(info.offset);b.limit(info.offset+info.size);val bytes=ByteArray(info.size);b.get(bytes);samples+=info.presentationTimeUs to bytes};finished=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false)}
   }
   check(finished&&samples.isNotEmpty());val mux=MediaMuxer(file.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
   try{val first=mux.addTrack(output!!);val second=mux.addTrack(output!!);mux.start();val period=samples.last().first+23220
    repeat(10){cycle->samples.forEach{(pts,bytes)->val sample=MediaCodec.BufferInfo().apply{set(0,bytes.size,cycle*period+pts,0)};mux.writeSampleData(first,ByteBuffer.wrap(bytes),sample);mux.writeSampleData(second,ByteBuffer.wrap(bytes),sample)}};mux.stop()
   }finally{mux.release()}
  }finally{runCatching{codec.stop()};codec.release()}
 }
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){waitForIdleSync();val app=App.get();val saved=targetContext.getSharedPreferences("nexttv",0).all;val file=File(targetContext.cacheDir,"two-audio-choice.mp4")
 fun cleanup(){if(::activity.isInitialized){runOnMainSync{activity.finish()};waitForIdleSync()};val e=targetContext.getSharedPreferences("nexttv",0).edit();for(k in listOf("selected_group","previews","preload_next","auto_update","auto_update_epg")){when(val v=saved[k]){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);else->e.remove(k)}};e.commit();file.delete()}
 try{fixture(file);targetContext.getSharedPreferences("nexttv",0).edit().putString("selected_group","").putBoolean("previews",false).putBoolean("preload_next",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit();activity=startActivitySync(Intent(targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(1500);click("CCTV1综合");await("real stream first frame",40000){PlaybackSession.active?.rendered==true};val engine=PlaybackSession.active!!
 runOnMainSync{engine.player.repeatMode=androidx.media3.common.Player.REPEAT_MODE_ONE;engine.player.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)));engine.player.prepare();engine.player.play()}
 await("two supported audio tracks"){choices(engine).count{it.supported}==2};val second=choices(engine).last();sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);click("音轨");await("audio picker"){tree().contains("音轨选择")};click(second.label);await("selected second native track"){choices(engine).any{it.id==second.id&&it.selected}};check(!tree().contains("音轨选择"));sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);click("音轨");await("second choice retained"){tree().contains("音轨选择")};check(choices(engine).count{it.selected}==1);sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);check(!tree().contains("音轨选择"));cleanup();finish(Activity.RESULT_OK,Bundle().apply{putString("stream","PASS local generated dual-AAC fixture: audio menu appears, second actual Media3 track selected, selection retained, Back closes overlays; subscription unchanged\n")})
 }catch(t:Throwable){cleanup();finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream",t.stackTraceToString())})}
 }
}
