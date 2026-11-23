package com.nexttv;
import android.app.*;import android.os.*;import android.view.accessibility.AccessibilityNodeInfo;import org.json.JSONObject;
/** Runs in the separate test package so the measured application's process starts genuinely cold. */
public class ColdReleaseBenchmarkInstrumentation extends Instrumentation{
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 String shell(String command)throws Exception{try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))>=0)out.write(bytes,0,n);return out.toString("UTF-8");}}
 String tree(AccessibilityNodeInfo n){if(n==null||!n.refresh())return "";StringBuilder text=new StringBuilder();if(n.getText()!=null)text.append(n.getText());for(int i=0;i<n.getChildCount();i++)text.append('\n').append(tree(n.getChild(i)));return text.toString();}
 @Override public void onStart(){int result=Activity.RESULT_OK;String report="";try{
 shell("am force-stop com.nexttv");SystemClock.sleep(600);long at=SystemClock.elapsedRealtime();String launch=shell("am start -W -n com.nexttv/com.nexttv.core.tv.TvActivity");if(launch.contains("Error"))throw new IllegalStateException(launch);
 boolean ready=false;while(SystemClock.elapsedRealtime()-at<15000){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();String text=tree(root);if(text.contains("全部频道")&&text.contains("CCTV1综合")){ready=true;break;}SystemClock.sleep(30);}
 if(!ready)throw new IllegalStateException("Real home not visible: "+tree(getUiAutomation().getRootInActiveWindow()));JSONObject data=new JSONObject();data.put("home_visible_ms",SystemClock.elapsedRealtime()-at);java.util.regex.Matcher m=java.util.regex.Pattern.compile("TotalTime: (\\d+)").matcher(launch);if(!m.find())throw new IllegalStateException(launch);data.put("first_window_ms",Integer.parseInt(m.group(1)));data.put("launch",launch);report="PASS separate-process cold start reached real cached home\nCOLD_BENCH_JSON="+data+"\n";
 }catch(Throwable t){result=Activity.RESULT_CANCELED;report=android.util.Log.getStackTraceString(t);}Bundle b=new Bundle();b.putString("stream",report);finish(result,b);}
}
