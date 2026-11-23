// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.view.accessibility.AccessibilityNodeInfo;import org.json.*;import java.util.*;import android.graphics.Rect;
/** Exercises actual directional keys, repeated input and context gestures against Debug or Release. */
public class InteractionRefinementInstrumentation extends ViewingDesignInstrumentation {
 boolean ime(){for(android.view.accessibility.AccessibilityWindowInfo w:getUiAutomation().getWindows())if(w.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD)return true;return false;}
 JSONObject grid()throws Exception{java.util.regex.Matcher m=java.util.regex.Pattern.compile("NextTVGrid (\\{[^\\n]+\\})").matcher(dump());return m.find()?new JSONObject(m.group(1)):new JSONObject();}
 void focusText(String label)throws Exception{await("actionable focus "+label,()->{AccessibilityNodeInfo n=find(label,root());while(n!=null&&!n.isFocusable())n=n.getParent();return n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_FOCUS);});await(label,()->focus().contains(label));}
 AccessibilityNodeInfo selectedRow(AccessibilityNodeInfo n){if(n==null||!n.refresh())return null;if(n.isSelected()||n.isChecked())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo r=selectedRow(n.getChild(i));if(r!=null)return r;}return null;}
 void checkSelectedFocus()throws Exception{await("selected choice gets real keyboard focus",()->{AccessibilityNodeInfo selected=selectedRow(root()),focused=FreshAccessibilityNodes.focused(root());if(selected==null||focused==null)return false;Rect a=new Rect(),b=new Rect();selected.getBoundsInScreen(a);focused.getBoundsInScreen(b);return a.equals(b);});}
 void mouseContext()throws Exception{
  Rect b=new Rect();FreshAccessibilityNodes.focused(root()).getBoundsInScreen(b);
  long t=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){
   MotionEvent.PointerProperties props=new MotionEvent.PointerProperties();props.id=0;props.toolType=MotionEvent.TOOL_TYPE_MOUSE;
   MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=b.centerX();coords.y=b.centerY();coords.pressure=1;
   MotionEvent e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{props},new MotionEvent.PointerCoords[]{coords},0,action==MotionEvent.ACTION_DOWN?MotionEvent.BUTTON_SECONDARY:0,1,1,0,0,InputDevice.SOURCE_MOUSE,0);
   getUiAutomation().injectInputEvent(e,true);e.recycle();
  }
 }
 void touchHold()throws Exception{Rect b=new Rect();FreshAccessibilityNodes.focused(root()).getBoundsInScreen(b);long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,b.centerX(),b.centerY(),0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();SystemClock.sleep(800);e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,b.centerX(),b.centerY(),0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();}
 void inlineAction(int mode)throws Exception{
  Rect card=new Rect();FreshAccessibilityNodes.focused(root()).getBoundsInScreen(card);
  if(mode==0)key(KeyEvent.KEYCODE_MENU);else if(mode==1)mouseContext();else if(mode==2)touchHold();else{
   sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));SystemClock.sleep(800);sendKeySync(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
  }
  await("inline favorite action "+mode,()->focus().contains("添加收藏")||text().contains("添加收藏"));
  AccessibilityNodeInfo button=find("添加收藏",root());Rect action=new Rect();button.getBoundsInScreen(action);
  if(!card.contains(action))throw new IllegalStateException("Favorite action is not inside card "+card+" / "+action);
  if(dump().contains("NextTVVideoFrame"))throw new IllegalStateException("Context gesture leaked short play");
  if(mode==0){shot("inline-favorite");key(KeyEvent.KEYCODE_1);SystemClock.sleep(1200);if(dump().contains("NextTVVideoFrame")||!text().contains("添加收藏"))throw new IllegalStateException("Channel digits leaked through inline action");}key(KeyEvent.KEYCODE_ESCAPE);
  await("Back restores card",()->!text().contains("添加收藏"));
 }
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";try{
  android.accessibilityservice.AccessibilityServiceInfo service=getUiAutomation().getServiceInfo();service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(service);
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("grid_mode",true).putBoolean("reduce_motion",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).remove("favorites").commit();
  activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home",()->focus().equals("频道"));int fullCount=grid().optInt("count");String fullFirst=grid().optString("firstId");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);
  await("third column",()->grid().optInt("selectedIndex")==2);
  int columns=activity.getResources().getConfiguration().screenWidthDp<900?3:4;
  long t=SystemClock.uptimeMillis();int steps=8;java.util.Set<Integer> observed=new java.util.LinkedHashSet<>();observed.add(2);
  sendKeySync(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,0));
  for(int i=1;i<steps;i++){SystemClock.sleep(65);observed.add(grid().optInt("selectedIndex"));sendKeySync(new KeyEvent(t,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,i));}
  sendKeySync(new KeyEvent(t,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN,0));
  long settleEnd=SystemClock.uptimeMillis()+4000;
  while(SystemClock.uptimeMillis()<settleEnd){int at=grid().optInt("selectedIndex");observed.add(at);if(at==2+steps*columns)break;SystemClock.sleep(16);}
  await("rapid repeated down retains column",()->grid().optInt("selectedIndex")==2+steps*columns);shot("rapid-grid");observed.add(grid().optInt("selectedIndex"));for(int at:observed)if(at<2||at>2+steps*columns||at%columns!=2)throw new IllegalStateException("Intermediate focus left third column: "+observed);if(observed.size()<7)throw new IllegalStateException("Focus stayed behind during repeated scrolling: "+observed);
  pass("eight 65ms Down repeats preserve third column and update intermediate focused indices: "+observed);
  for(int mode=0;mode<4;mode++)inlineAction(mode);
  pass("remote Menu, mouse right click, touch hold and keyboard held Enter open card-contained favorite action without playing");
  key(KeyEvent.KEYCODE_ESCAPE);await("top Channels",()->focus().equals("频道"));key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);await("category choices",()->text().contains("频道分类"));
  key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);String chosen=focus();key(KeyEvent.KEYCODE_DPAD_CENTER);await("choice closed",()->!text().contains("频道分类"));
  await("new category first card settled",()->grid().optInt("selectedIndex")==0&&java.util.regex.Pattern.compile("(?m)^\\d{3,}$").matcher(focus()).find());SystemClock.sleep(120);key(KeyEvent.KEYCODE_DPAD_UP);await("filter restored",()->focus().equals("分类"));key(KeyEvent.KEYCODE_DPAD_CENTER);checkSelectedFocus();shot("category-remembered");key(KeyEvent.KEYCODE_ESCAPE);
  pass("reopened category dialog restores the actual selected option (including satellite group), not the first or arbitrary option");
  int filteredCount=grid().optInt("count");
  focusText("收藏");focusText("频道");
  await("category and grid reset together",()->grid().optString("group").isEmpty()&&grid().optInt("first")==0&&grid().optInt("offset")==0&&grid().optInt("selectedIndex")==-1);
  if(!text().contains("全部频道")||grid().optInt("count")!=fullCount||!grid().optString("firstId").equals(fullFirst))throw new IllegalStateException("Reset category did not restore full data");
  key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);checkSelectedFocus();key(KeyEvent.KEYCODE_ESCAPE);
  key(KeyEvent.KEYCODE_DPAD_DOWN);await("reset starts at first card",()->grid().optInt("selectedIndex")==0);
  pass("filtered category → Favorites → Channels resets label, selected choice, dataset, viewport and first-card entry together");
  focusText("设置");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);await("sources",()->text().contains("重新拉取"));
  key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);await("source actions",()->text().contains("编辑")&&text().contains("停用"));int operationWindow=root().getWindowId();key(KeyEvent.KEYCODE_DPAD_CENTER);await("editor",()->text().contains("M3U 订阅地址"));if(root().getWindowId()!=operationWindow)throw new IllegalStateException("Editor recreated the dialog window");shot("source-editor");await("input focused",()->focus().contains("M3U 订阅地址"));key(KeyEvent.KEYCODE_DPAD_CENTER);await("explicit keyboard",()->ime());key(KeyEvent.KEYCODE_ESCAPE);await("keyboard hidden first",()->!ime());if(!text().contains("M3U 订阅地址"))throw new IllegalStateException("First Esc skipped editor");key(KeyEvent.KEYCODE_ESCAPE);await("operation exact focus restored",()->focus().equals("编辑"));if(root().getWindowId()!=operationWindow)throw new IllegalStateException("Back recreated the parent dialog window");shot("source-actions-restored");key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_ESCAPE);
  pass("source editor hides IME first, then restores exact Edit focus in the same native dialog window");
  result="Interaction refinement "+passed+" groups passed";
 }catch(Throwable e){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(e)+"\n"+text()+"\n"+dump();try{shot("interaction-failure");}catch(Exception ignored){}}finally{restore();}
 Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
