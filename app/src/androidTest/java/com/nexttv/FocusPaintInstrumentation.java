// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.view.accessibility.AccessibilityNodeInfo;import java.util.*;import java.util.concurrent.*;
/** Samples the actual window buffer shortly after input, independently of Compose state. */
public class FocusPaintInstrumentation extends ViewingDesignInstrumentation {
 Bitmap capture()throws Exception{
  Bitmap bitmap=Bitmap.createBitmap(activity.getWindow().getDecorView().getWidth(),activity.getWindow().getDecorView().getHeight(),Bitmap.Config.ARGB_8888);
  CountDownLatch done=new CountDownLatch(1);int[] status={-1};
  runOnMainSync(()->PixelCopy.request(activity.getWindow(),bitmap,result->{status[0]=result;done.countDown();},new Handler(Looper.getMainLooper())));
  if(!done.await(5,TimeUnit.SECONDS)||status[0]!=PixelCopy.SUCCESS)throw new IllegalStateException("PixelCopy "+status[0]);return bitmap;
 }
 void twoFrames()throws Exception{CountDownLatch done=new CountDownLatch(1);runOnMainSync(()->Choreographer.getInstance().postFrameCallback(t->Choreographer.getInstance().postFrameCallback(u->done.countDown())));if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Frame timeout");}
 double difference(Bitmap a,Bitmap b,Rect bounds){double difference=0;int n=0;for(int y=bounds.centerY()-6;y<=bounds.centerY()+6;y++)for(int x=bounds.left+2;x<=bounds.left+4;x++){int ca=a.getPixel(x,y),cb=b.getPixel(x,y);difference+=Math.abs(Color.red(ca)-Color.red(cb))+Math.abs(Color.green(ca)-Color.green(cb))+Math.abs(Color.blue(ca)-Color.blue(cb));n+=3;}return difference/n;}
 @Override public void onStart(){int code=Activity.RESULT_OK;String result="";try{
  prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putBoolean("grid_mode",true).putBoolean("reduce_motion",false).putBoolean("auto_update",false).putBoolean("auto_update_epg",false).commit();
  activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("home",()->focus().equals("频道"));key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);
  List<String> samples=new ArrayList<>();
  for(int i=0;i<8;i++){
   SystemClock.sleep(250);AccessibilityNodeInfo focused=FreshAccessibilityNodes.focused(root());Rect old=new Rect();focused.getBoundsInScreen(old);Bitmap before=capture();
   int key=i%4<2?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT;long input=SystemClock.uptimeMillis();
   runOnMainSync(()->{activity.getWindow().getCallback().dispatchKeyEvent(new KeyEvent(input,input,KeyEvent.ACTION_DOWN,key,0));activity.getWindow().getCallback().dispatchKeyEvent(new KeyEvent(input,input+1,KeyEvent.ACTION_UP,key,0));});
   twoFrames();Bitmap early=capture();long elapsed=SystemClock.uptimeMillis()-input;SystemClock.sleep(250);Bitmap settled=capture();double ghost=difference(early,settled,old),contrast=difference(before,settled,old);
   samples.add("sample="+i+" capture_ms="+elapsed+" residual_rgb="+ghost+" focus_contrast_rgb="+contrast);
   before.recycle();early.recycle();settled.recycle();
   if(contrast<5)throw new IllegalStateException("No measurable departing focus contrast "+samples);
   if(ghost>2)throw new IllegalStateException("Departed focus still painted "+samples);
  }
  pass("actual window pixels show no departing highlight after two frame boundaries: "+samples);shot("focus-paint");result="Focus paint: 8 pixel samples passed";
 }catch(Throwable t){code=Activity.RESULT_CANCELED;result=android.util.Log.getStackTraceString(t);}finally{restore();}Bundle b=new Bundle();b.putString("stream",result);finish(code,b);
 }
}
