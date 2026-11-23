// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import org.json.*;import java.util.concurrent.*;
/** Separate instrumented target-focus latency from unrelated fading/shadow redraws. */
public class FocusResponseInstrumentation extends InteractionRefinementInstrumentation {
 static class Trial {int target;long input,focus,draw,observerNanos;boolean synchronous;CountDownLatch done=new CountDownLatch(1);}
 Trial trial;
 java.lang.reflect.Field gridObserver;java.lang.reflect.Method gridInvoke;String observerField,observerMethod;
 @Override public void onCreate(Bundle b){observerField=b==null?null:b.getString("gridField");observerMethod=b==null?null:b.getString("gridMethod");super.onCreate(b);}
 void prepareObserver(){
  try{
   if(observerField==null||observerMethod==null)throw new IllegalStateException("APK-derived read-only observer mapping required");
   gridObserver=activity.getClass().getDeclaredField(observerField);gridObserver.setAccessible(true);Object callback=gridObserver.get(activity);
   gridInvoke=callback.getClass().getMethod(observerMethod);gridInvoke.setAccessible(true);
   if(!(((JSONObject)gridInvoke.invoke(callback)).has("selectedIndex")))throw new IllegalStateException("Mapped callback is not grid diagnostics");
  }catch(Exception e){throw new RuntimeException("Cannot bind verified APK grid callback",e);}
 }
 int actual(){try{Object callback=gridObserver.get(activity);return ((JSONObject)gridInvoke.invoke(callback)).getInt("selectedIndex");}catch(Exception e){throw new RuntimeException(e);}}
 void observe(){observeAt(System.nanoTime());}
 void observeAt(long sampled){Trial t=trial;if(t!=null&&t.focus==0){long readAt=System.nanoTime();int index=actual();t.observerNanos+=System.nanoTime()-readAt;if(index==t.target)t.focus=sampled;}}
 JSONObject measure(int key,int expected)throws Exception{
  Trial t=new Trial();t.target=expected;
  ViewTreeObserver.OnDrawListener draw=()->{Trial current=trial;if(current==t){long drawAt=System.nanoTime();observeAt(drawAt);if(t.focus!=0&&t.draw==0){t.draw=drawAt;t.done.countDown();}}};
  runOnMainSync(()->{
   trial=t;activity.getWindow().getDecorView().getViewTreeObserver().addOnDrawListener(draw);t.input=System.nanoTime();long at=SystemClock.uptimeMillis();
   activity.getWindow().getCallback().dispatchKeyEvent(new KeyEvent(at,at,KeyEvent.ACTION_DOWN,key,0));activity.getWindow().getCallback().dispatchKeyEvent(new KeyEvent(at,at+1,KeyEvent.ACTION_UP,key,0));observe();t.synchronous=t.focus!=0;
   Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback(){public void doFrame(long ignored){if(trial!=t)return;observe();if(t.focus==0&&System.nanoTime()-t.input<3000000000L)Choreographer.getInstance().postFrameCallback(this);}});
  });
  boolean complete=t.done.await(4,TimeUnit.SECONDS);runOnMainSync(()->{trial=null;activity.getWindow().getDecorView().getViewTreeObserver().removeOnDrawListener(draw);});
  if(!complete)throw new IllegalStateException("Target not drawn key="+key+" target="+expected+" actual="+grid());
  SystemClock.sleep(80);return new JSONObject().put("key",key).put("target",expected).put("focus_ms",(t.focus-t.input)/1e6).put("focus_draw_ms",(t.draw-t.input)/1e6).put("synchronous",t.synchronous).put("observer_ms",t.observerNanos/1e6);
 }
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("grid_mode",true).putBoolean("reduce_motion",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("channel_previews",false).putBoolean("light_glass",false).putString("selected_group","").commit();
  activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home",()->focus().equals("频道"));key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);await("first card",()->grid().optInt("selectedIndex")==0);Throwable[] setupFailure=new Throwable[1];runOnMainSync(()->{try{prepareObserver();}catch(Throwable e){setupFailure[0]=e;}});if(setupFailure[0]!=null)throw new IllegalStateException("Observer initialization failed",setupFailure[0]);SystemClock.sleep(5000);
  JSONArray samples=new JSONArray();for(int repeat=0;repeat<8;repeat++)for(int index:new int[]{1,2,1,0})samples.put(measure(index==1&&grid().optInt("selectedIndex")==0||index==2?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT,index).put("phase","horizontal"));
  int columns=activity.getResources().getConfiguration().screenWidthDp<900?3:4;
  for(int cycle=0;cycle<4;cycle++){for(int i=1;i<=8;i++)samples.put(measure(KeyEvent.KEYCODE_DPAD_DOWN,i*columns).put("phase","vertical"));
  for(int i=7;i>=0;i--)samples.put(measure(KeyEvent.KEYCODE_DPAD_UP,i*columns).put("phase","vertical"));}
  String version=getTargetContext().getPackageManager().getPackageInfo("com.nexttv",0).versionName;
  result="FOCUS_RESPONSE_JSON="+new JSONObject().put("version",version).put("samples",samples);pass("96 target-state/target-draw observations (timestamps exclude diagnostic read work), including virtualized rows, completed");
 }catch(Throwable t){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(t);}finally{restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
