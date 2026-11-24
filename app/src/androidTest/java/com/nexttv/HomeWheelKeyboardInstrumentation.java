// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;

/** Real mouse wheel → physical arrow-key handoff, without assigning focus to hide a lost-focus bug. */
public class HomeWheelKeyboardInstrumentation extends com.nexttv.support.NextTvInstrumentation {
 private Activity activity;private android.content.SharedPreferences prefs;private java.util.Map<String,?> saved;private int passed;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private AccessibilityNodeInfo root(){AccessibilityNodeInfo n=getUiAutomation().getRootInActiveWindow();if(n!=null)n.refresh();return n;}

 private JSONObject grid()throws Exception{java.io.StringWriter writer=new java.io.StringWriter();runOnMainSync(()->activity.dump("",null,new java.io.PrintWriter(writer),null));java.util.regex.Matcher m=java.util.regex.Pattern.compile("NextTVGrid (\\{[^\\n]+\\})").matcher(writer.toString());return m.find()?new JSONObject(m.group(1)):new JSONObject();}
 private boolean card(){String f=focus();return !f.isEmpty()&&!f.equals("频道")&&!f.equals("分类")&&!f.equals("收藏")&&!f.equals("设置");}
 private void wheel(float amount,long settle){MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;p.toolType=MotionEvent.TOOL_TYPE_MOUSE;MotionEvent.PointerCoords c=new MotionEvent.PointerCoords();c.x=500;c.y=350;c.setAxisValue(MotionEvent.AXIS_VSCROLL,amount);long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_SCROLL,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{c},0,0,1,1,0,0,InputDevice.SOURCE_MOUSE,0);getUiAutomation().injectInputEvent(e,true);e.recycle();if(settle>0)SystemClock.sleep(settle);}
 protected void launch()throws Exception{activity=startActivitySync(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("initial Channels menu",()->focus().equals("频道"));SystemClock.sleep(800);}
 private void visible()throws Exception{await("focused card fully visible",()->{JSONObject g=grid();return g.getInt("itemTop")>=g.getInt("viewportStart")&&g.getInt("itemBottom")<=g.getInt("viewportEnd");});}
 protected void pass(String text){passed++;Bundle b=new Bundle();b.putString("stream","PASS "+text+"\n");sendStatus(0,b);}
 private void backToMenu()throws Exception{key(KeyEvent.KEYCODE_ESCAPE);await("Back to Channels menu",()->focus().equals("频道")&&grid().getInt("first")==0);}
 private void restore(){if(activity!=null)runOnMainSync(()->activity.finish());if(saved==null)return;android.content.SharedPreferences.Editor e=prefs.edit().clear();for(java.util.Map.Entry<String,?> i:saved.entrySet()){Object v=i.getValue();String k=i.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof java.util.Set)e.putStringSet(k,(java.util.Set<String>)v);}e.commit();}
 @Override public void onStart(){int result=Activity.RESULT_OK;String report="";try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("previews",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("grid_mode",true).commit();launch();
  wheel(-18,700);await("initial wheel moves beyond first row",()->grid().getInt("first")>=8);int scrolled=grid().getInt("first");key(KeyEvent.KEYCODE_DPAD_DOWN);await("category focus",()->focus().equals("分类"));key(KeyEvent.KEYCODE_DPAD_DOWN);await("keyboard reaches visible card after initial wheel",()->card());visible();if(grid().getInt("first")==0)throw new IllegalStateException("Handoff reset grid to top");pass("initial Channels menu → wheel scroll → Down/category → Down/current visible card");
  key(KeyEvent.KEYCODE_DPAD_RIGHT);await("Right moves between cards",()->card());visible();key(KeyEvent.KEYCODE_DPAD_DOWN);await("Down moves between rows",()->card());visible();pass("keyboard card movement remains active after wheel handoff");
  wheel(-18,700);key(KeyEvent.KEYCODE_DPAD_DOWN);await("card focus resumes after wheel disposes prior card",()->card());visible();pass("wheel from a previously focused card resumes keyboard navigation");
  backToMenu();wheel(-18,0);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);await("immediate keyboard handoff",()->card());SystemClock.sleep(600);visible();pass("immediate wheel → keyboard does not keep scrolling the focused card away");
  backToMenu();key(KeyEvent.KEYCODE_DPAD_DOWN);await("category after Back",()->focus().equals("分类"));key(KeyEvent.KEYCODE_DPAD_DOWN);await("first card after Back",()->card()&&grid().getInt("selectedIndex")==0);pass("Back still resets to menu; normal entry starts at first card");
  report="Home wheel/keyboard: "+passed+" groups passed\n";
 }catch(Throwable t){result=Activity.RESULT_CANCELED;report=android.util.Log.getStackTraceString(t);}finally{restore();}Bundle b=new Bundle();b.putString("stream",report);finish(result,b);}
}
