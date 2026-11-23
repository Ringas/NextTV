package com.nexttv;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Bitmap;
import java.io.*;

/** UI-only validation: no references to R8-renamed application classes. */
public class ReleasePortSmokeInstrumentation extends Instrumentation {
 private final StringBuilder results=new StringBuilder();
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private String tree(AccessibilityNodeInfo n){if(n==null)return "";StringBuilder b=new StringBuilder();b.append(n.getText()==null?"":n.getText()).append('\n');for(int i=0;i<n.getChildCount();i++)b.append(tree(n.getChild(i)));return b.toString();}
 private String tree(){return tree(getUiAutomation().getRootInActiveWindow());}
 private void await(String label,String text,long timeout){long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(tree().contains(text))return;SystemClock.sleep(150);}throw new IllegalStateException(label+": "+tree());}
 private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();SystemClock.sleep(220);}
 private AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text){if(n==null)return null;if(text.contentEquals(n.getText()==null?"":n.getText()))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo v=find(n.getChild(i),text);if(v!=null)return v;}return null;}
 private void click(String text){AccessibilityNodeInfo n=find(getUiAutomation().getRootInActiveWindow(),text);if(n==null)throw new IllegalStateException("Missing "+text);while(!n.isClickable()){n=n.getParent();if(n==null)throw new IllegalStateException("Not clickable "+text);}if(!n.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new IllegalStateException("Click failed "+text);SystemClock.sleep(300);}
 private File file(String name){return new File(getTargetContext().getExternalFilesDir(null),name);}
 private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream f=new FileOutputStream(file("tvport-release-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
 private void pass(String label){results.append("PASS ").append(label).append('\n');Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 @Override public void onStart(){try{
  startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(2500);
  await("release home","全部频道",10000);await("four menus","节目表",3000);shot("home");pass("R8 Release starts native TV entry");
  key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_RIGHT);await("favorites","我的收藏",5000);key(KeyEvent.KEYCODE_DPAD_RIGHT);await("guide","刷新节目单",5000);key(KeyEvent.KEYCODE_DPAD_RIGHT);await("settings","节目单地址",5000);SystemClock.sleep(2500);shot("settings");pass("Release four-menu focus navigation and settings resources");
  click("频道");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_ENTER);SystemClock.sleep(400);key(KeyEvent.KEYCODE_1);SystemClock.sleep(2000);
  key(KeyEvent.KEYCODE_MENU);await("more menu","更多",5000);click("播放信息");long readyEnd=SystemClock.uptimeMillis()+30000;while(!(tree().contains("视频：video/")&&tree().contains("状态：3"))&&SystemClock.uptimeMillis()<readyEnd){key(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(800);key(KeyEvent.KEYCODE_MENU);click("播放信息");}await("video format","视频：video/",1000);await("ready state","状态：3",1000);shot("player-info");results.append(tree()).append('\n');pass("Release real-source video format and READY state");
  key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_MENU);click("频道列表");await("sidebar","全部",5000);shot("sidebar");key(KeyEvent.KEYCODE_ESCAPE);key(KeyEvent.KEYCODE_BACK);await("return home","全部频道",5000);pass("Release sidebar, Escape and return home");
  try(FileOutputStream f=new FileOutputStream(file("tvport-release-results.txt"))){f.write(results.toString().getBytes("UTF-8"));}
  Bundle out=new Bundle();out.putString("stream",results.toString());finish(Activity.RESULT_OK,out);
 }catch(Throwable t){try{shot("failure");}catch(Exception ignored){}Bundle out=new Bundle();out.putString("stream",t.toString());finish(Activity.RESULT_CANCELED,out);}}
}
