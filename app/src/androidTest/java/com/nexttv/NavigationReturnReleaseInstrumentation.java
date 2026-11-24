package com.nexttv;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;

/** Public UI/Activity APIs only: valid against the R8 Release APK. */
public class NavigationReturnReleaseInstrumentation extends com.nexttv.support.NextTvInstrumentation {
 private Activity activity;private int passed;private final StringBuilder results=new StringBuilder();
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}

 protected void launch()throws Exception{activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));text("频道分类：");SystemClock.sleep(2300);}
 private void closeEditor()throws Exception{key(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(400);if(tree().contains("保存"))key(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(250);}
 protected void pass(String name){passed++;results.append("PASS ").append(name).append('\n');Bundle b=new Bundle();b.putString("stream","PASS "+name+"\n");sendStatus(0,b);}
 @Override public void onStart(){android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("nexttv",0);String original=prefs.getString("selected_group","");try{
  prefs.edit().putString("selected_group","").commit();launch();if(tree().contains("节目表"))throw new IllegalStateException("Unexpected guide menu");focus("收藏");text("我的收藏");focus("设置");text("节目单地址");key(KeyEvent.KEYCODE_DPAD_RIGHT);if(!focus().equals("设置"))throw new IllegalStateException("Nav boundary");pass("three menus and D-pad boundaries");
  click("节目单地址");text("保存并更新");closeEditor();await("parent settings row",()->focus().contains("节目单地址"));key(KeyEvent.KEYCODE_BACK);await("settings top",()->focus().equals("设置"));pass("settings dialog → row → Settings nav");
  click("管理播放源");click("添加订阅");text("确认添加");closeEditor();text("重新拉取");key(KeyEvent.KEYCODE_BACK);await("source parent row",()->focus().contains("管理播放源"));key(KeyEvent.KEYCODE_BACK);await("settings top",()->focus().equals("设置"));pass("source editor → source list → settings row → Settings nav");
  key(KeyEvent.KEYCODE_BACK);text("再按一次退出");if(activity.isFinishing())throw new IllegalStateException("Exited on first back");key(KeyEvent.KEYCODE_DPAD_LEFT);text("我的收藏");if(tree().contains("再按一次退出"))throw new IllegalStateException("Confirmation not cancelled");key(KeyEvent.KEYCODE_ESCAPE);text("再按一次退出");key(KeyEvent.KEYCODE_ESCAPE);await("favorites app exit",()->activity.isFinishing());pass("favorites top double back and interaction cancellation");
  launch();focus("设置");key(KeyEvent.KEYCODE_BACK);text("再按一次退出");SystemClock.sleep(2100);key(KeyEvent.KEYCODE_BACK);text("再按一次退出");if(activity.isFinishing())throw new IllegalStateException("Expired confirmation exits");key(KeyEvent.KEYCODE_BACK);await("settings exit",()->activity.isFinishing());pass("settings top confirmation expires independently");
  launch();focus("CCTV1综合");for(int i=0;i<10;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_BACK);await("channel nav after scroll",()->focus().equals("频道"));if(activity.isFinishing()||tree().contains("再按一次退出"))throw new IllegalStateException("Back skipped scroll level");key(KeyEvent.KEYCODE_BACK);text("再按一次退出");key(KeyEvent.KEYCODE_BACK);await("channel exit",()->activity.isFinishing());pass("scrolled channels → animated top + own nav → confirmed exit");
  launch();click("CCTV1综合");text("线路 ");key(KeyEvent.KEYCODE_MENU);text("画面适配");key(KeyEvent.KEYCODE_ESCAPE);await("all player overlays close",()->!tree().contains("频道列表"));if(tree().contains("再按一次退出播放"))throw new IllegalStateException("Overlay armed exit");key(KeyEvent.KEYCODE_BACK);text("再按一次退出播放");key(KeyEvent.KEYCODE_DPAD_RIGHT);text("线路 ");SystemClock.sleep(800);key(KeyEvent.KEYCODE_BACK);if(!tree().contains("再按一次退出播放")){if(tree().contains("画面适配")||tree().contains("频道列表"))throw new IllegalStateException("Back did not close information overlay");key(KeyEvent.KEYCODE_BACK);}text("再按一次退出播放");key(KeyEvent.KEYCODE_BACK);text("频道分类：");if(activity.isFinishing())throw new IllegalStateException("Live exit also exited app");key(KeyEvent.KEYCODE_BACK);await("home top",()->focus().equals("频道"));key(KeyEvent.KEYCODE_BACK);text("再按一次退出");if(activity.isFinishing())throw new IllegalStateException("Live gate leaked into app gate");key(KeyEvent.KEYCODE_BACK);await("independent app exit",()->activity.isFinishing());pass("player overlay close, double exit and independent application gate");
  launch();focus("频道");long now=SystemClock.uptimeMillis();sendKeySync(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0));SystemClock.sleep(450);sendKeySync(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,1));text("再按一次退出");if(activity.isFinishing())throw new IllegalStateException("Held Back counted twice");sendKeySync(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0));key(KeyEvent.KEYCODE_BACK);await("second distinct back",()->activity.isFinishing());pass("held/repeated Back is one press");
  prefs.edit().putString("selected_group",original).commit();results.append("Release navigation: ").append(passed).append(" groups passed\n");Bundle b=new Bundle();b.putString("stream",results.toString());finish(Activity.RESULT_OK,b);
 }catch(Throwable t){prefs.edit().putString("selected_group",original).commit();Bundle b=new Bundle();b.putString("stream",t.toString());finish(Activity.RESULT_CANCELED,b);}}
}
