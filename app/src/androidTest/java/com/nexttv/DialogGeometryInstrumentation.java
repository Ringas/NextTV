// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.graphics.Rect;import android.os.*;import android.view.*;import android.view.accessibility.AccessibilityNodeInfo;import java.util.*;
/** Actual dialog geometry, stable native host and outside-tap regression. */
public class DialogGeometryInstrumentation extends InteractionRefinementInstrumentation {
 AccessibilityNodeInfo pane(AccessibilityNodeInfo n){if(n==null||!n.refresh())return null;if(n.getPaneTitle()!=null)return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=pane(n.getChild(i));if(found!=null)return found;}return null;}
 Rect centered()throws Exception{
  await("dialog pane semantics",()->pane(root())!=null);AccessibilityNodeInfo n=pane(root());Rect r=new Rect(),viewport=new Rect();n.getBoundsInScreen(r);root().getBoundsInScreen(viewport);
  if(Math.abs(r.exactCenterX()-viewport.exactCenterX())>2||Math.abs(r.exactCenterY()-viewport.exactCenterY())>2)throw new IllegalStateException("Off-center pane="+r+" viewport="+viewport+" title="+n.getPaneTitle());
  if(r.width()>=viewport.width()||r.height()>=viewport.height())throw new IllegalStateException("Sheet fills viewport "+r);return r;
 }
 void outside(){Rect viewport=new Rect();root().getBoundsInScreen(viewport);long t=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,viewport.left+12,viewport.top+12,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);if(!getUiAutomation().injectInputEvent(e,true))throw new IllegalStateException("Touch injection rejected");e.recycle();if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(50);}}
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";RawMotionRecording recording=null;try{
  android.accessibilityservice.AccessibilityServiceInfo service=getUiAutomation().getServiceInfo();service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(service);
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("reduce_motion",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit();
  activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home",()->focus().equals("频道"));key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);centered();shot("centered-category-before-outside");outside();await("outside closes category",()->!text().contains("频道分类"));pass("standalone choices are centered and outside taps dismiss without extra close buttons");
  focusText("设置");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);await("sources",()->text().contains("重新拉取"));key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);await("source actions",()->text().contains("编辑")&&text().contains("停用"));centered();int window=root().getWindowId();
  if(inlineRecording)recording=RawMotionRecording.start(this,"nexttv-1.3.40-dialog-transition.mp4");
  List<String> bounds=new ArrayList<>();
  for(int i=0;i<4;i++){
   key(KeyEvent.KEYCODE_DPAD_CENTER);await("editor",()->text().contains("M3U 订阅地址"));for(int sample=0;sample<5;sample++){bounds.add("edit="+centered());if(root().getWindowId()!=window)throw new IllegalStateException("Editor recreated native window");SystemClock.sleep(30);}if(i==0)shot("centered-source-editor");
   key(KeyEvent.KEYCODE_ESCAPE);await("exact Edit restored",()->focus().equals("编辑"));for(int sample=0;sample<5;sample++){bounds.add("actions="+centered());if(root().getWindowId()!=window)throw new IllegalStateException("Back recreated native window");SystemClock.sleep(30);}if(i==0)shot("centered-source-actions");
  }
  pass("four edit/back cycles keep one native window and centered bounds during size changes: "+bounds);
  key(KeyEvent.KEYCODE_DPAD_CENTER);await("editor before keyboard",()->text().contains("M3U 订阅地址"));key(KeyEvent.KEYCODE_DPAD_CENTER);await("editor keyboard visible",()->ime());outside();await("outside closes editor and keyboard",()->text().contains("编辑")&&!text().contains("M3U 订阅地址")&&!ime());await("Edit focus after outside",()->focus().equals("编辑"));
  key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);await("delete confirmation",()->text().contains("删除订阅？"));centered();outside();await("outside returns to source actions",()->text().contains("编辑")&&!text().contains("删除订阅？"));outside();await("outside dismisses source actions",()->!text().contains("停用")&&text().contains("重新拉取"));pass("confirmation is centered; outside tap respects confirmation → actions → source list hierarchy");result="Dialog geometry: "+passed+" groups passed";
 }catch(Throwable t){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(t);}finally{if(recording!=null)recording.close();restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
