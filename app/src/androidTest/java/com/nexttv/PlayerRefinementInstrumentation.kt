package com.nexttv
import android.app.*
import android.os.*
import android.view.KeyEvent as K
import android.view.accessibility.AccessibilityNodeInfo as N
import com.nexttv.core.App
import com.nexttv.core.tv.*
import com.nexttv.core.data.replayAddress
class PlayerRefinementInstrumentation:Instrumentation(){
 override fun onCreate(b:Bundle?){super.onCreate(b);start()}
 private fun text(n:N?):String=FreshAccessibilityNodes.text(n)
 private fun focused(n:N?):N?=FreshAccessibilityNodes.focused(n)
 private fun await(label:String,ms:Long=45000,p:()->Boolean){val end=SystemClock.uptimeMillis()+ms;while(SystemClock.uptimeMillis()<end){if(p())return;SystemClock.sleep(if(label=="captured overlay before replacement")5 else 70)};error(label+" replay="+PlaybackSession.active?.replay?.title+" serial="+PlaybackSession.active?.tuneSerial+" error="+PlaybackSession.active?.error+" UI="+text(uiAutomation.rootInActiveWindow))}
 private fun key(k:Int){sendKeyDownUpSync(k);SystemClock.sleep(180)}
 private fun shot(name:String){val b=uiAutomation.takeScreenshot()?:error("No image");val v=android.content.ContentValues().apply{put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"nexttv-1.3.37-$name.png");put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png");put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/NextTVAudit")};val uri=targetContext.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,v)!!;targetContext.contentResolver.openOutputStream(uri)!!.use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 override fun onStart(){val sp=targetContext.getSharedPreferences("nexttv",0);val saved=sp.all.toMap();var a:Activity?=null;var code=Activity.RESULT_OK;val lines=mutableListOf<String>();fun pass(s:String){lines+="PASS $s";sendStatus(0,Bundle().apply{putString("stream","PASS $s\n")})}
 try{
 sp.edit().putBoolean("start_live",false).putBoolean("keep_last_frame",true).putBoolean("previews",false).putInt("preload_mode",0).commit();a=startActivitySync(android.content.Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));val app=App.get();await("cache"){app.cacheReady&&app.channels.channels.isNotEmpty()}
 fun find(n:N?,label:String):N?{if(n==null||!n.refresh())return null;if(n.text?.toString()==label)return n;for(i in 0 until n.childCount)find(n.getChild(i),label)?.let{return it};return null}
 await("real home card"){find(uiAutomation.rootInActiveWindow,"CCTV1综合")!=null};var card=find(uiAutomation.rootInActiveWindow,"CCTV1综合")!!;while(!card.isClickable)card=card.parent;check(card.performAction(N.ACTION_CLICK));await("real video"){PlaybackSession.active?.rendered==true};val e=PlaybackSession.active!!;fun playingIntent():Boolean {var value=false;runOnMainSync{value=e.player.playWhenReady};return value};val live=app.epg.nowNext(e.current).first?:error("No actual current programme")
 key(K.KEYCODE_MENU)
 await("programme text visible"){find(uiAutomation.rootInActiveWindow,live.title)!=null}
 val titleBounds=android.graphics.Rect();find(uiAutomation.rootInActiveWindow,live.title)!!.getBoundsInScreen(titleBounds)
 check(titleBounds.width()>=targetContext.resources.displayMetrics.widthPixels*.28f){"Programme area still cramped: $titleBounds"}
 pass("actual programme title area is at least 28% of the window; identity and programme share one information header")
 key(K.KEYCODE_ESCAPE);key(K.KEYCODE_DPAD_CENTER);key(K.KEYCODE_DPAD_RIGHT);await("live programme focused"){text(focused(uiAutomation.rootInActiveWindow)).contains(live.title)};shot("epg-current-focus");key(K.KEYCODE_DPAD_UP);key(K.KEYCODE_DPAD_LEFT);key(K.KEYCODE_DPAD_RIGHT);await("reopened EPG resets live focus"){text(focused(uiAutomation.rootInActiveWindow)).contains(live.title)};pass("opening/reopening guide focuses actual current live programme");key(K.KEYCODE_ESCAPE)
 val c=e.current;val route=c.playbackRoutes.first();val two=c.copy(routes=listOf(route,route.copy(originalId="preserve-second-real-route")));runOnMainSync{e.tune(two)};await("two route real video"){e.rendered};val old=e.player
 runOnMainSync{check(e.switchRoute(1));check(e.player===old){"Old decoder released before capture could commit"}};await("captured overlay before replacement",3000){e.player!==old&&((a as com.nexttv.core.tv.TvActivity).videoFrameDiagnostics?.invoke()?.optInt("variation",0)?:0)>8};await("new real route frame"){e.rendered&&e.routeIndex==1};await("overlay removed after rendered frame"){(a as com.nexttv.core.tv.TvActivity).videoFrameDiagnostics?.invoke()?.optBoolean("retained")==false};pass("route retune waits for nonblank capture before replacing Player, removes snapshot after new frame")
 key(K.KEYCODE_MENU);key(K.KEYCODE_ESCAPE);key(K.KEYCODE_DPAD_LEFT);await("short route notice"){text(uiAutomation.rootInActiveWindow).lines().any{it=="线路 1"}};check(!text(uiAutomation.rootInActiveWindow).contains("线路 1/2"));shot("route-short-notice");await("route frame"){e.rendered}
 val p=app.epg.programsFor(c).lastOrNull{it.stop<System.currentTimeMillis()&&c.playbackRoutes.any{r->replayAddress(r,it,System.currentTimeMillis())!=null}}?:error("No real replay programme");runOnMainSync{check(e.playReplay(c,p))};await("actual replay video"){e.ready&&e.rendered}
 key(K.KEYCODE_SPACE);check(!playingIntent());key(K.KEYCODE_SPACE);check(playingIntent())
 key(K.KEYCODE_MENU);await("common pause button"){text(uiAutomation.rootInActiveWindow).contains("暂停")}
 key(K.KEYCODE_DPAD_CENTER);await("common continue button"){!playingIntent()&&text(uiAutomation.rootInActiveWindow).contains("继续播放")}
 key(K.KEYCODE_DPAD_CENTER);await("common continue takes effect"){playingIntent()}
 pass("replay Space and the same remote/mouse/touch pause-resume button control actual playback")
 key(K.KEYCODE_MENU);key(K.KEYCODE_ESCAPE);val index=e.routeIndex;key(K.KEYCODE_DPAD_RIGHT);await("seek forward feedback"){text(uiAutomation.rootInActiveWindow).contains("快进 30 秒")};key(K.KEYCODE_DPAD_LEFT);shot("replay-rewind-immediate");await("seek reverse feedback"){text(uiAutomation.rootInActiveWindow).contains("快退 30 秒")};shot("replay-rewind");check(e.routeIndex==index);key(K.KEYCODE_DPAD_RIGHT);await("repeat forward feedback"){text(uiAutomation.rootInActiveWindow).contains("快进 30 秒")};shot("replay-forward");SystemClock.sleep(2600);check(!text(uiAutomation.rootInActiveWindow).contains("快退 30 秒")&&!text(uiAutomation.rootInActiveWindow).contains("快进 30 秒"));pass("actual replay arrows show target/duration and direction, keep route, then dismiss feedback")
 }catch(t:Throwable){code=Activity.RESULT_CANCELED;lines+=t.stackTraceToString()}finally{runOnMainSync{a?.finish()};val edit=sp.edit().clear();saved.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit()};finish(code,Bundle().apply{putString("stream",lines.joinToString("\n"))})
 }
}
