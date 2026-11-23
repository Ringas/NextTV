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
public class TvFrameInstrumentation extends Instrumentation {
    private Activity activity;
    private volatile long latestInputMillis;
    private final ConcurrentLinkedQueue<JSONObject> focusEvents=new ConcurrentLinkedQueue<>();
    private boolean recordScroll, alignedReturn, scrollOnly;
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
    private android.graphics.Rect screenBounds(AccessibilityNodeInfo node){
        android.graphics.Rect bounds=new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        return bounds;
    }

    private void collectVisibleCards(AccessibilityNodeInfo node,android.graphics.Rect viewport,
                                     List<AccessibilityNodeInfo> cards){
        if(node==null)return;
        android.graphics.Rect bounds=screenBounds(node);
        if(node.isFocusable()&&node.isClickable()&&bounds.top>viewport.top+2
            &&bounds.bottom<viewport.bottom&&bounds.width()>=100&&bounds.height()>=80){
            cards.add(node);
            return;
        }
        for(int i=0;i<node.getChildCount();i++)collectVisibleCards(node.getChild(i),viewport,cards);
    }

    private JSONObject prepareReturnStart()throws Exception{
        firstCard();AccessibilityNodeInfo header=findText("分类",getUiAutomation().getRootInActiveWindow());while(header!=null&&!header.isFocusable())header=header.getParent();
        if(header==null||!header.performAction(AccessibilityNodeInfo.ACTION_FOCUS))throw new IllegalStateException("Category fixture unavailable");SystemClock.sleep(100);
        AccessibilityNodeInfo collection=scrollGrid(getUiAutomation().getRootInActiveWindow());if(collection==null)throw new IllegalStateException("Grid viewport unavailable");android.graphics.Rect viewport=new android.graphics.Rect();collection.getBoundsInScreen(viewport);
        // A fixed integral wheel distance is reproducible in both APKs. Do not
        // chase an unreachable subpixel row origin or a stale directional cursor.
        wheelPixels(1920f*activity.getResources().getDisplayMetrics().density,viewport);
        JSONObject positioned=grid();
        int first=positioned.getInt("first"), offset=positioned.getInt("offset");
        android.content.res.Configuration config=activity.getResources().getConfiguration();
        int width=config.screenWidthDp;
        int columns=config.screenHeightDp<480&&width>=600&&width<900?3:width<720?2:width<900?3:4;
        int target=first+columns;
        // Channel numbers persist across subscription changes and can have gaps.
        // Use viewport geometry and confirm the actual index after focusing.
        long deadline=SystemClock.uptimeMillis()+3000;
        boolean focused=false;
        while(!focused&&SystemClock.uptimeMillis()<deadline){
            ArrayList<AccessibilityNodeInfo> candidates=new ArrayList<>();
            collectVisibleCards(scrollGrid(getUiAutomation().getRootInActiveWindow()),viewport,candidates);
            int firstTop=Integer.MAX_VALUE;
            for(AccessibilityNodeInfo card:candidates)firstTop=Math.min(firstTop,screenBounds(card).top);
            AccessibilityNodeInfo targetCard=null;
            android.graphics.Rect targetBounds=null;
            for(AccessibilityNodeInfo card:candidates){
                android.graphics.Rect bounds=screenBounds(card);
                if(offset==0&&bounds.top<=firstTop+2)continue;
                if(targetBounds==null||bounds.top<targetBounds.top
                    ||(bounds.top==targetBounds.top&&bounds.left<targetBounds.left)){
                    targetCard=card;
                    targetBounds=bounds;
                }
            }
            focused=targetCard!=null&&targetCard.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if(!focused)SystemClock.sleep(60);
        }
        if(!focused)throw new IllegalStateException("Target fixture row unavailable: "+positioned);
        key(KeyEvent.KEYCODE_SHIFT_LEFT,400);
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
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);if(arguments!=null)warmupMs=Long.parseLong(arguments.getString("warmupMs","1800"));alignedReturn=arguments!=null&&"true".equals(arguments.getString("alignedReturn"));recordScroll=arguments!=null&&"true".equals(arguments.getString("recordScroll"));configureStartup=arguments==null?null:arguments.getString("configureStartup");recordDraws=arguments!=null&&"true".equals(arguments.getString("recordDraws"));idlePreview=arguments!=null&&"true".equals(arguments.getString("idlePreview"));scrollOnly=arguments!=null&&"true".equals(arguments.getString("scrollOnly"));start();}
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
        HandlerThread worker=new HandlerThread("frame-metrics");worker.start();getUiAutomation().setOnAccessibilityEventListener(event->{if(event.getEventType()==android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)focusEvents.add(json("at_ms",event.getEventTime(),"text",event.getText().toString(),"phase",phase));});
        try {
            if(configureStartup!=null) {
                android.content.SharedPreferences.Editor edit=getTargetContext().getSharedPreferences("tv_experience",0).edit().putString("startup",configureStartup);
                String playlist=getTargetContext().getSharedPreferences("iptv_prefs",0).getString("playlist_text", "");
                for(String line:playlist.split("\\r?\\n")){String url=line.trim();if(url.startsWith("http://")||url.startsWith("https://")){edit.putString("last_url",url);break;}}
                edit.commit();
                Bundle b=new Bundle();b.putString("stream","Configured startup: "+configureStartup+"\n");finish(Activity.RESULT_OK,b);return;
            }
            android.content.SharedPreferences preferences=getTargetContext().getSharedPreferences("nexttv",0);saved=preferences.getAll();preferences.edit().putBoolean("start_live",false).putString("selected_group","").putBoolean("auto_update",false).putBoolean("auto_update_epg",false).putBoolean("previews",false).putBoolean("channel_previews",false).putBoolean("light_glass",false).putBoolean("preload_next",false).putBoolean("reduce_motion",false).putBoolean("grid_mode",true).commit();
            activity=startActivitySync(new Intent().setClassName("com.nexttv","com.nexttv.core.tv.TvActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline=SystemClock.uptimeMillis()+10000;
            while(!tree(getUiAutomation().getRootInActiveWindow()).contains("全部频道")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(200);
            if(!tree(getUiAutomation().getRootInActiveWindow()).contains("全部频道"))throw new IllegalStateException("Requires cached real playlist and home startup");
            if(idlePreview) {
                SystemClock.sleep(12000);
                long cpu=android.os.Process.getElapsedCpuTime(), at=SystemClock.elapsedRealtime();
                long rx=android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid());
                key(KeyEvent.KEYCODE_DPAD_DOWN,100);
                JSONArray memory=new JSONArray();
                for(int i=0;i<5;i++){SystemClock.sleep(3000);Debug.MemoryInfo m=new Debug.MemoryInfo();Debug.getMemoryInfo(m);memory.put(m.getTotalPss());}
                JSONObject idle=json("wall_ms",SystemClock.elapsedRealtime()-at,"cpu_ms",android.os.Process.getElapsedCpuTime()-cpu,
                    "rx_bytes",android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid())-rx,"pss_kb",memory);
                Bundle b=new Bundle();b.putString("stream","IDLE_JSON="+idle+"\n");finish(Activity.RESULT_OK,b);return;
            }
            SystemClock.sleep(warmupMs);
            final int startWidth=activity.getWindow().getDecorView().getWidth(), startHeight=activity.getWindow().getDecorView().getHeight();
            Window.OnFrameMetricsAvailableListener listener=(w,m,dropped)->{
                String label=phase;
                if(!label.equals("warmup"))frames.add(json("phase",label,"total_ms",m.getMetric(FrameMetrics.TOTAL_DURATION)/1e6,"vsync_ns",m.getMetric(FrameMetrics.VSYNC_TIMESTAMP),"intended_vsync_ns",m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                    "layout_ms",m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)/1e6,"draw_ms",m.getMetric(FrameMetrics.DRAW_DURATION)/1e6,
                    "animation_ms",m.getMetric(FrameMetrics.ANIMATION_DURATION)/1e6,"sync_ms",m.getMetric(FrameMetrics.SYNC_DURATION)/1e6,
                    "command_ms",m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)/1e6,"swap_ms",m.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)/1e6,
                    "unknown_delay_ms",m.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)/1e6,"dropped_callbacks",dropped));
            };
            ViewTreeObserver.OnDrawListener draw=()->{
                long t=SystemClock.elapsedRealtimeNanos();String label=phase;
                if(recordDraws&&!label.equals("warmup"))draws.add(json("phase",label,"segment",segment,"ns",System.nanoTime(),"last_input_ns",latestInputNanos));
                if(!label.equals("warmup"))for(long start:pending)inputs.add(json("phase",label,"dispatch_to_next_draw_ms",(t-start)/1e6));
                pending.clear();
            };
            runOnMainSync(()->{activity.getWindow().addOnFrameMetricsAvailableListener(listener,new Handler(worker.getLooper()));activity.getWindow().getDecorView().getViewTreeObserver().addOnDrawListener(draw);});
            if(recordScroll)dump("nexttv-scroll-start");
            key(KeyEvent.KEYCODE_DPAD_DOWN,500);
            for(int i=0;i<8;i++)key(KeyEvent.KEYCODE_DPAD_DOWN,90);key(KeyEvent.KEYCODE_ESCAPE,1000);
            firstCard();dump("nexttv-scroll-phase","horizontal");phase="horizontal";
            for(int r=0;r<10;r++){for(int i=0;i<2;i++)key(KeyEvent.KEYCODE_DPAD_RIGHT,90);for(int i=0;i<2;i++)key(KeyEvent.KEYCODE_DPAD_LEFT,90);}
            SystemClock.sleep(600);phase="warmup";firstCard();dump("nexttv-scroll-phase","vertical");phase="vertical";
            for(int r=0;r<6;r++){for(int i=0;i<8;i++)key(KeyEvent.KEYCODE_DPAD_DOWN,90);for(int i=0;i<8;i++)key(KeyEvent.KEYCODE_DPAD_UP,90);}
            SystemClock.sleep(600);
            for(int r=0;!scrollOnly&&r<3;r++){
                phase="warmup";dump("nexttv-scroll-phase","warmup");segment++;
                long preparationAt=SystemClock.uptimeMillis();JSONObject start=prepareReturnStart();start.put("preparation_settle_ms",SystemClock.uptimeMillis()-preparationAt);
                dump("nexttv-scroll-phase","return_top");phase="return_top";
                key(KeyEvent.KEYCODE_ESCAPE,0);
                start.put("back_ns",latestInputNanos);start.put("back_ms",latestInputMillis);returnStarts.put(start);
                // Measure observed completion separately from the functional timeout.
                long returnDeadline=latestInputMillis+4000;
                JSONObject end;
                do{
                    end=grid();
                    if(end.getInt("first")==0&&end.getInt("offset")==0&&end.getInt("selectedIndex")==-1&&!end.getBoolean("returning")&&end.getBoolean("nav"))break;
                    SystemClock.sleep(50);
                }while(SystemClock.uptimeMillis()<returnDeadline);
                end.put("observed_completion_ms",SystemClock.uptimeMillis()-latestInputMillis);
                returnEnds.put(end);
                if(end.getInt("first")!=0||end.getInt("offset")!=0||end.getInt("selectedIndex")!=-1||end.getBoolean("returning")||!end.getBoolean("nav"))throw new IllegalStateException("Return did not restore top and channel tab focus "+end);
            }
            phase="warmup";
            runOnMainSync(()->{activity.getWindow().removeOnFrameMetricsAvailableListener(listener);activity.getWindow().getDecorView().getViewTreeObserver().removeOnDrawListener(draw);});
            Debug.MemoryInfo mem=new Debug.MemoryInfo();Debug.getMemoryInfo(mem);
            String diagnostics=dump("nexttv-scroll-stop");
            JSONObject result=json("focus_events",new JSONArray(new ArrayList<>(focusEvents)),"return_starts",returnStarts,"return_ends",returnEnds,"diagnostics",diagnostics,"frames",new JSONArray(new ArrayList<>(frames)),"inputs",new JSONArray(new ArrayList<>(inputs)),"dispatches",new JSONArray(new ArrayList<>(dispatches)),"pss_kb",mem.getTotalPss(),"draw_frames",new JSONArray(new ArrayList<>(draws)),"warmup_ms",warmupMs,"start_width",startWidth,"start_height",startHeight,"end_width",activity.getWindow().getDecorView().getWidth(),"end_height",activity.getWindow().getDecorView().getHeight());
            if(frames.size()<20)throw new IllegalStateException("Insufficient frame samples");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"fix13-release-frames.json"))){out.write(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            String exportName="nexttv-frames-"+SystemClock.uptimeMillis()+".json";android.content.ContentValues exportValues=new android.content.ContentValues();exportValues.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,exportName);exportValues.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"application/json");exportValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/NextTVPerf");android.net.Uri exportUri=getTargetContext().getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,exportValues);if(exportUri==null)throw new IllegalStateException("No benchmark export URI");try(java.io.OutputStream stream=getTargetContext().getContentResolver().openOutputStream(exportUri)){stream.write(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            restoreProfile();Bundle b=new Bundle();b.putString("stream","PASS Release FrameMetrics recorded: "+frames.size()+" frames, "+inputs.size()+" dispatch/draw samples; export="+exportName+"\n");finish(Activity.RESULT_OK,b);
        }catch(Throwable t){restoreProfile();Bundle b=new Bundle();b.putString("stream",android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,b);}
        finally {worker.quitSafely();}
    }
}
