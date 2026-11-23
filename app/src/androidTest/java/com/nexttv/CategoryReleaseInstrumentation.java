package com.nexttv;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
/** UI-only category regression against the actual R8 APK. */
public class CategoryReleaseInstrumentation extends Instrumentation {
 private int passed;private java.util.ArrayList<String> choices;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private AccessibilityNodeInfo find(String value,boolean prefix,AccessibilityNodeInfo n){if(n==null)return null;String t=n.getText()==null?"":n.getText().toString();if(prefix?t.startsWith(value):t.equals(value))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo r=find(value,prefix,n.getChild(i));if(r!=null)return r;}return null;}
 private AccessibilityNodeInfo find(String v,boolean prefix){return find(v,prefix,getUiAutomation().getRootInActiveWindow());}
 private void await(String v,boolean prefix){long end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){if(find(v,prefix)!=null)return;SystemClock.sleep(100);}throw new IllegalStateException("Missing "+v);}
 private void click(String v,boolean prefix){await(v,prefix);AccessibilityNodeInfo n=find(v,prefix);while(!n.isClickable()){n=n.getParent();if(n==null)throw new IllegalStateException("Not clickable "+v);}if(!n.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new IllegalStateException("Click failed");waitForIdleSync();}
 private void names(AccessibilityNodeInfo n,LinkedHashSet<String> list){if(n==null)return;if(n.getText()!=null&&!n.getText().toString().equals("频道分类")&&!n.getText().toString().equals("✓"))list.add(n.getText().toString());for(int i=0;i<n.getChildCount();i++)names(n.getChild(i),list);}
 private AccessibilityNodeInfo focused(AccessibilityNodeInfo n){if(n==null)return null;if(n.isFocused())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo v=focused(n.getChild(i));if(v!=null)return v;}return null;}
 private void select(String g){await("频道分类：",true);String current=find("频道分类：",true).getText().toString().substring(5);click("频道分类：",true);await("频道分类",false);long until=SystemClock.uptimeMillis()+3000;while(find(current,false,focused(getUiAutomation().getRootInActiveWindow()))==null&&SystemClock.uptimeMillis()<until)SystemClock.sleep(40);int from=Math.max(0,choices.indexOf(current)),to=Math.max(0,choices.indexOf(g));for(int i=0;i<Math.abs(to-from);i++){sendKeyDownUpSync(to>from?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP);SystemClock.sleep(90);}click(g,false);await("频道分类："+g,false);SystemClock.sleep(250);passed++;Bundle b=new Bundle();b.putString("stream","PASS Release category "+g+"\n");sendStatus(0,b);}
 @Override public void onStart(){try{
  startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  click("频道分类：",true);await("频道分类",false);LinkedHashSet<String> options=new LinkedHashSet<>();SystemClock.sleep(150);for(int i=0;i<24;i++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);SystemClock.sleep(30);}for(int i=0;i<24;i++){names(getUiAutomation().getRootInActiveWindow(),options);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(80);}choices=new ArrayList<>(options);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
  select("全部频道");for(int i=0;i<8;i++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(70);}
  ArrayList<String> groups=new ArrayList<>(options);groups.remove("全部频道");if(groups.size()<2)throw new IllegalStateException("Need real categories");
  select(groups.get(1));select(groups.get(1));for(String g:groups)select(g);select("全部频道");select(groups.get(1));select("全部频道");
  Bundle b=new Bundle();b.putString("stream","Release category regression: "+passed+" selections passed\n");finish(Activity.RESULT_OK,b);
 }catch(Throwable t){Bundle b=new Bundle();b.putString("stream",android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,b);}}
}
