// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.app.ActivityManager
import com.nexttv.core.App
import com.nexttv.core.data.Channel
import com.nexttv.core.data.ChannelRoute
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
 suspend fun warm(channelId:String,stillCurrent:()->Boolean){
  val mode=app.prefs.preloadMode
  if(mode==0||!stillCurrent())return
  while(currentCoroutineContext().isActive&&stillCurrent()&&app.prefs.preloadMode==mode){
   val channels=app.channels.channels
   val index=channels.indexOfFirst{it.id==channelId}
   if(channels.size<2||index<0)return
   val now=android.os.SystemClock.elapsedRealtime()
   unavailableWarmChannels.entries.removeAll{it.value.expiresAt<=now}
   preloadPool.clear()
   coroutineScope {
    PreloadPolicy.directions(mode).forEachIndexed { slot, offset ->
     launch {
      withTimeoutOrNull(PreloadPolicy.DIRECTION_TIMEOUT_MS) {
       for(candidate in PreloadPolicy.indices(channels.size,index,offset,PreloadPolicy.MAX_CHANNELS)){
        if(warmChannel(channels[candidate],slot))break
       }
      }
     }
    }
   }
   delay(PreloadPolicy.refreshIntervalMillis(resources.memoryClass,resources.isLowRamDevice))
  }
 }
 private suspend fun warmChannel(channel:Channel,slot:Int):Boolean {
  val routes=channel.playbackRoutes
  val attempts=PreloadPolicy.routeAttempts(routes.size,preferredRoute(channel))
  var definitiveFailures=0
  for(index in attempts){
   var entry:DataPreloadPool.Entry?=null
   var ready=false
   try {
    withTimeoutOrNull(PreloadPolicy.ATTEMPT_TIMEOUT_MS) {
     if(!com.nexttv.supportsHttpPlayback(routes[index].url)){
      definitiveFailures++
      return@withTimeoutOrNull
     }
     val httpFailure=CompletableDeferred<Exception>()
     val media=sourceFactory.create(channel,index,onPreloadHttpFailure={httpFailure.complete(it)})
     ensureActive()
     val owned=preloadPool.add(channel.id,slot,media,index)
     val pending=owned?:preloadPool.existing(channel.id)?:return@withTimeoutOrNull
     entry=owned
     val error=select<Throwable?> {
      if(owned!=null)httpFailure.onAwait{it}
      pending.result.onAwait{it}
     }
     if(error==null)ready=true
     else if(pending.routeIndex==index&&isConfirmedPreloadFailure(error))definitiveFailures++
    }
    if(ready){
     unavailableWarmChannels.remove(channel.id)
     return true
    }
   } catch(e:CancellationException){
    throw e
   } catch(e:Exception){
    if(isConfirmedPreloadFailure(e))definitiveFailures++
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
