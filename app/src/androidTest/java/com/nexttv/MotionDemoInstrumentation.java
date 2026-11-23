// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;
/** Raw on-device motion preview. Not a frame-rate or latency benchmark. */
public class MotionDemoInstrumentation extends ViewingDesignInstrumentation {
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";RawMotionRecording recording=null;try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("grid_mode",true).putBoolean("reduce_motion",false).commit();
  activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home",()->focus().equals("频道"));if(inlineRecording)recording=RawMotionRecording.start(this,"nexttv-1.3.40-interface-motion.mp4");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);
  for(int i=0;i<8;i++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(65);}SystemClock.sleep(500);shot("motion-demo-grid");key(KeyEvent.KEYCODE_MENU);SystemClock.sleep(1000);key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_ESCAPE);await("top menu",()->focus().equals("频道"));click("CCTV1综合");rendered();key(KeyEvent.KEYCODE_MENU);SystemClock.sleep(1000);key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_DPAD_CENTER);await("browser",()->browser().optString("region").equals("channels"));
  for(int i=0;i<9;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);shot("motion-demo-list");for(int i=0;i<9;i++)key(KeyEvent.KEYCODE_DPAD_UP);for(int i=0;i<3;i++){key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_RIGHT);}key(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(800);key(KeyEvent.KEYCODE_ESCAPE);
  pass("raw motion preview traverses actual grid, in-card favorite action, quick controls, list and linked panes; not an FPS benchmark");result="Motion preview completed";
 }catch(Throwable e){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(e)+"\n"+text();}finally{if(recording!=null)recording.close();restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
