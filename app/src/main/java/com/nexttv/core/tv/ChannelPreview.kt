// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nexttv.core.App
import com.nexttv.core.data.Channel
import com.nexttv.core.data.playbackHeaders
import com.nexttv.core.data.playbackUserAgent
import com.nexttv.resolveStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private object PreviewRouteMemory {
 private val lastGood = android.util.LruCache<String, com.nexttv.core.data.ChannelRoute>(128)
 private val failedUntil = android.util.LruCache<com.nexttv.core.data.ChannelRoute, Long>(256)

 fun order(id: String, routes: List<com.nexttv.core.data.ChannelRoute>) =
  previewRouteOrder(routes).sortedBy { it != lastGood.get(id) }
 fun remaining(route: com.nexttv.core.data.ChannelRoute): Long =
  ((failedUntil.get(route) ?: 0L) - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
 fun success(id: String, route: com.nexttv.core.data.ChannelRoute) {
  lastGood.put(id, route)
  failedUntil.remove(route)
 }
 fun failure(route: com.nexttv.core.data.ChannelRoute, reason: String) {
  val cooldown = if (reason == "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED") 120_000L else 30_000L
  failedUntil.put(route, android.os.SystemClock.elapsedRealtime() + cooldown)
 }
}

/** This entire policy is absent with previews OFF. Input pauses existing decoders, not recreates them. */
@Composable internal fun previewsReady(grid:LazyGridState):Boolean {
 val lifecycle=LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
 val lastInput=InteractionStyle.lastInputAt
 val scrolling=grid.isScrollInProgress
 var ready by remember{mutableStateOf(false)}
 LaunchedEffect(lastInput,scrolling,lifecycle){ready=false;if(!scrolling&&lifecycle==Lifecycle.State.RESUMED){delay(900);ready=true}}
 return ready&&!scrolling&&lifecycle==Lifecycle.State.RESUMED
}

/** One muted stream per actually visible card. Offscreen/cached cards never mount a player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun ChannelPreview(channel:Channel,running:Boolean,slot:Int,modifier:Modifier=Modifier){
 val lifecycle=LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
 if(lifecycle!=Lifecycle.State.RESUMED)return
 var activated by remember(channel.id){mutableStateOf(false)}
 LaunchedEffect(running){if(running)activated=true}
 if(!activated)return
 val context=LocalContext.current
 val latestRunning by rememberUpdatedState(running)
 val view=remember(channel.id){TextureView(context).apply{isFocusable=false;isClickable=false;importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO}}
 var frame by remember(channel.id){mutableStateOf(false)}
 var playerState by remember(channel.id){mutableStateOf<ExoPlayer?>(null)}
 AndroidView(factory={view},modifier=modifier.focusProperties{canFocus=false}.clearAndSetSemantics{}.graphicsLayer{alpha=if(frame)1f else 0f})
 LaunchedEffect(running, playerState) {
  val player = playerState ?: return@LaunchedEffect
  if (!running) {
   player.playWhenReady = false
  } else if (!player.playWhenReady) {
   delay(slot * 70L)
   if (player.currentMediaItem != null) player.seekToDefaultPosition()
   player.playWhenReady = true
  }
 }
 val scope=rememberCoroutineScope()
 val routes=remember(channel.id, channel.playbackRoutes){PreviewRouteMemory.order(channel.id, channel.playbackRoutes)}
 DisposableEffect(channel.id,routes,view){
  PreviewDiagnostics.sessionStarts++
  frame=false
  var player:ExoPlayer?=null;var disposed=false
  fun release(){player?.let{p->player=null;playerState=null;p.clearVideoTextureView(view);p.release();PreviewDiagnostics.stopped(channel.id)}}
  val request=scope.launch{
   snapshotFlow{latestRunning}.first{it}
   delay(previewStartDelay(slot))
   var available = routes.filter { PreviewRouteMemory.remaining(it) == 0L }
   if (available.isEmpty() && routes.isNotEmpty()) {
    delay(routes.minOf { PreviewRouteMemory.remaining(it) })
    available = routes.filter { PreviewRouteMemory.remaining(it) == 0L }
   }
   for((routeIndex,route)in available.withIndex()){
    fun recordFailure(reason: String) {
     PreviewRouteMemory.failure(route, reason)
     PreviewDiagnostics.error(channel.id, routeIndex, reason)
    }
    try{
     ensureActive();snapshotFlow{latestRunning}.first{it}
     val app=App.get();val source=app.channels.sources.firstOrNull{it.id==route.sourceId}
     val headers=playbackHeaders(source,app.prefs.defaultUserAgent,app.prefs.defaultReferer,route.headers)
     val resolved=withTimeout(8000){resolveStream(route.url,headers)}
     ensureActive();snapshotFlow{latestRunning}.first{it}
     val http=DefaultHttpDataSource.Factory().setUserAgent(playbackUserAgent(headers,app.prefs.defaultUserAgent)).setDefaultRequestProperties(headers).setConnectTimeoutMs(6000).setReadTimeoutMs(8000).setAllowCrossProtocolRedirects(true)
     val buffers=DefaultLoadControl.Builder().setBufferDurationsMs(1500,4000,250,500).setTargetBufferBytes(4*1024*1024).build()
     val p=ExoPlayer.Builder(context,DefaultRenderersFactory(context).setEnableDecoderFallback(true)).setLoadControl(buffers).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
     player=p;playerState=p;PreviewDiagnostics.started(channel.id)
     p.volume=0f;p.setAudioAttributes(AudioAttributes.DEFAULT,false)
     p.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
     p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,true).setMaxVideoSize(426,240).setForceLowestBitrate(true).build()
     val firstFrame=CompletableDeferred<Unit>();val failure=CompletableDeferred<PlaybackException>();var liveRetry=false
     p.addListener(object:Player.Listener{
      override fun onRenderedFirstFrame(){if(!disposed&&player===p){frame=true;PreviewRouteMemory.success(channel.id, route);firstFrame.complete(Unit);PreviewDiagnostics.rendered(channel.id)}}
      override fun onPlayerError(error:PlaybackException){if(!disposed&&player===p){
       if(error.errorCode==PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW&&!liveRetry){liveRetry=true;PreviewDiagnostics.liveRecoveries++;p.seekToDefaultPosition();p.prepare();p.playWhenReady=latestRunning}
       else failure.complete(error)
      }}
     })
     p.setVideoTextureView(view)
     p.setMediaItem(MediaItem.Builder().setUri(resolved.url).setMimeType(resolved.mime).build())
     PreviewDiagnostics.requests++;p.prepare();p.playWhenReady=latestRunning
     val rendered=withTimeoutOrNull(15000){kotlinx.coroutines.selects.select<Boolean>{firstFrame.onAwait{true};failure.onAwait{false}}}==true
     val error=if(rendered)failure.await()else if(failure.isCompleted)failure.await()else null
     recordFailure(error?.errorCodeName?:"FIRST_FRAME_TIMEOUT")
    }catch(e:TimeoutCancellationException){ensureActive();recordFailure("PROBE_TIMEOUT")}catch(e:CancellationException){throw e}catch(e:Exception){recordFailure(e.javaClass.simpleName)}
    frame=false;release()
    if(routeIndex<available.lastIndex)PreviewDiagnostics.routeSwitches++
   }
   PreviewDiagnostics.failed(channel.id)
  }
  onDispose{disposed=true;request.cancel();release()}
 }
}

internal object PreviewDiagnostics {
 private val errors=java.util.ArrayDeque<String>()
 fun error(id:String,route:Int,reason:String){if(errors.size>=32)errors.removeFirst();errors.addLast("$id:$route:$reason")}
 var sessionStarts=0
 var routeSwitches=0;var liveRecoveries=0
 var targetCount=0
 private var targets=emptySet<String>()
 fun visible(ids:Set<String>){targets=ids;targetCount=ids.size}
 var activePlayers=0;private set
 var peakPlayers=0;private set
 var requests=0;var frames=0
 private val active=linkedSetOf<String>();private val rendered=linkedSetOf<String>();private val failed=linkedSetOf<String>()
 fun started(id:String){active.add(id);activePlayers=active.size;peakPlayers=maxOf(peakPlayers,activePlayers);failed.remove(id)}
 fun stopped(id:String){active.remove(id);rendered.remove(id);activePlayers=active.size}
 fun rendered(id:String){rendered.add(id);frames++}
 fun failed(id:String){failed.add(id);if(failed.size>32)failed.remove(failed.first())}
 fun snapshot()=org.json.JSONObject().put("targetCount",targetCount).put("activePlayers",activePlayers).put("renderedPlayers",rendered.count{it in active}).put("peakPlayers",peakPlayers).put("sessionStarts",sessionStarts).put("routeSwitches",routeSwitches).put("liveRecoveries",liveRecoveries).put("requests",requests).put("frames",frames).put("activeIds",org.json.JSONArray(active.toList())).put("targetIds",org.json.JSONArray(targets.toList())).put("failedIds",org.json.JSONArray(failed.toList())).put("errors",org.json.JSONArray(errors.toList()))
}
