// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.graphics.Rect;import android.view.accessibility.AccessibilityNodeInfo;
/** Real category → scrolled channel cards → Favorites/Settings → Channels round trips. */
public class HomeCategoryStateInstrumentation extends InteractionRefinementInstrumentation {
 void stableChoice()throws Exception{
  checkSelectedFocus();Rect initial=new Rect();AccessibilityNodeInfo first=FreshAccessibilityNodes.focused(root());if(first==null)throw new IllegalStateException("Missing selected category");first.getBoundsInScreen(initial);
  for(int i=0;i<5;i++){SystemClock.sleep(50);Rect next=new Rect();AccessibilityNodeInfo n=FreshAccessibilityNodes.focused(root());if(n==null)throw new IllegalStateException("Restored focus disappeared");n.getBoundsInScreen(next);if(Math.abs(initial.top-next.top)>1||Math.abs(initial.bottom-next.bottom)>1)throw new IllegalStateException("Selected category bounced: "+initial+" / "+next);}
 }
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("grid_mode",true).putBoolean("channel_previews",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit();
  activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("Channels",()->focus().equals("频道"));int total=grid().getInt("count");String firstId=grid().getString("firstId");if(total<2)throw new IllegalStateException("Requires populated real source");
  for(String destination:new String[]{"收藏","设置"}){
   focusText("频道");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);await("category dialog",()->text().contains("频道分类"));key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);await("filtered first card",()->!grid().optString("group").isEmpty()&&grid().optInt("selectedIndex")==0);
   int count=grid().getInt("count");String chosenGroup=grid().getString("group");if(count>=total)throw new IllegalStateException("Fixture did not select a genuinely filtered category: "+chosenGroup+" count="+count);key(KeyEvent.KEYCODE_DPAD_UP);await("filtered category header",()->focus().equals("分类"));key(KeyEvent.KEYCODE_DPAD_CENTER);stableChoice();key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_DPAD_DOWN);await("filtered first entry",()->grid().getInt("selectedIndex")==0);for(int i=0;i<5;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);if(count>12&&grid().getInt("first")==0)throw new IllegalStateException("Fixture did not scroll selected group");
   focusText(destination);focusText("频道");await("complete reset after "+destination,()->grid().optString("group").isEmpty()&&grid().optInt("count")==total&&grid().optString("firstId").equals(firstId)&&grid().optInt("first")==0&&grid().optInt("offset")==0&&grid().optInt("selectedIndex")==-1);
   if(!text().contains("全部频道"))throw new IllegalStateException("Wrong restored title");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);checkSelectedFocus();
   stableChoice();
   key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_DPAD_DOWN);await("first card after reset",()->grid().getInt("selectedIndex")==0);pass("filtered/scrolled Channels → "+destination+" → Channels resets dataset/title/choice/viewport/focus; selected choice does not bounce (group="+chosenGroup+", filtered="+count+", total="+total+")");
  }
  result="Home category state: "+passed+" groups passed";
 }catch(Throwable t){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(t)+"\n"+text()+"\n"+dump();}finally{restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);}
}
