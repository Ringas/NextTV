package com.nexttv;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
/** Black-box signed Release probe: identical real built-in guide and force-refresh action. */
public class EpgReleaseBenchmarkInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 String tree(AccessibilityNodeInfo n){if(n==null)return "";StringBuilder s=new StringBuilder();if(n.getText()!=null)s.append(n.getText());for(int i=0;i<n.getChildCount();i++)s.append('\n').append(tree(n.getChild(i)));return s.toString();}
 AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text){if(n==null)return null;if(text.contentEquals(n.getText()==null?"":n.getText()))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo v=find(n.getChild(i),text);if(v!=null)return v;}return null;}
 void click(String text){AccessibilityNodeInfo n=null;long deadline=SystemClock.uptimeMillis()+10000;while(n==null&&SystemClock.uptimeMillis()<deadline){n=find(getUiAutomation().getRootInActiveWindow(),text);if(n==null)SystemClock.sleep(50);}if(n==null)throw new IllegalStateException("Missing "+text+" UI="+tree(getUiAutomation().getRootInActiveWindow()));while(!n.isClickable()){n=n.getParent();if(n==null)throw new IllegalStateException("Not clickable "+text);}if(!n.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new IllegalStateException("Click failed");SystemClock.sleep(120);}
 @Override public void onStart(){int result=Activity.RESULT_OK;String report="";Activity activity=null;android.content.SharedPreferences sp=getTargetContext().getSharedPreferences("nexttv",0);java.util.Map<String,?> saved=sp.getAll();String[] keys={"auto_update","auto_update_epg","previews","start_live","manual_epg_url"};
 try{
 sp.edit().putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("previews",false).putBoolean("start_live",false).putString("manual_epg_url","").commit();
 activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(8000);click("设置");SystemClock.sleep(600);
 long at=SystemClock.elapsedRealtime(),cpu=android.os.Process.getElapsedCpuTime(),rx=android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid());click("立即更新节目单");boolean done=false;
 while(SystemClock.elapsedRealtime()-at<60000){String t=tree(getUiAutomation().getRootInActiveWindow());if(t.contains("节目单已更新")){done=true;break;}if(t.contains("更新失败"))throw new IllegalStateException(t);SystemClock.sleep(100);}
 if(!done)throw new IllegalStateException("Guide refresh timed out");JSONObject data=new JSONObject();data.put("version",getTargetContext().getPackageManager().getPackageInfo("com.nexttv",0).versionName);data.put("elapsed_ms",SystemClock.elapsedRealtime()-at);data.put("cpu_ms",android.os.Process.getElapsedCpuTime()-cpu);data.put("rx_bytes",android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())-rx);Debug.MemoryInfo m=new Debug.MemoryInfo();Debug.getMemoryInfo(m);data.put("pss_kb",m.getTotalPss());report="PASS forced real built-in EPG refresh\nEPG_BENCH_JSON="+data+"\n";
 }catch(Throwable t){result=Activity.RESULT_CANCELED;report=android.util.Log.getStackTraceString(t);}finally{if(activity!=null){final Activity a=activity;runOnMainSync(a::finish);}android.content.SharedPreferences.Editor e=sp.edit();for(String key:keys){Object v=saved.get(key);if(v instanceof Boolean)e.putBoolean(key,(Boolean)v);else if(v instanceof String)e.putString(key,(String)v);else e.remove(key);}e.commit();}
 Bundle output=new Bundle();output.putString("stream",report);finish(result,output);
 }
}
