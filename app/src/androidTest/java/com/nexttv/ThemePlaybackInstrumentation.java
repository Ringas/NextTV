// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;
/** Actual light/dark playback glass, sharing the same live player without a restart. */
public class ThemePlaybackInstrumentation extends ViewingDesignInstrumentation {
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("light_glass",true).putBoolean("channel_previews",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit();
  activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("real channels",()->text().contains("CCTV1综合"));click("CCTV1综合");rendered();key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_INFO);await("light info",()->text().contains("继续播放"));rateFits();shot("light-player-info");key(KeyEvent.KEYCODE_MENU);await("light quick",()->text().contains("音轨"));shot("light-player-quick");click("解码方式");await("light choices",()->text().contains("硬件解码"));shot("light-decoder");key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_DPAD_CENTER);await("light channels",()->browser().optString("region").equals("channels"));key(KeyEvent.KEYCODE_DPAD_RIGHT);await("light EPG",()->browser().optString("pane").equals("epg"));String playing=browser().getString("playing");shot("light-browser");
  prefs.edit().putBoolean("light_glass",false).commit();SystemClock.sleep(400);if(!browser().optString("playing").equals(playing))throw new IllegalStateException("Theme change retuned channel");shot("dark-browser");key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_MENU);await("dark quick",()->text().contains("音轨"));shot("dark-player-quick");key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_INFO);shot("dark-player-info");
  pass("real live information/quick/browser/decoder share light and dark glass; changing colour preserves playing channel");result="Theme playback "+passed+" group passed";
 }catch(Throwable e){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(e)+"\n"+text();try{shot("theme-playback-failure");}catch(Exception ignored){}}finally{restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
