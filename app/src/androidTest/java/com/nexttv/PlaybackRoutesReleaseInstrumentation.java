package com.nexttv;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.graphics.Rect;
import android.media.AudioManager;
import android.content.Context;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
/** UI-only API checks against the R8 optimized production package. */
public class PlaybackRoutesReleaseInstrumentation extends com.nexttv.support.NextTvInstrumentation {
 private Activity activity;private int passed;private final StringBuilder results=new StringBuilder();
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}

 private void launch()throws Exception{activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));text("频道分类：");SystemClock.sleep(2300);}
 private void closeEditor()throws Exception{key(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(400);if(tree().contains("保存"))key(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(250);}
 private void pass(String name){passed++;results.append("PASS ").append(name).append('\n');Bundle b=new Bundle();b.putString("stream","PASS "+name+"\n");sendStatus(0,b);}

 @Override public void onStart(){android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("nexttv",0);String original=prefs.getString("selected_group","");prefs.edit().putString("selected_group","").commit();try{
  launch();focus("设置");text("节目单地址");
  for(String label:new String[]{"节目单","频道列表"}){AccessibilityNodeInfo n=find(label,false,getUiAutomation().getRootInActiveWindow());if(n==null||n.isFocusable()||n.isClickable())throw new IllegalStateException("Interactive heading "+label);}
  click("节目单地址");text("保存并更新");key(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(300);key(KeyEvent.KEYCODE_BACK);await("settings nav",()->focus().equals("设置"));
  click("节目单地址");text("保存并更新");key(KeyEvent.KEYCODE_ESCAPE);SystemClock.sleep(300);for(int i=0;i<11;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_BACK);await("settings nav after scroll return",()->focus().equals("设置"));SystemClock.sleep(500);key(KeyEvent.KEYCODE_BACK);text("再按一次退出");key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_RIGHT);pass("static settings headers; scrolled Back → page top + Settings nav → exit prompt");
  click("节目单地址");text("XMLTV 节目单地址");text("保存并更新");key(KeyEvent.KEYCODE_ESCAPE);await("EPG address popup close",()->!tree().contains("保存并更新"));
  click("管理播放源");text("添加订阅");text("重新拉取");if(tree().contains("扫码添加")||tree().contains("导入文件"))throw new IllegalStateException("Old subscription actions");click("添加订阅");text("M3U 订阅地址");text("名称,订阅地址");text("确认添加");key(KeyEvent.KEYCODE_ESCAPE);text("重新拉取");key(KeyEvent.KEYCODE_BACK);await("source settings parent",()->focus().contains("管理播放源"));key(KeyEvent.KEYCODE_BACK);await("settings nav after sources",()->focus().equals("设置"));SystemClock.sleep(500);pass("combined QR address dialogs and two-action source toolbar preserve Back hierarchy");
  focus("频道");click("CCTV1综合");text("1/");if(tree().contains("画面适配"))throw new IllegalStateException("Auto load opened actions");
  AudioManager audio=(AudioManager)getTargetContext().getSystemService(Context.AUDIO_SERVICE);int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
  key(KeyEvent.KEYCODE_DPAD_RIGHT);text("线路 2/");if(volume!=audio.getStreamVolume(AudioManager.STREAM_MUSIC))throw new IllegalStateException("Route changed volume");pass("merged real channel routes use left/right without changing volume");
  key(KeyEvent.KEYCODE_ENTER);text("央视");if(tree().contains("日期"))throw new IllegalStateException("Default extra pane");
  key(KeyEvent.KEYCODE_DPAD_LEFT);text("全部频道");text("央视");if(tree().contains("日期"))throw new IllegalStateException("Three panes");
  key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);text("日期");text("央视");if(tree().contains("频道分组\n"))throw new IllegalStateException("Obsolete heading");key(KeyEvent.KEYCODE_ESCAPE);await("EPG all close",()->!tree().contains("日期")&&!tree().contains("央视"));pass("default channel list and two-pane adjacent group/EPG navigation");
  key(KeyEvent.KEYCODE_ENTER);key(KeyEvent.KEYCODE_DPAD_LEFT);text("全部频道");key(KeyEvent.KEYCODE_BACK);await("group all close",()->!tree().contains("全部频道"));
  key(KeyEvent.KEYCODE_MENU);text("画面适配");key(KeyEvent.KEYCODE_BACK);await("menu close",()->!tree().contains("画面适配"));pass("Back/Esc closes every browser/menu layer to fullscreen playback");
  key(KeyEvent.KEYCODE_BACK);text("再按一次退出播放");Rect live=new Rect();find("再按一次退出播放",false,getUiAutomation().getRootInActiveWindow()).getBoundsInScreen(live);
  key(KeyEvent.KEYCODE_BACK);text("频道分类：");key(KeyEvent.KEYCODE_BACK);await("channel nav",()->focus().equals("频道"));key(KeyEvent.KEYCODE_BACK);text("再按一次退出");Rect home=new Rect();find("再按一次退出",false,getUiAutomation().getRootInActiveWindow()).getBoundsInScreen(home);
  if(live.centerX()!=home.centerX()||live.centerY()!=home.centerY()||live.bottom!=home.bottom)throw new IllegalStateException("Exit position mismatch "+live+" "+home);
  if(activity.isFinishing())throw new IllegalStateException("Exit gate leaked");key(KeyEvent.KEYCODE_BACK);await("app exit",()->activity.isFinishing());pass("aligned live/app exit notices and independent double-back gates");
  Bundle b=new Bundle();b.putString("stream",results+"Release routes: "+passed+" groups passed\n");finish(Activity.RESULT_OK,b);
 }catch(Throwable t){Bundle b=new Bundle();b.putString("stream",t.toString());finish(Activity.RESULT_CANCELED,b);}finally{prefs.edit().putString("selected_group",original).commit();}}
}
