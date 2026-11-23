package com.nexttv
import android.app.*
import android.content.Intent
import android.graphics.Rect
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo as Node
import com.nexttv.core.App
import com.nexttv.core.tv.*
import com.nexttv.core.data.replayAddress
import kotlin.math.abs

/** Uses an actual replay-capable programme from the fixed real subscription. Debug friend APIs. */
class ReplayTimelineInstrumentation:Instrumentation(){
 private var inlineRecord=false
 private var tag="normal"
 override fun onCreate(args:Bundle?){super.onCreate(args);inlineRecord=args?.getString("inlineRecord")=="true";tag=args?.getString("shotTag")?:"normal";start()}
 private fun text()=FreshAccessibilityNodes.text(uiAutomation.rootInActiveWindow)
 private fun focus()=FreshAccessibilityNodes.focused(uiAutomation.rootInActiveWindow)
 private fun await(label:String,condition:()->Boolean){val end=SystemClock.uptimeMillis()+45000;while(SystemClock.uptimeMillis()<end){if(condition())return;SystemClock.sleep(60)};error("$label UI=${text()}")}
 private fun key(code:Int){sendKeyDownUpSync(code);SystemClock.sleep(180)}
 private fun find(label:String,n:Node?=uiAutomation.rootInActiveWindow):Node?{if(n==null||!n.refresh())return null;if(n.text?.toString()==label)return n;for(i in 0 until n.childCount)find(label,n.getChild(i))?.let{return it};return null}
 private fun sliderFocused()=focus()?.let{it.rangeInfo!=null&&it.stateDescription?.toString()?.contains("，共")==true}==true
 private fun shot(name:String){val b=uiAutomation.takeScreenshot()!!;val values=android.content.ContentValues().apply{put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"nexttv-1.3.40-$tag-$name.png");put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png");put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/NextTVAudit")};val uri=targetContext.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!;targetContext.contentResolver.openOutputStream(uri)!!.use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 override fun onStart(){val prefs=targetContext.getSharedPreferences("nexttv",0);val saved=prefs.all;var activity:Activity?=null;var recording:RawMotionRecording?=null;var code=Activity.RESULT_OK;val lines=mutableListOf<String>();fun pass(s:String){lines+="PASS $s";sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})}
 try{
 prefs.edit().putBoolean("start_live",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putInt("preload_mode",0).commit()
 activity=startActivitySync(Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("real channel card"){find("CCTV1综合")!=null};var card=find("CCTV1综合")!!;while(!card.isClickable)card=card.parent;check(card.performAction(Node.ACTION_CLICK));await("actual live frame"){PlaybackSession.active?.rendered==true};val engine=PlaybackSession.active!!
 key(KeyEvent.KEYCODE_MENU);await("live quick actions"){find("音轨")!=null};val liveButton=Rect();val liveTitle=Rect();find("音轨")!!.getBoundsInScreen(liveButton);find("CCTV1综合")!!.getBoundsInScreen(liveTitle);val liveGap=liveButton.top-liveTitle.top;key(KeyEvent.KEYCODE_ESCAPE)
 val app=App.get();await("actual EPG"){app.epg.programsFor(engine.current).isNotEmpty()}
 val programme=app.epg.programsFor(engine.current).lastOrNull{it.stop<System.currentTimeMillis()&&engine.current.playbackRoutes.any{r->replayAddress(r,it,System.currentTimeMillis())!=null}}?:error("No actual replay-capable programme")
 runOnMainSync{check(engine.playReplay(engine.current,programme))};await("actual replay frame"){engine.ready&&engine.rendered};var duration=0L
 runOnMainSync{duration=engine.player.duration.takeIf{it>0}?:programme.stop-programme.start;check(duration>120000);engine.player.playWhenReady=false;engine.seekReplay(duration/3)}
 SystemClock.sleep(400);if(inlineRecord)recording=RawMotionRecording.start(this,"nexttv-1.3.40-replay-timeline.mp4")
 key(KeyEvent.KEYCODE_MENU);await("pause action"){FreshAccessibilityNodes.text(focus()).contains("继续播放")};val replayButton=Rect();val replayTitle=Rect();find("音轨")!!.getBoundsInScreen(replayButton);find("CCTV1综合")!!.getBoundsInScreen(replayTitle);check(abs((replayButton.top-replayTitle.top)-liveGap)<=2){"Replay pushed header/buttons: live=$liveGap replay=${replayButton.top-replayTitle.top}"};pass("replay retains the original programme progress position and live information/menu height");shot("replay-timeline-normal");key(KeyEvent.KEYCODE_DPAD_UP);shot("replay-up-diagnostic");sendStatus(0,Bundle().apply{putString("stream","FOCUS_AFTER_UP text=${focus()?.text} desc=${focus()?.contentDescription} state=${focus()?.stateDescription}\n")});await("Up enters progress"){sliderFocused()};val serial=engine.tuneSerial;val route=engine.routeIndex;var before=0L;runOnMainSync{before=engine.player.currentPosition}
 repeat(2){key(KeyEvent.KEYCODE_DPAD_UP)};check(sliderFocused());var after=0L;runOnMainSync{after=engine.player.currentPosition};check(abs(after-before)<1500){"Up sought replay $before -> $after"};key(KeyEvent.KEYCODE_DPAD_DOWN);await("Down restores original action"){FreshAccessibilityNodes.text(focus()).contains("继续播放")};pass("Up enters timeline and stops at its upper boundary without seeking; Down restores the originating action")
 key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);await("audio action"){FreshAccessibilityNodes.text(focus()).trim()=="音轨"};key(KeyEvent.KEYCODE_DPAD_UP);await("timeline from another action"){sliderFocused()};key(KeyEvent.KEYCODE_DPAD_DOWN);await("Down remembers audio"){FreshAccessibilityNodes.text(focus()).trim()=="音轨"};key(KeyEvent.KEYCODE_DPAD_UP)
 runOnMainSync{before=engine.player.currentPosition};key(KeyEvent.KEYCODE_DPAD_RIGHT);runOnMainSync{after=engine.player.currentPosition};check(abs(after-before-30000)<2000){"Right seek $before -> $after"};key(KeyEvent.KEYCODE_DPAD_LEFT);runOnMainSync{after=engine.player.currentPosition};check(abs(after-before)<2000){"Left seek $before -> $after"};check(engine.tuneSerial==serial&&engine.routeIndex==route&&engine.replay!=null);shot("replay-timeline-focused");pass("all replay actions share Up-to-timeline; Down restores the exact button; Left/Right seek 30s without retuning or changing route")
 val bounds=Rect();focus()!!.getBoundsInScreen(bounds);check(((activity as TvActivity).directPointerRegion?.height?:0f)>=40*targetContext.resources.displayMetrics.density-1){"Timeline routed touch band too small $bounds"};val time=SystemClock.uptimeMillis()
 for((action,x)in listOf(MotionEvent.ACTION_DOWN to .25f,MotionEvent.ACTION_MOVE to .5f,MotionEvent.ACTION_UP to .5f)){val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,bounds.left+bounds.width()*x,bounds.top-5f,0);event.source=InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(event,true));event.recycle();SystemClock.sleep(100)}
 runOnMainSync{after=engine.player.currentPosition};check(after.toDouble()/duration in .40.. .60){"Touch did not seek to midpoint: $after / $duration"};check(engine.tuneSerial==serial&&engine.routeIndex==route);shot("replay-timeline-touch")
 val mouseAt=SystemClock.uptimeMillis()
 for((action,x)in listOf(MotionEvent.ACTION_DOWN to .25f,MotionEvent.ACTION_MOVE to .75f,MotionEvent.ACTION_UP to .75f)){
  val props=arrayOf(MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE});val coords=arrayOf(MotionEvent.PointerCoords().apply{this.x=bounds.left+bounds.width()*x;y=bounds.exactCenterY();pressure=if(action==MotionEvent.ACTION_UP)0f else 1f})
  val event=MotionEvent.obtain(mouseAt,SystemClock.uptimeMillis(),action,1,props,coords,0,if(action==MotionEvent.ACTION_UP)0 else MotionEvent.BUTTON_PRIMARY,1f,1f,0,0,InputDevice.SOURCE_MOUSE,0);check(uiAutomation.injectInputEvent(event,true));event.recycle();SystemClock.sleep(100)
 }
 runOnMainSync{after=engine.player.currentPosition};check(after.toDouble()/duration in .65.. .85){"Mouse drag did not seek: $after / $duration"};check(engine.tuneSerial==serial&&engine.routeIndex==route);shot("replay-timeline-mouse");key(KeyEvent.KEYCODE_DPAD_DOWN);await("touch back to keyboard action"){FreshAccessibilityNodes.text(focus()).trim()=="音轨"};key(KeyEvent.KEYCODE_ESCAPE);await("Back closes controls"){!text().contains("解码方式")};check(engine.replay!=null);pass("touch and mouse dragging seek actual replay, preserve route, hand back to keyboard and Back closes the overlay")
 fun swipeReplay(from:Float,to:Float){
  val down=SystemClock.uptimeMillis()
  for(i in listOf(0, 1, 2, 3, 4, 8)){
   // The final movement is delivered only on UP, as with batched touch events.
   val action=if(i==0)MotionEvent.ACTION_DOWN else if(i==8)MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
   val display=targetContext.resources.displayMetrics
   val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,display.widthPixels*.45f+display.density*(from+(to-from)*i/8f),display.heightPixels*.45f,0)
   event.source=InputDevice.SOURCE_TOUCHSCREEN
   check(uiAutomation.injectInputEvent(event,true));event.recycle();SystemClock.sleep(20)
  }
 }
 runOnMainSync{before=engine.player.currentPosition}
 swipeReplay(0f,90f)
 runOnMainSync{after=engine.player.currentPosition}
 check(abs(after-before-60000)<2000){"Right swipe should advance: $before -> $after"}
 swipeReplay(90f,0f)
 runOnMainSync{after=engine.player.currentPosition}
 check(abs(after-before)<2000){"Left swipe should rewind: $before -> $after"}
 pass("fullscreen touch drag accumulates two 30-second steps in either direction without reversing keyboard or timeline behavior")

 }catch(t:Throwable){code=Activity.RESULT_CANCELED;lines+=t.stackTraceToString()}finally{recording?.close();runOnMainSync{activity?.finish()};val edit=prefs.edit().clear();saved.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit()};finish(code,Bundle().apply{putString("stream",lines.joinToString("\n"))})
 }
}
