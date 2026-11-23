package com.nexttv;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** UI-only, Java-only runner: independent of the target Release's R8-renamed Kotlin runtime. */
@android.annotation.TargetApi(30)
public class WheelReturnLatencyInstrumentation extends Instrumentation {
    private Activity activity;
    private volatile long latestInputMillis;
    private final ConcurrentLinkedQueue<JSONObject> focusEvents=new ConcurrentLinkedQueue<>();
    private boolean recordScroll, alignedReturn;
    private final JSONArray returnStarts=new JSONArray(),returnEnds=new JSONArray();
    private String dump(String...args){java.io.StringWriter out=new java.io.StringWriter();runOnMainSync(()->activity.dump("",null,new java.io.PrintWriter(out),args));return out.toString();}
    private JSONObject grid(){try{String value=dump();for(String line:value.split("\n"))if(line.startsWith("NextTVGrid "))return new JSONObject(line.substring(11));throw new IllegalStateException(value);}catch(JSONException e){throw new RuntimeException(e);}}
    private AccessibilityNodeInfo findText(String text,AccessibilityNodeInfo node){if(node==null)return null;if(text.contentEquals(node.getText()==null?"":node.getText()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findText(text,node.getChild(i));if(found!=null)return found;}return null;}
    private AccessibilityNodeInfo visibleCard(AccessibilityNodeInfo n){
        if(n==null)return null;
        android.graphics.Rect bounds=new android.graphics.Rect();n.getBoundsInScreen(bounds);
        if(n.isFocusable()&&n.isClickable()&&bounds.height()>=80&&bounds.height()<=320&&bounds.width()>=100&&bounds.width()<=600)return n;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo item=visibleCard(n.getChild(i));if(item!=null)return item;}
        return null;
    }
    private void firstCard(){
        // Eight Down/Up inputs do not prove that a virtualized row is visible.
        // Normalize only warmup/preparation; measured return-top checks remain unchanged.
        try{JSONObject at=grid();if(at.getInt("first")!=0||at.getInt("offset")!=0||at.getBoolean("returning")){
            if(at.getBoolean("nav")){
                AccessibilityNodeInfo visible=visibleCard(getUiAutomation().getRootInActiveWindow());
                if(visible==null||!visible.performAction(AccessibilityNodeInfo.ACTION_FOCUS))throw new IllegalStateException("Cannot focus visible warmup card: "+at);
                SystemClock.sleep(200);
            }
            key(KeyEvent.KEYCODE_ESCAPE,1000);long deadline=SystemClock.uptimeMillis()+4000;
            while(SystemClock.uptimeMillis()<deadline){at=grid();if(at.getInt("first")==0&&at.getInt("offset")==0&&!at.getBoolean("returning"))break;SystemClock.sleep(50);}
            if(at.getInt("first")!=0||at.getInt("offset")!=0||at.getBoolean("returning"))throw new IllegalStateException("Warmup did not restore top: "+at);
        }}catch(JSONException e){throw new RuntimeException(e);}
        AccessibilityNodeInfo node=null;long deadline=SystemClock.uptimeMillis()+3000;
        while(node==null&&SystemClock.uptimeMillis()<deadline){node=findText("CCTV1综合",getUiAutomation().getRootInActiveWindow());if(node==null)SystemClock.sleep(60);}
        if(node==null)throw new IllegalStateException("First card absent");while(!node.isFocusable()){node=node.getParent();if(node==null)throw new IllegalStateException("First card not focusable");}if(!node.performAction(AccessibilityNodeInfo.ACTION_FOCUS))throw new IllegalStateException("Cannot focus first card");SystemClock.sleep(100);
        // Public Tab clears any pending directional cursor before the accessibility
        // fixture places focus. Otherwise the next Down can start from a stale
        // logical row in the old APK, making the two return starts incomparable.
        key(KeyEvent.KEYCODE_TAB,80);
        node=findText("CCTV1综合",getUiAutomation().getRootInActiveWindow());
        while(node!=null&&!node.isFocusable())node=node.getParent();
        if(node==null||!node.performAction(AccessibilityNodeInfo.ACTION_FOCUS))throw new IllegalStateException("Cannot restore first card after Tab");SystemClock.sleep(200);
        JSONObject normalized=grid();if(normalized.optInt("selectedIndex")!=0||normalized.optInt("navigationIndex",-1)!=-1)throw new IllegalStateException("Warmup cursor not normalized "+normalized);
    }

    private AccessibilityNodeInfo scrollGrid(AccessibilityNodeInfo node){
        if(node==null)return null;android.graphics.Rect rect=new android.graphics.Rect();node.getBoundsInScreen(rect);if(node.isScrollable()&&rect.width()>400&&rect.height()>200)return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=scrollGrid(node.getChild(i));if(found!=null)return found;}return null;
    }
    private void wheelPixels(float distance,android.graphics.Rect viewport){
        MotionEvent.PointerProperties props=new MotionEvent.PointerProperties();props.id=0;props.toolType=MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=viewport.exactCenterX();coords.y=viewport.exactCenterY();coords.setAxisValue(MotionEvent.AXIS_VSCROLL,-distance/(48f*activity.getResources().getDisplayMetrics().density));long now=SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(now,now,MotionEvent.ACTION_SCROLL,1,new MotionEvent.PointerProperties[]{props},new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,InputDevice.SOURCE_MOUSE,0);
        if(!getUiAutomation().injectInputEvent(event,true))throw new IllegalStateException("Fixture wheel rejected");event.recycle();SystemClock.sleep(450);
        long end=SystemClock.uptimeMillis()+4000;String previous="";int stable=0;
        while(SystemClock.uptimeMillis()<end){JSONObject at=grid();String position=at.optInt("first")+":"+at.optInt("offset");stable=position.equals(previous)?stable+1:0;previous=position;if(stable>=4)return;SystemClock.sleep(90);}
        throw new IllegalStateException("Fixture wheel did not settle "+grid());
    }
    private JSONObject prepareReturnStart()throws Exception{
        firstCard();AccessibilityNodeInfo header=findText("分类",getUiAutomation().getRootInActiveWindow());while(header!=null&&!header.isFocusable())header=header.getParent();
        if(header==null||!header.performAction(AccessibilityNodeInfo.ACTION_FOCUS))throw new IllegalStateException("Category fixture unavailable");SystemClock.sleep(100);
        AccessibilityNodeInfo collection=scrollGrid(getUiAutomation().getRootInActiveWindow());if(collection==null)throw new IllegalStateException("Grid viewport unavailable");android.graphics.Rect viewport=new android.graphics.Rect();collection.getBoundsInScreen(viewport);
        // A fixed integral wheel distance is reproducible in both APKs. Do not
        // chase an unreachable subpixel row origin or a stale directional cursor.
        wheelPixels(1920f*activity.getResources().getDisplayMetrics().density,viewport);
        JSONObject positioned=grid();int first=positioned.getInt("first"),offset=positioned.getInt("offset");int columns=activity.getResources().getConfiguration().screenWidthDp<900?3:4;int target=first+columns;
        AccessibilityNodeInfo card=findText(String.format(java.util.Locale.ROOT,"%03d",target+1),getUiAutomation().getRootInActiveWindow());while(card!=null&&!card.isFocusable())card=card.getParent();
        if(card==null||!card.performAction(AccessibilityNodeInfo.ACTION_FOCUS))throw new IllegalStateException("Target fixture card unavailable");key(KeyEvent.KEYCODE_SHIFT_LEFT,400);
        JSONObject start=grid();start.put("fixture_target",target);start.put("fixture_wheel_notches",40);
        if(start.getInt("first")!=first||start.getInt("offset")!=offset||start.getInt("selectedIndex")!=target||start.getBoolean("nav")||start.getInt("itemTop")<start.getInt("viewportStart")||start.getInt("itemBottom")>start.getInt("viewportEnd"))throw new IllegalStateException("Fixture focus changed scroll geometry "+start);return start;
    }

    private java.util.Map<String,?> saved;
    private final String[] profileKeys={"auto_update","auto_update_epg","previews","channel_previews","light_glass","preload_next","reduce_motion","grid_mode","selected_group","start_live"};
    private void restoreProfile(){if(saved==null)return;android.content.SharedPreferences.Editor edit=getTargetContext().getSharedPreferences("nexttv",0).edit();for(String key:profileKeys){Object v=saved.get(key);if(v instanceof Boolean)edit.putBoolean(key,(Boolean)v);else if(v instanceof String)edit.putString(key,(String)v);else edit.remove(key);}edit.commit();saved=null;}

    private final ConcurrentLinkedQueue<JSONObject> frames=new ConcurrentLinkedQueue<>(), inputs=new ConcurrentLinkedQueue<>(), dispatches=new ConcurrentLinkedQueue<>();
    private final ArrayList<Long> pending=new ArrayList<>();
    private final ConcurrentLinkedQueue<JSONObject> draws=new ConcurrentLinkedQueue<>();
    private boolean recordDraws, idlePreview;
    private volatile int segment=0;
    private volatile long latestInputNanos;
    private volatile String phase="warmup";
    private String configureStartup;
    private long warmupMs=1800;
    private static JSONObject json(Object...pairs) {
        JSONObject result=new JSONObject();
        try {for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);}
        catch(JSONException e){throw new IllegalStateException(e);}
        return result;
    }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);if(arguments!=null)warmupMs=Long.parseLong(arguments.getString("warmupMs","1800"));alignedReturn=arguments!=null&&"true".equals(arguments.getString("alignedReturn"));recordScroll=arguments!=null&&"true".equals(arguments.getString("recordScroll"));configureStartup=arguments==null?null:arguments.getString("configureStartup");recordDraws=arguments!=null&&"true".equals(arguments.getString("recordDraws"));idlePreview=arguments!=null&&"true".equals(arguments.getString("idlePreview"));start();}
    private void key(int code,long settle) {
        Runnable dispatch=()->{
            long t=SystemClock.uptimeMillis();latestInputMillis=t;latestInputNanos=System.nanoTime();pending.add(SystemClock.elapsedRealtimeNanos());
            long dispatchAt=System.nanoTime(),dispatchCpu=android.os.Debug.threadCpuTimeNanos();
            activity.getWindow().getCallback().dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0));
            activity.getWindow().getCallback().dispatchKeyEvent(new KeyEvent(t,t+10,KeyEvent.ACTION_UP,code,0));
            if(!phase.equals("warmup"))dispatches.add(json("phase",phase,"key",code,"handler_ms",(System.nanoTime()-dispatchAt)/1e6,"handler_cpu_ms",(android.os.Debug.threadCpuTimeNanos()-dispatchCpu)/1e6));
        };
        if(alignedReturn&&phase.equals("return_top")){
            java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
            runOnMainSync(()->Choreographer.getInstance().postFrameCallback(time->{try{dispatch.run();}finally{done.countDown();}}));
            try{if(!done.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Return vsync timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
        }else runOnMainSync(dispatch);
        SystemClock.sleep(settle);
    }
    private String tree(AccessibilityNodeInfo n) {
        if(n==null)return "";
        StringBuilder b=new StringBuilder();if(n.getText()!=null)b.append(n.getText());
        for(int i=0;i<n.getChildCount();i++)b.append(tree(n.getChild(i)));return b.toString();
    }
    @Override public void onStart() {
        int status=Activity.RESULT_OK;String report="";
        try {
            android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("nexttv",0);saved=prefs.getAll();prefs.edit().putBoolean("start_live",false).putString("selected_group","").putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("previews",false).putBoolean("channel_previews",false).putBoolean("light_glass",false).putBoolean("reduce_motion",false).putBoolean("grid_mode",true).commit();
            activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline=SystemClock.uptimeMillis()+10000;while(!tree(getUiAutomation().getRootInActiveWindow()).contains("全部频道")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);
            if(!tree(getUiAutomation().getRootInActiveWindow()).contains("全部频道"))throw new IllegalStateException("Real home absent");SystemClock.sleep(8000);
            JSONArray durations=new JSONArray(),starts=new JSONArray();
            for(int trial=0;trial<5;trial++){
                JSONObject start=prepareReturnStart();starts.put(start);
                final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);final long[] begin={0},finished={0};
                ViewTreeObserver.OnDrawListener listener=()->{if(begin[0]==0||finished[0]!=0)return;java.io.StringWriter out=new java.io.StringWriter();activity.dump("",null,new java.io.PrintWriter(out),new String[0]);try{for(String line:out.toString().split("\n"))if(line.startsWith("NextTVGrid ")){JSONObject state=new JSONObject(line.substring(11));if(state.getInt("first")==0&&state.getInt("offset")==0&&state.getInt("selectedIndex")==-1&&state.getBoolean("nav")&&!state.getBoolean("returning")){finished[0]=System.nanoTime();done.countDown();}}}catch(Exception ignored){}};
                runOnMainSync(()->{activity.getWindow().getDecorView().getViewTreeObserver().addOnDrawListener(listener);begin[0]=System.nanoTime();long t=SystemClock.uptimeMillis();activity.getWindow().getCallback().dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE,0));activity.getWindow().getCallback().dispatchKeyEvent(new KeyEvent(t,t+10,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ESCAPE,0));});
                boolean completed=done.await(1000,java.util.concurrent.TimeUnit.MILLISECONDS);runOnMainSync(()->activity.getWindow().getDecorView().getViewTreeObserver().removeOnDrawListener(listener));if(!completed)throw new IllegalStateException("Return not drawn complete in 1 second");durations.put((finished[0]-begin[0])/1e6);SystemClock.sleep(200);
            }
            report="WHEEL_RETURN_LATENCY_JSON="+json("durations_ms",durations,"starts",starts,"definition","40-notch wheel fixture, key dispatch to first UI draw with top offset zero, nav focused, return animation complete; excludes display presentation")+"\nPASS five real return-to-top operations completed within 1 second\n";
        }catch(Throwable t){status=Activity.RESULT_CANCELED;report=android.util.Log.getStackTraceString(t);}
        finally{runOnMainSync(()->{if(activity!=null)activity.finish();});restoreProfile();}
        Bundle b=new Bundle();b.putString("stream",report);finish(status,b);
    }
}
