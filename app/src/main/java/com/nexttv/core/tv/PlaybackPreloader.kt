// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.app.ActivityManager
import com.nexttv.core.App
import com.nexttv.core.data.Channel
import com.nexttv.core.data.ChannelRoute
import com.nexttv.core.data.RouteHealth
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

internal class PlaybackPreloader(
 private val app: App,
 private val resources: ActivityManager,
 private val preloadPool: DataPreloadPool,
 private val sourceFactory: PlaybackSourceFactory,
 private val preferredRoute: (Channel) -> Int,
) {
 private val unavailableWarmChannels=mutableMapOf<String,WarmFailure>()
 private data class WarmFailure(val routes:List<ChannelRoute>,val expiresAt:Long)
 fun confirmedUnavailable(channel:Channel):Boolean {
  if(app.prefs.preloadMode==0)return false
  val failure=unavailableWarmChannels[channel.id]?:return false
  return failure.expiresAt>android.os.SystemClock.elapsedRealtime()&&failure.routes==channel.playbackRoutes
 }
 val confirmedFailureCount get()=unavailableWarmChannels.size
 fun clearFailures(){unavailableWarmChannels.clear()}
 suspend fun warm(channelId:String,direction:Int,stillCurrent:()->Boolean){
  val mode=app.prefs.preloadMode
  if(mode==0||!stillCurrent())return
  while(currentCoroutineContext().isActive&&stillCurrent()&&app.prefs.preloadMode==mode){
   val channels=app.channels.displayChannels
   val index=channels.indexOfFirst{it.id==channelId}
   if(channels.size<2||index<0)return
   // A route recovers asynchronously. Recompute the plan after a short settle so
   // the old next-channel preload (e.g. C) is not started in the same instant the
   // recovered route (e.g. B) becomes the new primary/backup target.
   delay(PreloadPolicy.PLAN_SETTLE_MS)
   if(!stillCurrent()||app.prefs.preloadMode!=mode)return
   val settledChannels=app.channels.displayChannels
   val settledIndex=settledChannels.indexOfFirst{it.id==channelId}
   if(settledChannels.size<2||settledIndex<0)return
   val now=android.os.SystemClock.elapsedRealtime()
   unavailableWarmChannels.entries.removeAll{it.value.expiresAt<=now}
   val plan=effectiveTargets(settledChannels,PreloadPolicy.plan(settledChannels.size,settledIndex,direction,mode))
    .filter{!app.prefs.hideUnavailableRoutes||RouteHealth.isChannelAvailable(settledChannels[it.index])}
   if(plan.isEmpty())return
   val keepIds=plan.mapTo(hashSetOf()){settledChannels[it.index].id}
   preloadPool.retain(keepIds)
   preloadPool.pruneStale(
    now,
    PreloadPolicy.deepTtlMillis(resources.memoryClass,resources.isLowRamDevice),
    PreloadPolicy.shallowTtlMillis(resources.memoryClass,resources.isLowRamDevice),
   )
   val primary=plan.firstOrNull{it.deep}
   val backups=plan.filter{it!==primary}
   var primaryReady=false
   coroutineScope {
    val primaryJob=primary?.let{target->async{warmTarget(settledChannels[target.index],target.slot,true,stillCurrent)}}
    val backupJobs=backups.map{target->async{warmTarget(settledChannels[target.index],target.slot,false,stillCurrent)}}
    primaryReady=primaryJob?.await()?:false
    backupJobs.awaitAll()
    if(primary!=null&&!primaryReady&&confirmedUnavailable(settledChannels[primary.index])){
     val promote=backups.firstOrNull{it.slot==0}
     if(promote!=null){
      preloadPool.remove(settledChannels[promote.index].id)
      withTimeoutOrNull(PreloadPolicy.DIRECTION_TIMEOUT_MS){
       warmChannel(settledChannels[promote.index],promote.slot,true,stillCurrent)
      }
     }
    }
   }
   delay(PreloadPolicy.refreshIntervalMillis(resources.memoryClass,resources.isLowRamDevice))
  }
 }
 /**
  * 已确认不可用的台不再反复深预热；它的下一个候选会自动接替深预热位。
  * 另一方向仍只保留一个浅候选，避免「上下台」模式把线路放大成 6 路。
  */
 private fun effectiveTargets(channels:List<Channel>,plan:List<PreloadPolicy.Target>):List<PreloadPolicy.Target> {
  val primary=plan.filter{it.slot==0}
  val available=primary.filterNot{confirmedUnavailable(channels[it.index])}
  val chosen=(available.firstOrNull()?:primary.firstOrNull())?.copy(deep=true)?:return emptyList()
  val rest=primary.filter{
   it.index!=chosen.index&&!confirmedUnavailable(channels[it.index])
  }.map{it.copy(deep=false)}
  val opposite=plan.filter{it.slot!=0}.map{it.copy(deep=false)}
  return listOf(chosen)+rest+opposite
 }
 private suspend fun warmTarget(channel:Channel,slot:Int,deep:Boolean,stillCurrent:()->Boolean):Boolean {
  if(!stillCurrent())return false
  return withTimeoutOrNull(if(deep)PreloadPolicy.DIRECTION_TIMEOUT_MS else PreloadPolicy.SHALLOW_TIMEOUT_MS){
   warmChannel(channel,slot,deep,stillCurrent)
  }?:false
 }
 private suspend fun warmChannel(channel:Channel,slot:Int,deep:Boolean,stillCurrent:()->Boolean):Boolean {
  val routes=channel.playbackRoutes
  val attempts=PreloadPolicy.routeAttempts(routes.size,preferredRoute(channel))
  var definitiveFailures=0
  for(index in attempts){
   if(!stillCurrent())return false
   var entry:DataPreloadPool.Entry?=null
   var ready=false
   try {
    withTimeoutOrNull(PreloadPolicy.ATTEMPT_TIMEOUT_MS) {
     if(!stillCurrent())return@withTimeoutOrNull
     if(!com.nexttv.supportsHttpPlayback(routes[index].url)){
      definitiveFailures++
      return@withTimeoutOrNull
     }
     preloadPool.existing(channel.id)?.let{existing->
      if(existing.failed||(deep&&!existing.deep))preloadPool.remove(channel.id)
     }
     val httpFailure=CompletableDeferred<Exception>()
     val media=sourceFactory.create(channel,index,onPreloadHttpFailure={httpFailure.complete(it)})
     ensureActive()
     val owned=preloadPool.add(channel.id,slot,media,index,deep)
     val pending=owned?:preloadPool.existing(channel.id)?:return@withTimeoutOrNull
     entry=owned
     val error=select<Throwable?> {
      if(owned!=null)httpFailure.onAwait{it}
      pending.result.onAwait{it}
     }
     if(error==null)ready=true
     else if(pending.routeIndex==index&&isDefinitiveRouteFailure(error)){definitiveFailures++;RouteHealth.markProbeFailure(routes[index], definitive = true)}
    }
    if(ready){
     RouteHealth.markProbeSuccess(routes[index])
     unavailableWarmChannels.remove(channel.id)
     return true
    }
   } catch(e:CancellationException){
    throw e
   } catch(e:Exception){
    if(isDefinitiveRouteFailure(e)){definitiveFailures++;RouteHealth.markProbeFailure(routes[index], definitive = true)}
   } finally {
    if(!ready)entry?.let(preloadPool::discard)
   }
  }
  if(attempts.isNotEmpty()&&attempts.distinct().size==routes.size&&definitiveFailures==attempts.size){
   unavailableWarmChannels[channel.id]=WarmFailure(routes,android.os.SystemClock.elapsedRealtime()+PreloadPolicy.FAILURE_TTL_MS)
  }
  return false
 }
}
