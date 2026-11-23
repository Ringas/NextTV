// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.content.Context
import androidx.compose.runtime.*
import androidx.media3.common.*
import androidx.media3.exoplayer.*
import com.nexttv.core.App
import com.nexttv.core.data.*
import kotlinx.coroutines.*

/** Each attempt owns its player/listener/job. Late callbacks cannot fail or retune a newer attempt. */
class PlaybackSession(private val context:Context,initial:Channel,private val scope:CoroutineScope){
 companion object {
  private var activeRef:java.lang.ref.WeakReference<PlaybackSession>?=null
  /** 只保留弱引用：Activity 非正常销毁时不会把 Context 长期钉在静态字段上。 */
  internal var active:PlaybackSession?
   get()=activeRef?.get()
   private set(value){activeRef=value?.let{java.lang.ref.WeakReference(it)}}
  internal var stallTimeoutMillis=25000L
 }
 val app=App.get()
 var current by mutableStateOf(initial);private set
 private val preloadPool=DataPreloadPool(context)
 var beforeRetune:(suspend ()->Unit)?=null
 private var retuneJob:Job?=null
 internal var surfaceHasFrame=false;private set
 private var requestedAutoplay=true
 private val decoderHistory=DecoderHistory(context)
 private val playbackRecovery=PlaybackRecovery()
 private val recoveryBudget get()=playbackRecovery.budget
 private val videoFailedRoutes get()=playbackRecovery.videoFailedRoutes
 private var manualDecoderMode by mutableStateOf("auto")
 private var manualDecoderMime:String?=null
 private var lastVideoFormat:Format?=null
 private var decoderPreferences=DecoderPreferences()
 private var decoderContext=""
 private val audioRecoveryAttempts get()=playbackRecovery.audioRecoveryAttempts
 private var activePlayer by mutableStateOf<ExoPlayer?>(null)
 val player:ExoPlayer get()=checkNotNull(activePlayer)
 var routeIndex by mutableIntStateOf(0);private set
 val routeCount get()=current.playbackRoutes.size
 var loading by mutableStateOf(false);private set
 var error by mutableStateOf("");private set
 var ready by mutableStateOf(false);private set
 var rendered by mutableStateOf(false);private set
 var tuneSerial by mutableIntStateOf(0);private set
 var aspect by mutableIntStateOf(0)
 var replay by mutableStateOf<Program?>(null);private set
 private val transferWindow=TransferRateWindow()
 private var rateSampleAt=android.os.SystemClock.elapsedRealtime()
 var bytesPerSecond by mutableLongStateOf(0);private set
 private var byteCount=0L;private var rateJob:Job?=null
 private val transfer=object:androidx.media3.datasource.TransferListener{override fun onTransferInitializing(s:androidx.media3.datasource.DataSource,d:androidx.media3.datasource.DataSpec,n:Boolean){};override fun onTransferStart(s:androidx.media3.datasource.DataSource,d:androidx.media3.datasource.DataSpec,n:Boolean){};override fun onBytesTransferred(s:androidx.media3.datasource.DataSource,d:androidx.media3.datasource.DataSpec,n:Boolean,b:Int){if(n)synchronized(this@PlaybackSession){byteCount+=b}};override fun onTransferEnd(s:androidx.media3.datasource.DataSource,d:androidx.media3.datasource.DataSpec,n:Boolean){}}
 private val resources=context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
 private val sourceFactory=PlaybackSourceFactory(app,transfer){id,index,replayStart->
  current.id==id&&routeIndex==index&&replay?.start==replayStart
 }
 private val preloader=PlaybackPreloader(app,resources,preloadPool,sourceFactory,::preferredRoute)
 private val warmSlots get()=if(app.prefs.preloadMode==3)2 else if(app.prefs.preloadMode==0)0 else 1
 private val lastGoodRoutes=mutableMapOf<String,String>()
 private fun routeKey(channel:Channel,index:Int)=channel.playbackRoutes.getOrNull(index)?.let{"${it.sourceId}\n${it.url}\n${it.headers.toSortedMap()}"}.orEmpty()
 private val softwareAttempts=mutableSetOf<String>()
 private var tuneStarted=0L
 private var firstFrameMs=0L
 private var decoderName=""
 private var softwareDecoder=false
 private val recentFailures=java.util.ArrayDeque<String>()
 internal var latestFailureDetail by mutableStateOf("")
  private set
 private fun recordFailure(code:String,message:String){
  if(recentFailures.size>=16)recentFailures.removeFirst()
  recentFailures.addLast("${current.name}[${routeIndex+1}] "+code.take(160))
  latestFailureDetail="${com.nexttv.core.ui.Ui.displayName(current.name)} · 线路 ${routeIndex+1} · $message"
 }
 private fun failWithReason(code:String,message:String,serial:Int){
  if(closed||serial!=tuneSerial||recovering||!foreground)return
  recordFailure(code,message)
  failed(message,serial)
 }
 private var audioDecoderName=""
 internal val videoDecoderLabel get()=decoderName
 internal val audioDecoderLabel get()=audioDecoderName
 private var audioSoftwareDecoder=false
 private var warmHits=0
 private var warmMisses=0
 fun diagnostics()="warmSlots=$warmSlots prepared=${preloadPool.size()} readyIds=${preloadPool.readyIds()} confirmedWarmFailures=${preloader.confirmedFailureCount} hits=$warmHits misses=$warmMisses firstFrameMs=$firstFrameMs softwareAttempts=${softwareAttempts.size} decoder=$decoderName software=$softwareDecoder audioDecoder=$audioDecoderName audioSoftware=$audioSoftwareDecoder pcm=${decoderPreferences.forcePcm} audioRetries=${audioRecoveryAttempts.size} failures=${recentFailures.joinToString(" | ")}"
 fun trimMemory(){warmJob?.cancel();clearWarm();preloader.clearFailures()}
 private fun clearWarm(){preloadPool.clear()}
 private var prepareJob:Job?=null;private var warmJob:Job?=null;private var retryJob:Job?=null;private var loadingJob:Job?=null;private var timeoutJob:Job?=null
 private var stableJob:Job?=null
 private var waitingForNetwork=false
 private var networkLost=false
 private val network=PlaybackNetwork(context)
 private fun offline()=network.offline
 private val networkCallback=network.callback(
  onLost={scope.launch{networkLost=true}},
  onAvailable={scope.launch{networkAvailable()}},
 )
 private fun networkAvailable(){
  if(closed||offline())return
  val wasLost=networkLost
  networkLost=false
  if(waitingForNetwork)resumeAfterNetworkWait()
  else if(wasLost&&foreground&&error.isNotBlank()){
   pendingPosition=if(replay!=null)resumePosition else null
   restartCurrentRoute()
  }
 }
 private fun resumeAfterNetworkWait(){
  if(closed||!foreground||!waitingForNetwork||offline())return
  waitingForNetwork=false
  restartCurrentRoute()
 }
 private fun restartCurrentRoute(autoplay:Boolean=requestedAutoplay){
  exhausted.clear();recoveryBudget.reset();videoFailedRoutes.clear()
  recovery.reset(routeIndex);replayRecovery.reset(replayRoutes().indexOf(routeIndex))
  attempt(current,routeIndex,autoplay)
 }
 private fun waitForNetwork(){
  if(waitingForNetwork)return
  waitingForNetwork=true
  tuneSerial++
  prepareJob?.cancel();timeoutJob?.cancel();loadingJob?.cancel();stableJob?.cancel();warmJob?.cancel()
  recoveryBudget.pause()
  loading=false;ready=false;rendered=false;surfaceHasFrame=false
  recordFailure("NETWORK_DISCONNECTED","网络已断开")
  error="网络已断开，正在等待网络恢复…"
  activePlayer?.stop()
  clearWarm();preloadPool.releaseBorrowed();preloader.clearFailures()
 }
 private var direction=1;private var closed=false;private var recovering=false
 private var foreground=true;private var resumeMain=false;private var resumePosition=0L;private var pendingPosition:Long?=null
 private val recovery get()=playbackRecovery.liveRoutes
 private val replayRecovery get()=playbackRecovery.replayRoutes
 private fun replayRoutes()=current.playbackRoutes.indices.filter{replay?.let{p->replayAddress(current.playbackRoutes[it],p,System.currentTimeMillis())!=null}==true}
 private val exhausted get()=playbackRecovery.exhausted
 private fun decoderKey(channel:Channel=current,index:Int=routeIndex)=channel.id+":"+routeKey(channel,index)+":"+(replay?.start?:0)
 private fun makePlayer():ExoPlayer {
  val historyRoute=if(decoderContext.isBlank())null else routeKey(current,routeIndex)
  val selector=androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
  val softwareMime=decoderPreferences.videoSoftwareMime
  if(softwareMime!=null)selector.setParameters(selector.buildUponParameters()
   .setMaxVideoSize(1920,1080).setMaxVideoFrameRate(if(softwareMime in setOf("video/hevc","video/av01","video/x-vnd.on2.vp9"))30 else 60)
   .setExceedVideoConstraintsIfNecessary(false))
  return ExoPlayer.Builder(context,DecoderCompatibility.renderers(context,decoderPreferences.copy(discouragedVideoNames=historyRoute?.let{decoderHistory.discouraged(it)}?:emptySet())){mime->historyRoute?.let{decoderHistory.preferred(it,mime)}}).setTrackSelector(selector)
   .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
   .setHandleAudioBecomingNoisy(true).setPlaybackLooper(preloadPool.looper).setLoadControl(preloadPool.loadControl).build()
 }
 private fun retryDecoder(preferences:DecoderPreferences){
  val autoplay=player.playWhenReady
  decoderPreferences=preferences;pendingPosition=if(replay!=null)player.currentPosition.coerceAtLeast(0)else null
  attempt(current,routeIndex,autoplay);if(!autoplay){timeoutJob?.cancel();recoveryBudget.pause()}
 }
 private fun trySoftwareVideo(format:Format):Boolean {
  val key=decoderKey()
  if(recoveryBudget.remaining(stallTimeoutMillis)<=0||manualDecoderMode!="auto"||softwareDecoder||decoderPreferences.videoSoftwareMime!=null||key in softwareAttempts)return false
  if(!softwareVideoBudget(format.sampleMimeType,format.width,format.height,format.frameRate,resources.memoryClass,Runtime.getRuntime().availableProcessors(),format.drmInitData!=null||format.cryptoType!=C.CRYPTO_TYPE_NONE)||!DecoderCompatibility.softwareSupports(context,format))return false
  softwareAttempts.add(key);retryDecoder(decoderPreferences.copy(videoSoftwareMime=format.sampleMimeType));return true
 }
 private fun tryDecoderRecovery(error:PlaybackException):Boolean {
  if(closed||!foreground||recovering||offline())return false
  recoveryBudget.enter(current.id)
  val format=(error as? ExoPlaybackException)?.rendererFormat?:return false
  val mime=format.sampleMimeType?:return false
  val decode=error.errorCode in setOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,PlaybackException.ERROR_CODE_DECODING_FAILED,PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
  if(mime.startsWith("video/")&&decode){
   decoderHistory.failure(routeKey(current,routeIndex),decoderName);videoFailedRoutes+=routeIndex
   val eligible=if(replay!=null)replayRoutes()else current.playbackRoutes.indices.toList()
   if(eligible.any{it !in videoFailedRoutes})return false
   return trySoftwareVideo(format)
  }
  if(!mime.startsWith("audio/"))return false
  val output=error.errorCode==PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED||error.errorCode==PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
  if(!decode&&!output)return false
  return tryAudioDecoder(format,output)
 }
 private fun tryAudioDecoder(format:Format,output:Boolean):Boolean {
  if(recoveryBudget.remaining(stallTimeoutMillis)<=0||format.drmInitData!=null||format.cryptoType!=C.CRYPTO_TYPE_NONE)return false
  val mime=format.sampleMimeType?:return false
  val key=decoderKey()
  if(output&&!decoderPreferences.forcePcm&&audioRecoveryAttempts.add(key+":pcm")){retryDecoder(decoderPreferences.copy(forcePcm=true));return true}
  if(mime in setOf("audio/mpeg-L1","audio/mpeg-L2")&&!decoderPreferences.preferMp2Software&&audioRecoveryAttempts.add(key+":mp2")){retryDecoder(decoderPreferences.copy(preferMp2Software=true));return true}
  if(!audioSoftwareDecoder&&decoderPreferences.audioSoftwareMime==null&&DecoderCompatibility.softwareSupports(context,format)&&audioRecoveryAttempts.add(key+":software")){retryDecoder(decoderPreferences.copy(audioSoftwareMime=mime));return true}
  return false
 }

 private fun listen(target:ExoPlayer,serial:Int)=object:Player.Listener {
  private fun valid()=target===player&&!closed&&serial==tuneSerial
  override fun onPlaybackStateChanged(state:Int){if(!valid())return;ready=state==Player.STATE_READY
   if(ready){if(rendered){timeoutJob?.cancel();recoveryBudget.pause()};loadingJob?.cancel();loading=false;if(!recovering&&target.playWhenReady)error=""}
   if(state==Player.STATE_BUFFERING){stableJob?.cancel();if(foreground&&target.playWhenReady)watch(serial)}
   if(ready&&rendered){stableJob?.cancel();stableJob=scope.launch{delay(30000);if(valid()&&target.isPlaying){exhausted.clear();recoveryBudget.reset();videoFailedRoutes.clear();recovery.reset(routeIndex);replayRecovery.reset(replayRoutes().indexOf(routeIndex))}}}
   if(state==Player.STATE_ENDED){if(replay!=null)returnLive()else failWithReason("LIVE_STREAM_ENDED","直播流已结束",serial)}
  }
  override fun onRenderedFirstFrame(){if(!valid())return;rendered=true;surfaceHasFrame=true;recoveryBudget.pause();lastVideoFormat=target.videoFormat;target.videoFormat?.let{decoderHistory.success(routeKey(current,routeIndex),it.sampleMimeType.orEmpty(),decoderName)};lastGoodRoutes[current.id]=routeKey(current,routeIndex);preloadPool.releaseBorrowed();firstFrameMs=android.os.SystemClock.elapsedRealtime()-tuneStarted;timeoutJob?.cancel();loadingJob?.cancel();loading=false;stableJob?.cancel();stableJob=scope.launch{delay(30000);if(valid()&&target.isPlaying){exhausted.clear();recoveryBudget.reset();videoFailedRoutes.clear();recovery.reset(routeIndex);replayRecovery.reset(replayRoutes().indexOf(routeIndex))}};warmJob?.cancel();warmJob=scope.launch{delay(500);warm()}}
  override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int){if(!valid())return
   if(!playWhenReady){timeoutJob?.cancel();recoveryBudget.pause()}
   else if(foreground&&(!ready||!rendered)){recoveryBudget.enter(current.id);watch(serial)}
  }
  override fun onTracksChanged(tracks:Tracks){if(valid()){
   if(foreground&&!recovering&&!offline()){
    val video=unsupportedTrackFormat(tracks,C.TRACK_TYPE_VIDEO)
    if(video!=null){videoFailedRoutes+=routeIndex;if(!((if(replay!=null)replayRoutes()else current.playbackRoutes.indices.toList()).all{it in videoFailedRoutes}&&trySoftwareVideo(video))){failWithReason("UNSUPPORTED_VIDEO "+video.sampleMimeType,"当前设备暂无可用视频解码器",serial)};return}
    unsupportedAudioFormat(tracks)?.let{format->if(!tryAudioDecoder(format,false)){failWithReason("UNSUPPORTED_AUDIO "+format.sampleMimeType,"当前设备暂无可用音轨解码器",serial)}}
   }
  }}
  override fun onPlayerError(e:PlaybackException){if(valid()){
   val http=generateSequence<Throwable>(e){it.cause}.take(8).filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
   val message=playbackFailureMessage(e,e.errorCode)
   recordFailure(e.errorCodeName+(http?.let{" HTTP $it"}?:""),message)
   if(!tryDecoderRecovery(e))failed(message,serial)
  }}
 }
 init{
  network.register(networkCallback)
  active=this
  app.channels.onChannelsChanged={scope.launch{updateChannelRoutes()}}
  tune(initial)
  rateJob=scope.launch{while(isActive){delay(1000);bytesPerSecond=synchronized(this@PlaybackSession){val now=android.os.SystemClock.elapsedRealtime();val b=byteCount;byteCount=0;val rate=transferWindow.add(b,now-rateSampleAt);rateSampleAt=now;rate}}}
 }
 private fun updateChannelRoutes(){
  if(closed)return
  val route=current.playbackRoutes.getOrNull(routeIndex)
  val channels=app.channels.channels
  fun routeIndexIn(channel:Channel)=channel.playbackRoutes.indexOfFirst { it.url==route?.url && it.sourceId==route?.sourceId }
  val sameChannel=channels.firstOrNull{it.id==current.id}
  val withCurrentRoute=sameChannel?.takeIf{routeIndexIn(it)>=0}
   ?:channels.firstOrNull{channel->channel.playbackRoutes.any{it.originalId==route?.originalId}&&routeIndexIn(channel)>=0}
  val previousIds=current.playbackRoutes.mapTo(hashSetOf()){it.originalId}
  val updated=sameChannel ?: channels.firstOrNull { channel ->
   channel.id in previousIds || channel.playbackRoutes.any { it.originalId in previousIds }
  }
  if(withCurrentRoute!=null){
   val index=routeIndexIn(withCurrentRoute)
   val routesChanged=current.playbackRoutes!=withCurrentRoute.playbackRoutes
   val activeRouteChanged=current.id!=withCurrentRoute.id||index!=routeIndex||
    withCurrentRoute.playbackRoutes[index]!=route
   current=withCurrentRoute;routeIndex=index
   if(routesChanged){
    warmJob?.cancel();clearWarm()
    if(activeRouteChanged||(recovering&&retryJob?.isActive==true)){
     val autoplay=if(waitingForNetwork)requestedAutoplay else if(foreground)player.playWhenReady else resumeMain
     if(replay!=null&&!waitingForNetwork)pendingPosition=player.currentPosition
     restartCurrentRoute(autoplay)
     return
    }
    videoFailedRoutes.clear()
    recovery.reset(index)
    replayRecovery.reset(replayRoutes().indexOf(index))
    if(rendered&&foreground)warmJob=scope.launch{warm()}
   }
  }else if(updated!=null)tune(updated)
  else if(route!=null&&app.channels.sources.none{it.id==route.sourceId&&it.enabled}){
   clearWarm()
   val next=channels.firstOrNull()
   if(next!=null)tune(next)
   else{retryJob?.cancel();prepareJob?.cancel();timeoutJob?.cancel();player.stop();error="没有已启用的频道"}
  }
 }
 private fun watch(serial:Int){
  timeoutJob?.cancel()
  if(rendered)recoveryBudget.reset()
  recoveryBudget.enter(current.id)
  val wait=minOf((stallTimeoutMillis/2).coerceAtLeast(1),12000L,recoveryBudget.remaining(stallTimeoutMillis))
  if(foreground)timeoutJob=scope.launch{delay(wait);if(serial==tuneSerial&&foreground&&(!ready||(!rendered&&player.currentTracks.groups.any{it.type==C.TRACK_TYPE_VIDEO}))&&player.playWhenReady){
   failWithReason("FIRST_FRAME_OR_BUFFER_TIMEOUT","等待首帧或缓冲超时",serial)
  }}
 }
 fun applyPreloadSetting(){warmJob?.cancel();clearWarm();preloader.clearFailures();warmJob=scope.launch{warm()}}
 fun playReplay(channel:Channel,program:Program):Boolean{recoveryBudget.reset();videoFailedRoutes.clear();val i=preferredReplayRoute(channel,program,System.currentTimeMillis());if(i<0)return false;resumePosition=0;pendingPosition=null;replay=program;current=channel;replayRecovery.reset(replayRoutes().indexOf(i));exhausted.clear();recovery.reset(i);attempt(channel,i);return true}
 fun returnLive(){replay=null;tune(current)}
 fun tune(channel:Channel){direction=1;tuneChannel(channel)}
 private fun tuneChannel(channel:Channel){if(closed)return;recoveryBudget.reset();videoFailedRoutes.clear();if(channel.id!=current.id){manualDecoderMode="auto";manualDecoderMime=null;lastVideoFormat=null};recentFailures.clear();if(channel.id!=current.id){softwareAttempts.clear();audioRecoveryAttempts.clear()};replay=null;pendingPosition=null;resumePosition=0;exhausted.clear()
  val index=preferredRoute(channel)
  recovery.reset(index);attempt(channel,index)
 }
 private fun preferredRoute(channel:Channel):Int =
  preloadPool.routeIndex(channel.id)?.takeIf{it in channel.playbackRoutes.indices}
   ?:channel.playbackRoutes.indices.firstOrNull{routeKey(channel,it)==lastGoodRoutes[channel.id]}
   ?:channel.playbackRoutes.indices.firstOrNull{decoderHistory.hasSuccess(routeKey(channel,it))}
   ?:0
 fun chooseRoute(index:Int):Boolean{
  if(index !in current.playbackRoutes.indices||index==routeIndex)return false
  recoveryBudget.reset();videoFailedRoutes.clear()
  if(replay!=null&&replayAddress(current.playbackRoutes[index],replay!!,System.currentTimeMillis())==null)return false
  if(replay!=null){pendingPosition=player.currentPosition.coerceAtLeast(0);replayRecovery.reset(replayRoutes().indexOf(index))};exhausted.clear();recovery.reset(index);attempt(current,index);return true
 }
 fun switchRoute(delta:Int):Boolean{
  if(routeCount<2)return false
  val next=(1 until routeCount).map{(routeIndex+delta*it+routeCount*it)%routeCount}.firstOrNull{replay==null||replayAddress(current.playbackRoutes[it],replay!!,System.currentTimeMillis())!=null}?:return false
  return chooseRoute(next)
 }
 private fun attempt(channel:Channel,index:Int,autoplay:Boolean=true){
  if(closed)return
  retuneJob?.cancel();requestedAutoplay=autoplay
  timeoutJob?.cancel();retryJob?.cancel();prepareJob?.cancel();stableJob?.cancel()
  current=channel;routeIndex=index;ready=false;rendered=false;tuneSerial++
  if(beforeRetune!=null&&foreground){
   rendered=false
   retuneJob=scope.launch(start=CoroutineStart.UNDISPATCHED){beforeRetune?.invoke();ensureActive();if(!closed&&foreground)attemptPrepared(channel,index,autoplay)}
  }else attemptPrepared(channel,index,autoplay)
 }
 private fun attemptPrepared(channel:Channel,index:Int,autoplay:Boolean=true){
  if(closed)return
  if(!foreground){current=channel;routeIndex=index;return}
  if(offline()){
   if(activePlayer==null)activePlayer=makePlayer()
   if(replay!=null&&pendingPosition==null)pendingPosition=resumePosition
   waitForNetwork()
   return
  }
  waitingForNetwork=false
  recoveryBudget.enter(channel.id)
  val nextDecoderContext=decoderKey(channel,index)
  if(nextDecoderContext!=decoderContext){decoderContext=nextDecoderContext;decoderPreferences=modePreferences()}
  stableJob?.cancel();retryJob?.cancel();prepareJob?.cancel();warmJob?.cancel();loadingJob?.cancel();timeoutJob?.cancel();recovering=false
  synchronized(this){byteCount=0;transferWindow.clear();rateSampleAt=android.os.SystemClock.elapsedRealtime()};bytesPerSecond=0
  current=channel;routeIndex=index;app.prefs.lastChannelId=channel.playbackRoutes.getOrNull(index)?.originalId?:channel.id;error="";ready=false;rendered=false;loading=false
  val serial=tuneSerial
  tuneStarted=android.os.SystemClock.elapsedRealtime();firstFrameMs=0
  val old=activePlayer
  val cached=if(replay==null&&decoderPreferences.defaultSelection)preloadPool.take(channel.id,index)else null
  val target=makePlayer()
  activePlayer=target;surfaceHasFrame=false
  if(cached?.ready==true)warmHits++ else warmMisses++
  clearWarm()
  old?.release();preloadPool.releaseBorrowed();decoderName="";softwareDecoder=false;audioDecoderName="";audioSoftwareDecoder=false
  target.addAnalyticsListener(object:androidx.media3.exoplayer.analytics.AnalyticsListener {
   override fun onAudioDecoderInitialized(eventTime:androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,name:String,initializedTimestampMs:Long,initializationDurationMs:Long){
    if(target!==player||serial!=tuneSerial)return
    audioDecoderName=name
    audioSoftwareDecoder=name=="Mp2Decoder"||knownSoftwareDecoder(name,false)||target.audioFormat?.sampleMimeType?.let{mime->runCatching{androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime,false,false).firstOrNull{it.name==name}?.softwareOnly}.getOrNull()}==true
   }
   override fun onVideoDecoderInitialized(eventTime:androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,name:String,initializedTimestampMs:Long,initializationDurationMs:Long){
    if(target!==player||serial!=tuneSerial)return
    decoderName=name
    softwareDecoder=knownSoftwareDecoder(name,false)||target.videoFormat?.sampleMimeType?.let{mime->runCatching{androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime,false,false).firstOrNull{it.name==name}?.softwareOnly}.getOrNull()}?:name.startsWith("c2.android.")||name.startsWith("OMX.google.")
   }
  })
  target.addListener(listen(target,serial));target.volume=1f;target.playWhenReady=foreground&&autoplay
  loadingJob=scope.launch{delay(500);if(serial==tuneSerial&&!ready)loading=true}
  watch(serial)
  if(cached!=null){preloadPool.adopt(cached);target.setMediaSource(cached.source);target.prepare();target.playWhenReady=foreground&&autoplay;return}
  prepareJob=scope.launch{
   try{val media=sourceFactory.create(channel,index,replay);ensureActive();if(serial==tuneSerial&&!closed){val position=pendingPosition;pendingPosition=null;if(position!=null)target.setMediaSource(media,position)else target.setMediaSource(media);target.prepare();target.playWhenReady=foreground&&autoplay}}
   catch(e:CancellationException){throw e}catch(e:Exception){failWithReason(e.javaClass.simpleName,playbackFailureMessage(e),serial)}
  }
 }
 private fun failed(message:String,serial:Int){
  if(closed||serial!=tuneSerial||recovering||!foreground)return
  if(offline()){
   if(replay!=null)pendingPosition=pendingPosition?:player.currentPosition.coerceAtLeast(resumePosition)
   waitForNetwork()
   return
  }
  stableJob?.cancel();recoveryBudget.enter(current.id)
  if(replay!=null){
   recovering=true;loadingJob?.cancel();timeoutJob?.cancel();loading=false
   resumePosition=player.currentPosition.coerceAtLeast(resumePosition);pendingPosition=resumePosition
   val eligible=replayRoutes();val next=if(recoveryBudget.remaining(stallTimeoutMillis)<=0)null else replayRecovery.next(eligible.size,eligible.indexOf(routeIndex))?.let{eligible[it]}
   if(next==null){error="回放暂不可用，可在快捷菜单中返回直播\n$message";return}
   val retry=next;retryJob=scope.launch{delay(1000);if(!closed&&serial==tuneSerial&&foreground){if(offline())waitForNetwork()else attempt(current,retry)}};return
  }
  recovering=true;ready=false;loadingJob?.cancel();timeoutJob?.cancel();loading=false
  val nextRoute=if(recoveryBudget.remaining(stallTimeoutMillis)<=0)null else recovery.next(routeCount,routeIndex)
  val channel=current
  val nextChannel=if(nextRoute==null){exhausted+=channel.id;val all=app.channels.channels;val at=all.indexOfFirst{it.id==channel.id};val remaining=PreloadPolicy.indices(all.size,at,direction).map(all::get).filterNot{it.id in exhausted};remaining.firstOrNull{!preloader.confirmedUnavailable(it)}?:remaining.firstOrNull()}else null
  if(nextRoute==null&&nextChannel==null){player.pause();error="暂无可用频道\n$message";return}
  retryJob=scope.launch{
   delay(if(nextRoute!=null&&routeCount==1)1000 else 300)
   if(closed||serial!=tuneSerial||!foreground)return@launch
   if(offline()){waitForNetwork();return@launch}
   if(nextRoute!=null)attempt(channel,nextRoute)
   else {recoveryBudget.reset();videoFailedRoutes.clear();manualDecoderMode="auto";manualDecoderMime=null;lastVideoFormat=null;val target=nextChannel!!;val index=preferredRoute(target);recovery.reset(index);attempt(target,index)}
  }
 }
 fun seekReplay(position:Long){if(replay==null)return;val duration=player.duration.takeIf{it>0}?:((replay!!.stop-replay!!.start).coerceAtLeast(1));player.seekTo(position.coerceIn(0,(duration-1000).coerceAtLeast(0)));resumePosition=player.currentPosition}
 fun zap(delta:Int) {
  val channels=app.channels.channels
  if(channels.isEmpty()||delta==0)return
  direction=if(delta<0)-1 else 1
  val index=channels.indexOfFirst{it.id==current.id}
  val candidates=PreloadPolicy.indices(channels.size,index,direction,PreloadPolicy.MAX_CHANNELS).map(channels::get)
  val target=candidates.firstOrNull{!preloader.confirmedUnavailable(it)}?:candidates.firstOrNull()
   ?:channels.getOrNull(index)?:channels.first()
  tuneChannel(target)
 }
 private suspend fun warm(){
  val serial=tuneSerial
  preloader.warm(current.id){serial==tuneSerial&&!closed&&foreground&&replay==null&&!offline()}
 }
 data class DecoderChoice(val id:String,val label:String,val detail:String,val enabled:Boolean=true)
 val selectedDecoderId get()=manualDecoderMode
 private fun modePreferences()=DecoderPreferences(videoHardwareOnly=manualDecoderMode=="hardware",videoSoftwareMime=manualDecoderMime.takeIf{manualDecoderMode=="software"})
 fun decoderChoices()=videoDecoderChoices(context,player.videoFormat?:lastVideoFormat,resources.memoryClass)
 val decoderAutomaticDescription get()="优先硬解，按视频自动匹配"
 fun selectDecoder(id:String):Boolean{
  if(id!="auto"&&decoderChoices().none{it.id==id&&it.enabled})return false
  if(id==selectedDecoderId)return false
  val format=player.videoFormat?:lastVideoFormat
  manualDecoderMode=id;manualDecoderMime=if(id=="software")format?.sampleMimeType else null
  recoveryBudget.reset();videoFailedRoutes.clear();softwareAttempts.clear();audioRecoveryAttempts.clear();recovery.reset(routeIndex);replayRecovery.reset(replayRoutes().indexOf(routeIndex))
  retryDecoder(modePreferences());return true
 }
 data class AudioChoice(val id:String,val label:String,val detail:String,val selected:Boolean,val supported:Boolean,val group:Tracks.Group,val index:Int)
 fun audioChoices()=playerAudioChoices(player.currentTracks)
 val automaticAudio:Boolean get()=player.trackSelectionParameters.overrides.values.none{it.type==C.TRACK_TYPE_AUDIO}
 fun selectAutomaticAudio(){player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()}
 fun selectAudio(group:Tracks.Group,index:Int){player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,listOf(index))).build()}
 fun setForeground(value:Boolean){if(foreground==value||closed)return;foreground=value
  if(!value){val pendingRetune=retuneJob?.isActive==true;retuneJob?.cancel();stableJob?.cancel();resumeMain=if(pendingRetune||waitingForNetwork)requestedAutoplay else player.playWhenReady;if(!pendingRetune&&replay!=null&&player.currentPosition>0)resumePosition=player.currentPosition;if(LaunchPolicy.resetOnReturn(context))player.stop()else player.pause();prepareJob?.cancel();retryJob?.cancel();recovering=false;clearWarm();preloader.clearFailures();warmJob?.cancel();timeoutJob?.cancel()}
  else if(waitingForNetwork)resumeAfterNetworkWait()
  else{recoveryBudget.reset();if(player.playbackState==Player.STATE_IDLE||player.playerError!=null||(!ready&&!rendered)){pendingPosition=if(replay!=null)resumePosition else null;attempt(current,routeIndex,resumeMain)}else if(resumeMain){player.play();if(!ready)watch(tuneSerial);warmJob=scope.launch{delay(500);warm()}}}
 }
 fun release(){retuneJob?.cancel();network.unregister(networkCallback);stableJob?.cancel();if(active===this){active=null;app.channels.onChannelsChanged=null};closed=true;rateJob?.cancel();loadingJob?.cancel();timeoutJob?.cancel();prepareJob?.cancel();warmJob?.cancel();retryJob?.cancel();player.release();clearWarm();preloadPool.release()}
}
