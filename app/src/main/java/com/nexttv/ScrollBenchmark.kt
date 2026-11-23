package com.nexttv

/** Opt-in Activity.dump diagnostics. Disabled during normal operation. */
internal object ScrollBenchmark {
 private val samples=ArrayList<org.json.JSONArray>()
 private var phase="warmup"
 private var running=false
 private var frameTime=0L
 private val callback=object:android.view.Choreographer.FrameCallback{
  override fun doFrame(time:Long){frameTime=time;if(running)android.view.Choreographer.getInstance().postFrameCallback(this)}
 }
 fun start(){samples.clear();phase="warmup";running=true;android.view.Choreographer.getInstance().postFrameCallback(callback);MotionProbe.frame={_:Float,_:Float,index:Int,offset:Int->if(samples.size<4096)samples.add(org.json.JSONArray().put(android.os.SystemClock.uptimeMillis()).put(frameTime).put(phase).put(index).put(offset))}}
 fun phase(value:String){phase=value}
 fun stop():org.json.JSONArray{running=false;MotionProbe.frame=null;android.view.Choreographer.getInstance().removeFrameCallback(callback);return org.json.JSONArray(samples)}
}
