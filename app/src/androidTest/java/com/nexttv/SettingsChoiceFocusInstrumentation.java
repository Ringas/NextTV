// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;
/** No accessibility-click shortcuts: selected row restores, then D-pad changes mode in both directions. */
public class SettingsChoiceFocusInstrumentation extends SettingsAppearanceInstrumentation {
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("reduce_motion",false).putBoolean("channel_previews",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit();activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home",()->focus().equals("频道"));appearance();pressTo("动态效果");
  for(int i=0;i<6;i++){final boolean reduced=i%2==1;key(KeyEvent.KEYCODE_DPAD_CENTER);await("choice shown",()->find("标准",root())!=null&&find("减少",root())!=null);checkSelectedFocus();await("current mode focused",()->focus().contains(reduced?"减少":"标准"));key(reduced?KeyEvent.KEYCODE_DPAD_UP:KeyEvent.KEYCODE_DPAD_DOWN);await("other mode reachable",()->focus().contains(reduced?"标准":"减少"));key(KeyEvent.KEYCODE_DPAD_CENTER);await("mode changed and dialog closed",()->prefs.getBoolean("reduce_motion",false)!=reduced&&(find("标准",root())==null||find("减少",root())==null));await("original settings row restored",()->focus().contains("动态效果"));}
  pass("six remote-only standard/reduced cycles restore selected focus and allow both-direction navigation");result="Choice focus "+passed+" group passed";
 }catch(Throwable e){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(e)+"\n"+text()+"\n"+dump();try{shot("choice-focus-failure");}catch(Exception ignored){}}finally{restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
