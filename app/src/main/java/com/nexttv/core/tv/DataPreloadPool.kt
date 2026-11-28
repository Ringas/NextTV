// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.content.Context
import android.os.HandlerThread
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.*
import kotlinx.coroutines.CompletableDeferred

/**
 * Number of preloaded streams currently holding a network connection.
 * RouteHealthProbe uses it to leave room for preloading instead of competing
 * with it; the counter is process-wide because only one playback pool is live.
 */
internal object PreloadActivity {
 private val active=java.util.concurrent.atomic.AtomicInteger()
 fun added(){active.incrementAndGet()}
 fun removed(){active.updateAndGet{value->if(value>0)value-1 else 0}}
 fun cleared(count:Int){active.updateAndGet{value->(value-count).coerceAtLeast(0)}}
 val count:Int get()=active.get()
}

/** Two data loaders share the main playback looper/allocator. They do not create video decoders. */
internal class DataPreloadPool(private val context:Context) {
 private companion object {
  const val KIND_SOURCE=0
  const val KIND_DEEP=1
  const val KIND_SHALLOW=2
 }

 private val thread=HandlerThread("NextTV-media").apply{start()}
 val looper get()=thread.looper
 val loadControl=DefaultLoadControl.Builder().setBufferDurationsMs(800,25000,400,600).build()
 private val activityManager=context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
 private val bufferMillis=PreloadPolicy.bufferMillis(activityManager.memoryClass,activityManager.isLowRamDevice)
 private val builders=mutableMapOf<Int,DefaultPreloadManager>()
 internal data class Entry(
  val channelId:String,
  val manager:DefaultPreloadManager,
  val item:MediaItem,
  val source:MediaSource,
  val routeIndex:Int,
  val deep:Boolean,
  val createdAt:Long=SystemClock.elapsedRealtime(),
  var ready:Boolean=false,
  var failed:Boolean=false,
 ){
  val result=CompletableDeferred<PreloadException?>()
 }
 private val entries=linkedMapOf<String,Entry>()
 private var borrowed:Entry?=null
 private fun manager(slot:Int)=builders.getOrPut(slot){
  val control=TargetPreloadStatusControl<Int,DefaultPreloadManager.PreloadStatus>{kind->
   if(kind==KIND_DEEP&&bufferMillis>0L)DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(bufferMillis)
   else DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_SOURCE_PREPARED
  }
  DefaultPreloadManager.Builder(context,control).setPreloadLooper(looper).setLoadControl(loadControl)
   .setRenderersFactory(DecoderCompatibility.renderers(context)).build().also{m->
    m.addListener(object:PreloadManagerListener {
     override fun onCompleted(mediaItem:MediaItem){entries.values.firstOrNull{it.item==mediaItem}?.let{it.ready=true;it.result.complete(null)}}
     override fun onError(error:PreloadException){entries.values.firstOrNull{it.item==error.mediaItem}?.let{it.failed=true;it.result.complete(error)}}
    })
   }
 }
 fun add(channelId:String,slot:Int,source:MediaSource,routeIndex:Int,deep:Boolean):Entry?{
  if(channelId in entries)return null
  val m=manager(slot)
  val hls=source.mediaItem.localConfiguration?.mimeType==com.nexttv.HLS_MIME
  m.add(source,when{!deep->KIND_SHALLOW;hls->KIND_DEEP;else->KIND_SOURCE})
  val wrapped=m.getMediaSource(source.mediaItem)?:error("预热源创建失败")
  val entry=Entry(channelId,m,source.mediaItem,wrapped,routeIndex,deep)
  entries[channelId]=entry
  PreloadActivity.added()
  m.invalidate()
  return entry
 }
 fun existing(channelId:String):Entry?=entries[channelId]
 fun routeIndex(channelId:String):Int?=entries[channelId]?.takeUnless{it.failed}?.routeIndex
 fun take(channelId:String,index:Int):Entry?=entries[channelId]?.takeIf{it.routeIndex==index&&!it.failed}?.also{
  entries.remove(channelId)
  PreloadActivity.removed()
 }
 fun remove(channelId:String):Entry?{
  val entry=entries.remove(channelId) ?: return null
  PreloadActivity.removed()
  entry.manager.remove(entry.item)
  return entry
 }
 fun discard(entry:Entry){
  if(entries[entry.channelId]!==entry)return
  remove(entry.channelId)
 }
 fun retain(keepIds:Set<String>){
  entries.keys.toList().filterNot{it in keepIds}.forEach{remove(it)}
 }
 fun pruneStale(now:Long,deepTtl:Long,shallowTtl:Long){
  entries.values.toList().forEach{entry->
   val ttl=if(entry.deep)deepTtl else shallowTtl
   if(now-entry.createdAt>=ttl)discard(entry)
  }
 }
 fun adopt(entry:Entry){borrowed=entry}
 fun releaseBorrowed(){borrowed?.let{it.manager.remove(it.item)};borrowed=null}
 fun clear(){val count=entries.size;entries.values.toList().forEach{it.manager.remove(it.item)};entries.clear();PreloadActivity.cleared(count)}
 fun readyIds()=entries.values.filter{it.ready}.map{it.channelId}
 fun deepCount()=entries.values.count{it.deep}
 fun shallowCount()=entries.values.count{!it.deep}
 fun size()=entries.size
 fun release(){clear();releaseBorrowed();builders.values.forEach{it.release()};builders.clear();thread.quitSafely()}
}
