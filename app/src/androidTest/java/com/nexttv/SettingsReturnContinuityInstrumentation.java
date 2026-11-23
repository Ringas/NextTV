// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;
/** Repeated physical Back keys must not be undone by a delayed popup focus-restoration job. */
public class SettingsReturnContinuityInstrumentation extends SettingsRebuildInstrumentation {
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("reduce_motion",false).commit();activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home",()->focus().equals("频道"));
  for(int round=0;round<5;round++){
   focusLabel("设置");key(KeyEvent.KEYCODE_DPAD_DOWN);await("first category",()->focus().equals("订阅与节目单"));for(int i=0;i<2;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);await("appearance category",()->focus().equals("频道与外观"));key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);await("theme row",()->focus().contains("界面主题"));key(KeyEvent.KEYCODE_DPAD_CENTER);await("theme popup",()->text().contains("深色")&&text().contains("浅色"));
   for(int i=0;i<3;i++){sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(100);}
   await("three Back presses return to Settings nav "+round,()->focus().equals("设置"));SystemClock.sleep(350);if(!focus().equals("设置"))throw new IllegalStateException("Late restore stole top navigation focus");
  }
  pass("five repeated popup/content/category Back sequences reach top Settings and delayed restoration never steals it back");
  result="Settings return continuity passed";
 }catch(Throwable t){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(t)+"\n"+text();try{shot("return-failure");}catch(Exception ignored){}}finally{if(activity!=null)runOnMainSync(()->activity.finish());restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
