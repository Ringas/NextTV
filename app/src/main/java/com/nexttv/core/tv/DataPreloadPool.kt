// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.content.Context
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.*
import kotlinx.coroutines.CompletableDeferred

/** Two data loaders share the main playback looper/allocator. They do not create video decoders. */
internal class DataPreloadPool(private val context:Context) {
 private val thread=HandlerThread("NextTV-media").apply{start()}
 val looper get()=thread.looper
 val loadControl=DefaultLoadControl.Builder().setBufferDurationsMs(800,25000,400,600).build()
 private val activityManager=context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
 private val bufferMillis=PreloadPolicy.bufferMillis(activityManager.memoryClass,activityManager.isLowRamDevice)
 private val builders=mutableMapOf<Int,DefaultPreloadManager>()
 internal data class Entry(val channelId:String,val manager:DefaultPreloadManager,val item:MediaItem,val source:MediaSource,val routeIndex:Int,var ready:Boolean=false,var failed:Boolean=false){
  val result=CompletableDeferred<PreloadException?>()
 }
 private val entries=linkedMapOf<String,Entry>()
 private var borrowed:Entry?=null
 private fun manager(slot:Int)=builders.getOrPut(slot){
  val control=TargetPreloadStatusControl<Int,DefaultPreloadManager.PreloadStatus>{kind->
   if(kind==0||bufferMillis==0L)DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_SOURCE_PREPARED
   else DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(bufferMillis)
  }
  DefaultPreloadManager.Builder(context,control).setPreloadLooper(looper).setLoadControl(loadControl)
   .setRenderersFactory(DecoderCompatibility.renderers(context)).build().also{m->
    m.addListener(object:PreloadManagerListener {
     override fun onCompleted(mediaItem:MediaItem){entries.values.firstOrNull{it.item==mediaItem}?.let{it.ready=true;it.result.complete(null)}}
     override fun onError(error:PreloadException){entries.values.firstOrNull{it.item==error.mediaItem}?.let{it.failed=true;it.result.complete(error)}}
    })
   }
 }
 fun add(channelId:String,slot:Int,source:MediaSource,routeIndex:Int):Entry?{
  if(channelId in entries)return null
  val m=manager(slot);m.add(source,if(source.mediaItem.localConfiguration?.mimeType==com.nexttv.HLS_MIME)1 else 0)
  val wrapped=m.getMediaSource(source.mediaItem)?:error("预热源创建失败")
  val entry=Entry(channelId,m,source.mediaItem,wrapped,routeIndex)
  entries[channelId]=entry
  m.invalidate()
  return entry
 }
 fun existing(channelId:String):Entry?=entries[channelId]
 fun routeIndex(channelId:String):Int?=entries[channelId]?.takeUnless{it.failed}?.routeIndex
 fun take(channelId:String,index:Int):Entry?=entries[channelId]?.takeIf{it.routeIndex==index&&!it.failed}?.also{entries.remove(channelId)}
 fun discard(entry:Entry){
  if(entries[entry.channelId]!==entry)return
  entries.remove(entry.channelId)
  entry.manager.remove(entry.item)
 }
 fun adopt(entry:Entry){borrowed=entry}
 fun releaseBorrowed(){borrowed?.let{it.manager.remove(it.item)};borrowed=null}
 fun clear(){entries.values.forEach{it.manager.remove(it.item)};entries.clear()}
 fun readyIds()=entries.values.filter{it.ready}.map{it.channelId}
 fun size()=entries.size
 fun release(){clear();releaseBorrowed();builders.values.forEach{it.release()};builders.clear();thread.quitSafely()}
}
