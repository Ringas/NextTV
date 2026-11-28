// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.*
import android.view.View
import android.view.ViewGroup

/** Captures the actual video surface, independently of PlayerView's reset shutter. */
internal class RetainedVideoFrame {
 var surfaceRoot:View?=null
 var bitmap by mutableStateOf<Bitmap?>(null);private set
 var captureVersion by mutableIntStateOf(0);private set
 private var generation=0
 private var ticket=0
 private var inFlight=0
 private var latestNeed:(()->Boolean)?=null
 fun clear(){generation++;bitmap=null;latestNeed=null}
 fun capture(replace:Boolean=false,stillNeeded:()->Boolean){
  latestNeed=stillNeeded
  if(inFlight!=0){if(replace)generation++;return}
  if(bitmap!=null&&!replace)return
  val surface=findVideoSurface(surfaceRoot)?:return
  if(surface.width<=0||surface.height<=0)return
  val memory=surface.context.getSystemService(android.content.Context.ACTIVITY_SERVICE)as android.app.ActivityManager
  val edge=if(memory.isLowRamDevice||memory.memoryClass<192)640f else 960f
  val scale=minOf(1f,edge/surface.width,edge/surface.height)
  val width=(surface.width*scale).toInt().coerceAtLeast(1);val height=(surface.height*scale).toInt().coerceAtLeast(1)
  val output=runCatching{createBitmap(width,height,Bitmap.Config.ARGB_8888)}.getOrNull()?:return
  val request=++ticket;val requestedGeneration=generation;inFlight=request
  fun finish(success:Boolean){
   if(inFlight!=request)return
   inFlight=0
   val need=latestNeed
   if(success&&requestedGeneration==generation&&need?.invoke()==true){bitmap=output;captureVersion++}
   else if(requestedGeneration!=generation&&need?.invoke()==true)capture(replace=true,stillNeeded=need)
  }
  if(surface is SurfaceView&&Build.VERSION.SDK_INT>=24){
   runCatching{PixelCopy.request(surface,output,{result->finish(result==PixelCopy.SUCCESS)},Handler(Looper.getMainLooper()))}.onFailure{finish(false)}
  }else if(surface is TextureView){runCatching{surface.getBitmap(output)!=null}.fold({finish(it)},{finish(false)})}
  else finish(false)
 }
 fun diagnostics():org.json.JSONObject {
  val frame=bitmap;var low=255;var high=0
  if(frame!=null)for(y in 1..7)for(x in 1..11){val pixel=frame[frame.width*x/12,frame.height*y/8];val value=(android.graphics.Color.red(pixel)+android.graphics.Color.green(pixel)+android.graphics.Color.blue(pixel))/3;low=minOf(low,value);high=maxOf(high,value)}
  return org.json.JSONObject().put("pendingCopy",inFlight!=0).put("retained",frame!=null).put("width",frame?.width?:0).put("height",frame?.height?:0).put("variation",if(frame!=null)high-low else 0)
 }
 fun dispose(){surfaceRoot=null;clear()}
}

/** Shared with the official Compose video host; no dependency on PlayerView internals. */
internal fun findVideoSurface(root:View?):View? {
 if(root is SurfaceView||root is TextureView)return root
 if(root is ViewGroup)for(i in 0 until root.childCount)findVideoSurface(root.getChildAt(i))?.let{return it}
 return null
}
