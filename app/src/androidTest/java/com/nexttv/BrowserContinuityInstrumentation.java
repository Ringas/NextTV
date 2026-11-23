// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.graphics.Rect;
/** Real list continuity and reversible input, without assigning focus directly to hide lost focus. */
public class BrowserContinuityInstrumentation extends ViewingDesignInstrumentation {
 void burst(int key,int count){long t=SystemClock.uptimeMillis();sendKeySync(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,key,0));for(int i=1;i<count;i++){SystemClock.sleep(65);sendKeySync(new KeyEvent(t,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,key,i));}sendKeySync(new KeyEvent(t,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,key,0));}
 void horizontalDrag(int source,int from,int to){
  long down=SystemClock.uptimeMillis();
  for(int i=0;i<=10;i++){
   int action=i==0?MotionEvent.ACTION_DOWN:i==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
   MotionEvent.PointerProperties property=new MotionEvent.PointerProperties();property.id=0;property.toolType=source==InputDevice.SOURCE_MOUSE?MotionEvent.TOOL_TYPE_MOUSE:MotionEvent.TOOL_TYPE_FINGER;
   MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();point.x=from+(to-from)*i/10f;point.y=240;point.pressure=1;
   MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{property},new MotionEvent.PointerCoords[]{point},0,i==10?0:MotionEvent.BUTTON_PRIMARY,1,1,0,0,source,0);
   if(!getUiAutomation().injectInputEvent(event,true))throw new IllegalStateException("Pointer injection failed");
   event.recycle();SystemClock.sleep(18);
  }
 }
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("reduce_motion",false).commit();
  activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home channel",()->text().contains("CCTV1综合"));click("CCTV1综合");rendered();key(KeyEvent.KEYCODE_DPAD_CENTER);await("channels focus",()->browser().optString("region").equals("channels"));
  String original=browser().getString("candidate");burst(KeyEvent.KEYCODE_DPAD_DOWN,12);SystemClock.sleep(450);shot("channel-list-rapid-down");burst(KeyEvent.KEYCODE_DPAD_UP,12);await("reversible repeated list navigation",()->browser().optString("candidate").equals(original));
  Rect focusBounds=new Rect(),screen=new Rect();FreshAccessibilityNodes.focused(root()).getBoundsInScreen(focusBounds);root().getBoundsInScreen(screen);SystemClock.sleep(450);FreshAccessibilityNodes.focused(root()).getBoundsInScreen(focusBounds);if(!screen.contains(focusBounds))throw new IllegalStateException("List focus clipped "+focusBounds);
  pass("twelve 65ms Down repeats followed by twelve Up repeats return to the same channel with complete visible focus");
  key(KeyEvent.KEYCODE_DPAD_LEFT);await("groups",()->browser().optString("region").equals("groups"));String group=browser().getString("group");key(KeyEvent.KEYCODE_DPAD_RIGHT);await("channel-only pane",()->browser().optString("pane").equals("channels"));
  for(int i=0;i<5;i++){key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_RIGHT);}
  await("linked groups settle",()->browser().optString("region").equals("channels")&&browser().optString("group").equals(group));shot("groups-settled");
  key(KeyEvent.KEYCODE_DPAD_RIGHT);await("EPG",()->browser().optString("pane").equals("epg"));key(KeyEvent.KEYCODE_DPAD_LEFT);await("channels restored",()->browser().optString("pane").equals("channels"));for(int source:new int[]{InputDevice.SOURCE_TOUCHSCREEN,InputDevice.SOURCE_MOUSE}){
   horizontalDrag(source,290,160);await("drag opens groups",()->browser().optString("pane").equals("groups"));
   horizontalDrag(source,150,280);await("drag closes groups to channels only",()->browser().optString("pane").equals("channels"));
   horizontalDrag(source,150,280);await("next drag opens EPG",()->browser().optString("pane").equals("epg"));
   horizontalDrag(source,290,160);await("drag closes EPG to channels only",()->browser().optString("pane").equals("channels"));
  }
  pass("touch and mouse traverse Groups / Channels / EPG one step per drag with a channel-only intermediate view");
  key(KeyEvent.KEYCODE_ESCAPE);await("all overlays closed",()->!dump().contains("NextTVBrowser"));
  pass("repeated group/channel transitions preserve selected group; EPG return and single-Back dismissal remain unified");
  key(KeyEvent.KEYCODE_DPAD_CENTER);
  await("reopen at playing channel",()->browser().optString("candidate").equals(original));
  key(KeyEvent.KEYCODE_DPAD_LEFT);
  await("group focused",()->browser().optString("region").equals("groups"));
  key(KeyEvent.KEYCODE_DPAD_DOWN);
  await("browsed a different group",()->!browser().optString("group").equals(group));
  key(KeyEvent.KEYCODE_ESCAPE);
  await("browser closed without tuning",()->!dump().contains("NextTVBrowser"));
  key(KeyEvent.KEYCODE_DPAD_CENTER);
  await("candidate and group reset to live",()->browser().optString("pane").equals("channels")&&browser().optString("candidate").equals(original)&&browser().optString("playing").equals(original)&&browser().optString("group").equals(group));
  pass("browsing another group without tuning resets the next open to the playing channel and group");
  for(int i=0;i<3;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);
  SystemClock.sleep(400);
  Rect anchor=new Rect();FreshAccessibilityNodes.focused(root()).getBoundsInScreen(anchor);
  String anchorCandidate=browser().getString("candidate");
  key(KeyEvent.KEYCODE_DPAD_DOWN);
  await("candidate advances one row",()->!browser().optString("candidate").equals(anchorCandidate));
  await("focus returns to fixed second-row position",()->{
   android.view.accessibility.AccessibilityNodeInfo node=FreshAccessibilityNodes.focused(root());
   if(node==null)return false;
   Rect current=new Rect();node.getBoundsInScreen(current);
   return Math.abs(current.top-anchor.top)<=3;
  });
  pass("after a Down step the channel list scrolls beneath the same focus position");
  result="Browser continuity "+passed+" groups passed";
 }catch(Throwable t){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(t)+"\n"+text();try{shot("continuity-failure");}catch(Exception ignored){}}finally{restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
