// SPDX-License-Identifier: GPL-3.0-only
// Next TV — player controls (Compose for TV).
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.media.AudioManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.media3.common.C
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.nexttv.core.App
import com.nexttv.core.data.Channel
import com.nexttv.core.data.programmeProgress
import com.nexttv.core.data.playbackFraction
import com.nexttv.core.ui.Ui
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable fun PlayerScreen(initial:Channel,onClose:(Channel)->Unit,onHome:((Channel)->Unit)?=null,onLaunchReady:(()->Unit)?=null){
 val context=LocalContext.current;val scope=rememberCoroutineScope();val app=App.get()
 val engine=remember{PlaybackSession(context,initial,scope)}
 val launchReady=engine.rendered||engine.error.isNotBlank()||
  (engine.ready&&engine.player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO)&&
   !engine.player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
 if(launchReady&&onLaunchReady!=null)SideEffect(onLaunchReady)
 androidx.activity.compose.ReportDrawnWhen{engine.rendered}
 val retained=remember{RetainedVideoFrame()}
 val frameOpacity=remember{androidx.compose.animation.core.Animatable(1f)}
 val revealOpacity=remember{androidx.compose.animation.core.Animatable(1f)}
 DisposableEffect(engine,context){val activity=context as TvActivity;activity.videoFrameDiagnostics={retained.diagnostics().put("visible",retained.bitmap!=null&&app.prefs.keepLastFrame).put("opacity",frameOpacity.value).put("captureVersion",retained.captureVersion).put("surfaceHasFrame",engine.surfaceHasFrame).put("blackCover",videoTransitionNeedsCover(app.prefs.keepLastFrame,retained.bitmap!=null,engine.surfaceHasFrame,engine.rendered)&&revealOpacity.value>.01f)};onDispose{activity.videoFrameDiagnostics=null}}
 DisposableEffect(engine){engine.beforeRetune={
  if(app.prefs.keepLastFrame&&(engine.surfaceHasFrame||retained.bitmap!=null)){
   val fresh=engine.surfaceHasFrame
   if(fresh||retained.bitmap==null){
    val captureVersion=retained.captureVersion
    retained.capture(replace=fresh){!engine.rendered&&app.prefs.keepLastFrame}
    withTimeoutOrNull(80){snapshotFlow{retained.captureVersion>captureVersion}.first{it}}
    if(retained.captureVersion>captureVersion)frameOpacity.snapTo(1f)
    else if(fresh)retained.clear() // Never revive an older channel's fading snapshot.
   }
   if(retained.bitmap!=null){withFrameNanos{};withFrameNanos{}}
  }else retained.clear()
 };onDispose{engine.beforeRetune=null;retained.dispose()}}
 LaunchedEffect(engine.tuneSerial,engine.rendered,app.prefs.keepLastFrame,app.prefs.reduceMotion){
  val fadeSerial=engine.tuneSerial
  if(!engine.rendered){revealOpacity.snapTo(1f);if(!app.prefs.keepLastFrame)retained.clear()}
  else{
   withFrameNanos{};withFrameNanos{}
   if(app.prefs.reduceMotion){frameOpacity.snapTo(0f);revealOpacity.snapTo(0f)}
   else coroutineScope{
    launch{frameOpacity.animateTo(0f,androidx.compose.animation.core.tween(180,easing=androidx.compose.animation.core.LinearOutSlowInEasing))}
    launch{revealOpacity.animateTo(0f,androidx.compose.animation.core.tween(140,easing=androidx.compose.animation.core.LinearOutSlowInEasing))}
   }
   if(engine.rendered&&engine.tuneSerial==fadeSerial)retained.clear()
  }
 }
 val digitCapacity=remember(app.channels.revision){channelDigitCapacity(app.channels.channels.maxOfOrNull{it.number}?:0)}
 val browserController=remember{BrowserController()}
 val root=remember{FocusRequester()};val actionFocus=remember{FocusRequester()}
 var bars by remember{mutableStateOf(true)};var actionsVisible by remember{mutableStateOf(false)};var side by remember{mutableStateOf(false)}
 var actionFocused by remember{mutableStateOf(false)};var group by remember(engine.current.id,engine.current.group){mutableStateOf<String?>(engine.current.group.takeIf{it.isNotBlank()})}
 var dialog by remember{mutableStateOf("")};var notice by remember{mutableStateOf("")};var digits by remember{mutableStateOf("")}
 var tick by remember{mutableLongStateOf(System.currentTimeMillis())};var deadline by remember{mutableLongStateOf(System.currentTimeMillis()+InteractionStyle.infoMillis)}
 var requestActions by remember{mutableIntStateOf(0)};var sideGeneration by remember{mutableIntStateOf(0)};var digitGeneration by remember{mutableIntStateOf(0)}
 var exitNoticeEpoch by remember{mutableIntStateOf(0)}
 var seekFeedback by remember{mutableStateOf<ReplaySeekFeedback?>(null)}
 var seekEpoch by remember{mutableIntStateOf(0)}
 var bottomPanelHeight by remember{mutableIntStateOf(0)}
 val touchDragOrigin = remember(engine.current.id, engine.replay?.start) { longArrayOf(0L) }
 fun seek(delta:Long,multiplier:Int=1,chain:Boolean=false){
  val replay=engine.replay?:return
  val duration=engine.player.duration.takeIf{it>0}?:((replay.stop-replay.start).coerceAtLeast(1))
  val previous=seekFeedback
  val position=if(chain&&previous!=null&&(previous.delta>0)==(delta>0))previous.target else engine.player.currentPosition
  val target=replaySeekTarget(position,delta,duration)
  val effective=target-position
  if(effective==0L){notice=if(delta<0)"已到节目开头"else"已到节目结尾";return}
  val accumulated=if(previous!=null&&(previous.delta>0)==(delta>0))previous.delta+effective else effective
  engine.seekReplay(target);seekFeedback=ReplaySeekFeedback(target,duration,accumulated,multiplier);seekEpoch++
  if(!actionsVisible){bars=false;side=false;runCatching{root.requestFocus()}}
 }
 val replayHold=remember(engine.current.id,engine.replay?.start){longArrayOf(0L,0L,0L)}
 fun seekStep(direction:Int,repeated:Boolean){
  val now=android.os.SystemClock.elapsedRealtime()
  if(!repeated||replayHold[0]!=direction.toLong()){
   replayHold[0]=direction.toLong();replayHold[1]=now;replayHold[2]=now
   seek(direction*REPLAY_SEEK_STEP_MS,1)
   return
  }
  if(now-replayHold[2]<REPLAY_SEEK_HOLD_INTERVAL_MS)return
  val multiplier=replayHoldMultiplier(now-replayHold[1])
  replayHold[2]=now
  seek(direction*REPLAY_SEEK_STEP_MS*multiplier,multiplier,true)
 }
 LaunchedEffect(seekEpoch){if(seekFeedback!=null){delay(2200);seekFeedback=null}}
 LaunchedEffect(engine.current.id,engine.replay?.start){seekFeedback=null}

 val exitGate=remember{ExitConfirmation()}
 var selectedProgram by remember{mutableStateOf<com.nexttv.core.data.Program?>(null)}
 var consumeOkUp by remember{mutableStateOf(false)}
 var longOk by remember{mutableStateOf(false)}
 fun cancelPendingDigits(){digits="";digitGeneration++}
 fun showBars(focus:Boolean=false){cancelPendingDigits();side=false;bars=true;actionsVisible=focus;deadline=System.currentTimeMillis()+if(focus)InteractionStyle.actionsMillis else InteractionStyle.infoMillis;if(focus)requestActions++}
 fun hideBars(){bars=false;actionsVisible=false;actionFocused=false;runCatching{root.requestFocus()}}
 fun openSide(){cancelPendingDigits();browserController.move=null;browserController.pending=0;bars=false;actionsVisible=false;actionFocused=false;group=engine.current.groupName;sideGeneration++;side=true}
 fun perform(action:String){cancelPendingDigits();when(action){
  "选台"->openSide()
  "线路"->dialog="线路选择"
  "解码"->dialog="解码选择"
  "详情"->{
   bars=false;actionsVisible=false;actionFocused=false;notice="";seekFeedback=null
   dialog="播放详情"
  }
  "音轨"->if(engine.audioChoices().isEmpty())notice="暂无可选音轨"else dialog="音轨选择"
  "画面适配"->{engine.aspect=(engine.aspect+1)%3;notice=listOf("适应画面","裁切填充","拉伸全屏")[engine.aspect]}
  "主页"->(onHome?:onClose)(engine.current)
  "播放暂停"->{notice=if(engine.player.togglePlaybackIntent())"继续播放"else"已暂停"}
  "收藏"->{notice=if(app.prefs.toggleFavorite(engine.current))"已添加收藏"else"已取消收藏"}
  "返回直播"->{engine.returnLive();hideBars()}
 };deadline=System.currentTimeMillis()+InteractionStyle.actionsMillis}
 fun dismissOverlays(){cancelPendingDigits();browserController.move=null;browserController.pending=0;dialog="";side=false;bars=false;actionsVisible=false;digits="";notice="";actionFocused=false;exitGate.reset();runCatching{root.requestFocus()}}
 fun back(){
  if(dialog.isNotBlank()||side||bars||digits.isNotBlank())dismissOverlays()
  else if(engine.error.isNotBlank()&&app.prefs.startLive&&onHome!=null)onHome(engine.current)
  else if(exitGate.press(android.os.SystemClock.elapsedRealtime()))onClose(engine.current)
  else{exitNoticeEpoch++;notice="再按一次退出播放"}
 }
 var foregroundUi by remember{mutableStateOf(true)};var remainingDeadline by remember{mutableLongStateOf(0)}
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_PAUSE->{cancelPendingDigits();remainingDeadline=(deadline-System.currentTimeMillis()).coerceAtLeast(0);foregroundUi=false;engine.setForeground(false)};Lifecycle.Event.ON_RESUME->{if(!foregroundUi)deadline=System.currentTimeMillis()+remainingDeadline;foregroundUi=true;engine.setForeground(true)};else->Unit}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);engine.release()}}
 LaunchedEffect(Unit){root.requestFocus();while(true){if(!foregroundUi){delay(500);continue};tick=System.currentTimeMillis();if(bars&&tick>=deadline&&dialog.isBlank())hideBars();delay(250)}}
 LaunchedEffect(engine.tuneSerial){cancelPendingDigits();if(!side&&!actionsVisible&&dialog.isBlank()&&notice!="再按一次退出播放"){bars=true;actionsVisible=false;deadline=System.currentTimeMillis()+InteractionStyle.infoMillis}}
 LaunchedEffect(requestActions){if(requestActions>0){delay(60);runCatching{actionFocus.requestFocus()}}}
 LaunchedEffect(notice,exitNoticeEpoch){if(notice.isNotBlank()){delay(if(notice=="再按一次退出播放")2000 else 1800);notice=""}}
 LaunchedEffect(digitGeneration,foregroundUi,side,actionsVisible,dialog){
  if(foregroundUi&&!side&&!actionsVisible&&dialog.isBlank()&&digits.isNotBlank()){
   val pending=digits;val generation=digitGeneration
   delay(CHANNEL_DIGIT_WAIT_MS)
   if(!foregroundUi||side||actionsVisible||dialog.isNotBlank()||generation!=digitGeneration||pending!=digits)return@LaunchedEffect
   val c=app.channels.channels.firstOrNull{it.number==pending.toIntOrNull()};digits=""
   if(c!=null)engine.tune(c)else notice="没有该频道"
  }
 }
 val latestPlayerBack by rememberUpdatedState<()->Unit>({back()})
 DisposableEffect(context){val activity=context as TvActivity;activity.playerBack={latestPlayerBack()};activity.playerInteraction={if(actionsVisible)deadline=System.currentTimeMillis()+InteractionStyle.actionsMillis;exitGate.reset();if(notice=="再按一次退出播放")notice=""};onDispose{activity.playerBack=null;activity.playerInteraction=null}}
 KeepScreenOnWhilePlayback(engine.player,foregroundUi)
 val gestureHost = context as TvActivity
 SideEffect {
  gestureHost.replayTouchSeek = if (engine.replay != null && !side && !actionsVisible && dialog.isBlank()) {
   { steps, start, finish ->
    val replay = engine.replay
    if (replay != null) {
     if (start) touchDragOrigin[0] = engine.player.currentPosition
     val duration = engine.player.duration.takeIf { it > 0 } ?: (replay.stop - replay.start).coerceAtLeast(1)
     val origin = touchDragOrigin[0]
     val target = replaySeekTarget(origin, steps * REPLAY_SEEK_STEP_MS, duration)
     seekFeedback = ReplaySeekFeedback(target, duration, target - origin)
     seekEpoch++
     if (finish && target != origin) engine.seekReplay(target)
    }
   }
  } else null
 }
 DisposableEffect(gestureHost) { onDispose { gestureHost.replayTouchSeek = null } }
 BackHandler{back()}
 Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(root).onPreviewKeyEvent { event->
  val key=TvKey(event.nativeKeyEvent)
  if(dialog.isNotBlank())return@onPreviewKeyEvent false
  if(key.up&&consumeOkUp&&key.action==InputAction.CONFIRM){consumeOkUp=false;if(!longOk)openSide();longOk=false;return@onPreviewKeyEvent true}
  if(!key.down){if(key.action==InputAction.LEFT||key.action==InputAction.RIGHT||key.action==InputAction.SEEK_FORWARD||key.action==InputAction.SEEK_BACKWARD)replayHold[0]=0;return@onPreviewKeyEvent false}
  if(key.escape){back();return@onPreviewKeyEvent true}
  if(key.action==InputAction.MENU){showBars(true);return@onPreviewKeyEvent true}
  if(key.action==InputAction.INFO){if(!bars)showBars(false);return@onPreviewKeyEvent true}
  if(consumeOkUp&&key.action==InputAction.CONFIRM){if(key.repeated&&!longOk){longOk=true;showBars(true)};return@onPreviewKeyEvent true}
  if(side){
  val horizontal=key.horizontal
  val resumed=(horizontal!=null||key.vertical)&&InteractionStyle.keyboardAfterPointer&&browserController.resumeVisible?.invoke()==true
  if(horizontal!=null){if(resumed)browserController.pending=horizontal else browserController.navigate(horizontal);return@onPreviewKeyEvent true}
  return@onPreviewKeyEvent resumed
 }
  if(actionFocused){deadline=System.currentTimeMillis()+InteractionStyle.actionsMillis;return@onPreviewKeyEvent false}
  if(key.space&&engine.replay!=null){if(!key.repeated)notice=if(engine.player.togglePlaybackIntent())"继续播放"else"已暂停";return@onPreviewKeyEvent true}
  if(key.digit==null)cancelPendingDigits()
  when(key.action){
   InputAction.UP,InputAction.PREVIOUS_CHANNEL->{engine.zap(-1);true}
   InputAction.DOWN,InputAction.NEXT_CHANNEL->{engine.zap(1);true}
   InputAction.LEFT,InputAction.RIGHT->{val direction=key.horizontal!!;if(engine.replay!=null){seekStep((if ((context as TvActivity).pointerNavigation) -direction else direction),key.repeated)}else if(engine.switchRoute(direction))notice="线路 ${engine.routeIndex+1}";true}
   InputAction.CONFIRM->{consumeOkUp=true;longOk=false;true}
   InputAction.PLAY_PAUSE->{notice=if(engine.player.togglePlaybackIntent())"继续播放"else"已暂停";true}
   InputAction.SEEK_FORWARD->{if(engine.replay!=null)seekStep(1,key.repeated);true}
   InputAction.SEEK_BACKWARD->{if(engine.replay!=null)seekStep(-1,key.repeated);true}
   else->{val digit=key.digit;if(digit!=null){if(!key.repeated){digits=appendChannelDigit(digits,digit,digitCapacity);digitGeneration++};true}else false}
  }
 }.focusable()){
  PlaybackVideoSurface(engine.player,engine.aspect,app.prefs.keepLastFrame,retained)
  retained.bitmap?.takeIf{app.prefs.keepLastFrame}?.let{frame->androidx.compose.foundation.Image(frame.asImageBitmap(),null,Modifier.fillMaxSize().graphicsLayer{alpha=frameOpacity.value}.background(Color.Black),contentScale=when(engine.aspect){1->androidx.compose.ui.layout.ContentScale.Crop;2->androidx.compose.ui.layout.ContentScale.FillBounds;else->androidx.compose.ui.layout.ContentScale.Fit})}
  if(videoTransitionNeedsCover(app.prefs.keepLastFrame,retained.bitmap!=null,engine.surfaceHasFrame,engine.rendered))Box(Modifier.fillMaxSize().graphicsLayer{alpha=revealOpacity.value}.background(Color.Black))
  Box(Modifier.fillMaxSize().pointerInput(Unit){awaitPointerEventScope{var secondaryHeld=false;while(true){val event=awaitPointerEvent(PointerEventPass.Initial);if(event.type==PointerEventType.Press&&event.buttons.isSecondaryPressed){secondaryHeld=true;showBars(true)};if(secondaryHeld){event.changes.forEach{it.consume()};if(event.type==PointerEventType.Release)secondaryHeld=false};if(actionsVisible&&(event.type==PointerEventType.Press||event.type==PointerEventType.Scroll))deadline=System.currentTimeMillis()+InteractionStyle.actionsMillis}}}.pointerInput(side,bars){detectTapGestures(onLongPress={showBars(true)},onTap={if(side||actionsVisible)dismissOverlays()else openSide()})})
  if(onLaunchReady==null&&engine.loading&&engine.error.isBlank())Column(Modifier.align(Alignment.Center).padding(horizontal=36.dp,vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally){if(app.prefs.reduceMotion)AppText("◌",fontSize=32.sp,color=androidx.compose.ui.graphics.Color.White)else androidx.compose.material3.CircularProgressIndicator(Modifier.size(42.dp),color=NextTvStyle.Accent);AppText("正在连接…",shadow=androidx.compose.ui.graphics.Shadow(Color.Black,androidx.compose.ui.geometry.Offset(0f,1f),4f),fontSize=InteractionStyle.body,color=androidx.compose.ui.graphics.Color.White,modifier=Modifier.padding(top=12.dp))}
  if(engine.error.isNotBlank())AppText(engine.error,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=NextTvStyle.Error,fontSize=InteractionStyle.body,modifier=Modifier.align(Alignment.Center).padding(horizontal=32.dp).widthIn(max=620.dp).glassReadingPanel(RoundedCornerShape(InteractionStyle.panelRadius)).padding(horizontal=28.dp,vertical=22.dp))
  if(bars&&dialog!="播放详情")PlayerBottomBar(engine,tick,actionsVisible,actionFocus,onActionFocus={actionFocused=it},onAction={perform(it)},modifier=Modifier.align(Alignment.BottomCenter).onSizeChanged{bottomPanelHeight=it.height})
  val clockMode=app.prefs.playerClockMode
  if(!side&&dialog.isBlank()&&digits.isBlank()&&engine.error.isBlank()&&engine.ready&&playerClockVisible(clockMode,tick))PlayerPersistentHud(tick,Modifier.align(Alignment.TopEnd).padding(top=18.dp,end=26.dp))
  if(side)key(engine.current.sourceId,sideGeneration){ChannelSidePanel(engine.current,group,sideGeneration,onGroup={group=it},onSelect={if(it.id!=engine.current.id)engine.tune(it);side=false;root.requestFocus()},controller=browserController,onCloseAll={dismissOverlays()},playingReplay=engine.replay!=null,playingPaused=engine.ready&&!engine.player.playWhenReady,onProgram={c,p->if(engine.playReplay(c,p)){side=false;root.requestFocus()}else if(System.currentTimeMillis() in p.start until p.stop){engine.tune(c);side=false;root.requestFocus()}else if(p.desc.isNotBlank()){selectedProgram=p;dialog="节目详情"}else notice=if(p.start>System.currentTimeMillis())"节目尚未开始"else"该节目暂不支持回放"},modifier=Modifier.align(Alignment.CenterStart))}
  if(!actionsVisible)seekFeedback?.let{ReplaySeekOverlay(it,Modifier.align(Alignment.BottomCenter))}
  if(digits.isNotBlank())AppText(
   digits,modifier=Modifier.align(Alignment.Center),color=Color.White,fontSize=46.sp,
   fontWeight=FontWeight.Bold,shadow=androidx.compose.ui.graphics.Shadow(Color.Black,androidx.compose.ui.geometry.Offset(0f,1f),4f))
  if(notice=="再按一次退出播放")ExitNotice(notice)
  else if(notice.isNotBlank())AppText(notice,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=NextTvStyle.Text,fontSize=InteractionStyle.body,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=if(bars)with(LocalDensity.current){bottomPanelHeight.toDp()}+12.dp else LocalConfiguration.current.screenHeightDp.dp*.12f).glassReadingPanel(RoundedCornerShape(InteractionStyle.radius)).padding(horizontal=18.dp,vertical=12.dp))
 }
 when(dialog){
  "节目详情"->TvDialog(selectedProgram?.title?:"节目信息",{dismissOverlays()}){
   val p=selectedProgram
   Column(Modifier.weight(1f,false).appVerticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
    AppText(p?.let{Ui.hhmm(it.start)+"—"+Ui.hhmm(it.stop)}.orEmpty(),color=NextTvStyle.Muted,fontSize=InteractionStyle.caption)
    AppText(p?.desc?.ifBlank{"暂无节目简介"}.orEmpty(),color=NextTvStyle.Text,fontSize=InteractionStyle.body)
    AppText(if(p!=null&&p.start>System.currentTimeMillis())"节目尚未开始"else "该节目暂无可用回放",color=NextTvStyle.Muted,fontSize=InteractionStyle.caption)
   }
  }
  "播放详情"->PlaybackDetailsDialog(engine,onDismiss={dismissOverlays()})
  "解码选择"->ChoiceDialog("解码方式",listOf(ChoiceOption("auto","自动",engine.decoderAutomaticDescription))+engine.decoderChoices().map{ChoiceOption(it.id,it.label,it.detail,it.enabled)},engine.selectedDecoderId,onDismiss={dismissOverlays()},uniformRows=true){id->val changed=engine.selectDecoder(id);dismissOverlays();if(changed)showBars(false)}
  "线路选择"->RouteChoiceDialog(engine,onDismiss={dismissOverlays()}){id->val changed=engine.chooseRoute(id.toInt());dismissOverlays();if(changed){showBars(false);notice="线路 ${engine.routeIndex+1}"}}
  "音轨选择"->{val tracks=engine.audioChoices();ChoiceDialog("音轨选择",listOf(ChoiceOption("auto","自动","自动选择可用音轨"))+tracks.map{ChoiceOption(it.id,it.label,it.detail,it.supported)},if(engine.automaticAudio)"auto"else tracks.firstOrNull{it.selected}?.id,onDismiss={dismissOverlays()},uniformRows=true){id->if(id=="auto")engine.selectAutomaticAudio()else tracks.firstOrNull{it.id==id}?.let{engine.selectAudio(it.group,it.index)};dismissOverlays()}}
  "画面适配"->ChoiceDialog("画面适配",listOf(ChoiceOption("0","适应画面","保持原始比例，完整显示画面"),ChoiceOption("1","裁切填充","保持比例填满屏幕，可能裁去边缘"),ChoiceOption("2","拉伸全屏","填满屏幕，可能改变画面比例")),engine.aspect.toString(),onDismiss={dismissOverlays()}){id->engine.aspect=id.toInt();dismissOverlays()}
 }
}

@Composable private fun PlayerBottomBar(engine:PlaybackSession,now:Long,actionsVisible:Boolean,action:FocusRequester,onActionFocus:(Boolean)->Unit,onAction:(String)->Unit,modifier:Modifier){
 val app=App.get();val c=engine.current;val pair=app.epg.nowNext(c);val current=engine.replay?:pair.first;val next=if(engine.replay!=null)null else pair.second
 val scale=LocalDensity.current.fontScale
 val replaySeek=remember{FocusRequester()}
 val replayInput=remember(engine.replay?.start){ReplayTimelineInput()}
 val replayActionReturn=remember(engine.replay?.start){arrayOf(action)}
 val identityHeight=56.dp*scale
 val textMeasurer=androidx.compose.ui.text.rememberTextMeasurer()
 val badgeStyle=androidx.tv.material3.LocalTextStyle.current.copy(fontSize=10.sp,fontWeight=FontWeight.Bold,fontFamily=androidx.compose.ui.text.font.FontFamily.SansSerif,lineHeight=14.sp)
 val routeDigits=remember(app.channels.revision){app.channels.channels.maxOfOrNull{it.playbackRoutes.size}?.toString()?.length?:1}
 val density=LocalDensity.current
 val facts=playbackIdentityFacts(engine.routeCount,engine.routeIndex,engine.player.videoFormat?.height?:0,engine.player.audioFormat?.channelCount?:0,engine.bytesPerSecond.takeIf{it>0||engine.player.videoFormat!=null||engine.player.audioFormat!=null})
 val factWidths=remember(textMeasurer,badgeStyle,density,routeDigits,facts){
  val samples=listOf(listOf("8".repeat(routeDigits)+"/"+"8".repeat(routeDigits)),listOf("1080P","1440P","4K"),listOf("立体声","单声道","99声道"),listOf("888.8 MB/s","8888 KB/s","88.8 GB/s"))
  samples.map{sample->with(density){sample.maxOf{textMeasurer.measure(it,badgeStyle).size.width}.toDp()}+13.dp}
 }
 val timelineDuration=engine.player.duration.takeIf{it>0}?:current?.let{it.stop-it.start}?:0L
 BoxWithConstraints(modifier.fillMaxWidth()){
  val panelWidth=maxWidth*playerPanelFraction(maxWidth.value)
  val bottomInset = (maxHeight * .035f).coerceIn(14.dp, 26.dp)
  val infoPadding = (maxHeight * .017f).coerceIn(9.dp, 12.dp)
  Column(Modifier.align(Alignment.BottomCenter).width(panelWidth).padding(bottom=bottomInset).replayTimelineInput(replayInput,engine.replay!=null&&actionsVisible).onFocusChanged{onActionFocus(it.hasFocus)}.focusGroup().playerInfoPanel(RoundedCornerShape(24.dp)).padding(horizontal=20.dp).padding(top=infoPadding,bottom=if(actionsVisible)9.dp else infoPadding)){
   BoxWithConstraints(Modifier.fillMaxWidth()){
    val gap=14.dp
    val logoWidth=identityHeight*1.22f
    val identityWidth=factWidths.fold(0.dp){sum,v->sum+v}+9.dp+logoWidth+8.dp
    val clockWidth=112.dp*scale
    val programmeWidth=maxWidth-identityWidth-clockWidth-gap*2
    val identity:@Composable (Modifier)->Unit={m->
     Row(m.height(identityHeight),verticalAlignment=Alignment.CenterVertically){
      PlayerLogo(c,Modifier.width(logoWidth).height(identityHeight))
      Column(Modifier.weight(1f).height(identityHeight).padding(start=8.dp),verticalArrangement=Arrangement.Center){
       AppText(Ui.displayName(c.name),Modifier.fillMaxWidth().height(27.dp*scale),fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=NextTvStyle.Text,maxLines=1,overflow=TextOverflow.Ellipsis)
       Row(Modifier.padding(top=3.dp*scale).height(20.dp*scale),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){
        val descriptions=listOf("线路 ${engine.routeIndex+1}，共 ${engine.routeCount} 条","分辨率 ${facts[1]}","声道 ${facts[2]}","接收速率 ${facts[3]}")
        facts.forEachIndexed{i,value->Box(Modifier.width(factWidths[i]).fillMaxHeight().semantics(mergeDescendants=true){contentDescription=descriptions[i]},contentAlignment=Alignment.CenterStart){InfoBadge(value,tone=when{i==1&&(value.contains("4K")||value.contains("8K"))->BadgeTone.Warm;i==1->BadgeTone.Cool;i==2&&(engine.player.audioFormat?.channelCount?:0)>2->BadgeTone.Cool;else->BadgeTone.Neutral},modifier=Modifier.fillMaxWidth(),horizontalPadding=if(i==3)0.dp else 6.dp,bordered=i<3)}}
       }
      }
     }
    }
    val programme:@Composable (Modifier)->Unit={m->
     Column(m){
      AppText(current?.title?:"暂无节目信息",Modifier.fillMaxWidth().height(27.dp*scale),fontSize=InteractionStyle.heading,fontWeight=FontWeight.SemiBold,color=NextTvStyle.Text,maxLines=1,overflow=TextOverflow.Ellipsis)
      Row(Modifier.fillMaxWidth().padding(top=3.dp*scale).height(20.dp*scale),verticalAlignment=Alignment.CenterVertically){
       if(current!=null){InfoBadge(if(engine.ready&&!engine.player.playWhenReady)"已暂停"else if(engine.replay!=null)"回放中"else"直播中",tone=BadgeTone.Live);AppText("${Ui.hhmm(current.start)}–${Ui.hhmm(current.stop)}"+(next?.let{" · 接下来 ${Ui.hhmm(it.start)} ${it.title}"}?:""),Modifier.weight(1f).padding(start=7.dp),fontSize=11.sp,color=NextTvStyle.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)}
      }
      val progress=if(engine.replay!=null)playbackFraction(engine.player.currentPosition,timelineDuration)else programmeProgress(current,now)
      if(engine.replay!=null)ReplayTimeline(engine.player.currentPosition,timelineDuration,actionsVisible,replaySeek,replayInput,returnToAction={runCatching{replayActionReturn[0].requestFocus()}},onSeek={engine.seekReplay(it)},modifier=Modifier.fillMaxWidth().padding(top=5.dp).height(3.dp))
      else Box(Modifier.fillMaxWidth().padding(top=5.dp).height(3.dp).background(if(current==null)Color.Transparent else NextTvStyle.PlayerProgressTrack,androidx.compose.foundation.shape.CircleShape)){if(current!=null)Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(NextTvStyle.PlayerProgressFill,androidx.compose.foundation.shape.CircleShape))}
     }
    }
    val clock:@Composable (Modifier)->Unit={m->Column(m.height(identityHeight),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.Center){
      AppText(Ui.hhmm(now),Modifier.fillMaxWidth(),fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=NextTvStyle.Text,maxLines=1,textAlign=androidx.compose.ui.text.style.TextAlign.End)
      AppText("${Ui.weekday(now)} ${Ui.monthDay(now)}",Modifier.fillMaxWidth().padding(top=2.dp*scale),fontSize=11.sp,fontWeight=FontWeight.Medium,color=NextTvStyle.Muted,maxLines=1,textAlign=androidx.compose.ui.text.style.TextAlign.End)
    }}
    if(programmeWidth<200.dp*scale)Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)){
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){identity(Modifier.weight(1f));clock(Modifier.width(clockWidth).padding(start=8.dp))}
     programme(Modifier.fillMaxWidth())
    }else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap),verticalAlignment=Alignment.Top){identity(Modifier.width(identityWidth));programme(Modifier.weight(1f));clock(Modifier.width(clockWidth))}
   }
   if(actionsVisible){
    val activity=LocalActivity.current as TvActivity
    DisposableEffect(activity){onDispose{activity.quickActionsPointerRegion=null}}
    Box(Modifier.fillMaxWidth().padding(top=7.dp).height(1.dp).background(NextTvStyle.PlayerInfoBorder,androidx.compose.foundation.shape.CircleShape))
    Row(Modifier.fillMaxWidth()
     .onGloballyPositioned{activity.quickActionsPointerRegion=it.boundsInWindow()}
     .horizontalScroll(rememberScrollState(),flingBehavior=appFlingBehavior()).padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
     val actions=buildList{if(engine.replay!=null){add("播放暂停");add("返回直播")}else if(engine.routeCount>1)add("线路");add("音轨");add("画面适配");add("收藏");add("解码");add("详情");add("主页")}
     val playing=engine.player.playWhenReady
     val favorite=app.prefs.isFavorite(c)
     actions.forEachIndexed{i,id->key(id){
      val label=when(id){"解码"->"解码方式";"线路"->"线路 ${engine.routeIndex+1}";"播放暂停"->if(playing)"暂停"else"继续播放";"收藏"->if(favorite)"取消收藏"else"添加收藏";"画面适配"->"画面适配";"返回直播"->"返回直播";else->id}
      val description=when(id){"解码"->"解码方式";"线路"->"当前线路 ${engine.routeIndex+1}，共 ${engine.routeCount} 条";"播放暂停"->if(playing)"暂停播放"else"继续播放";"收藏"->if(favorite)"取消收藏"else"添加收藏";"画面适配"->listOf("画面适应","画面填充","画面拉伸")[engine.aspect];"返回直播"->"返回直播";else->id}
      val buttonRequest=if(i==0)action else remember(id){FocusRequester()}
      QuickActionButton(label,description,Modifier.focusRequester(buttonRequest).focusEdges(up=engine.replay==null,down=true,left=i==0,right=i==actions.lastIndex).focusProperties{up=if(engine.replay!=null)replaySeek else FocusRequester.Cancel;down=FocusRequester.Cancel;if(i==0)left=FocusRequester.Cancel;if(i==actions.lastIndex)right=FocusRequester.Cancel},onFocus={replayActionReturn[0]=buttonRequest},onClick={onAction(id)})
     }}
    }
   }
  }
 }
}

@Composable private fun QuickActionButton(label:String,description:String,modifier:Modifier,onFocus:()->Unit,onClick:()->Unit){
 TvSurface(modifier.widthIn(min=76.dp).height(34.dp).border(.75.dp,NextTvStyle.PlayerInfoBorder,RoundedCornerShape(InteractionStyle.badgeRadius)).semantics(mergeDescendants=true){contentDescription=description},kind="quick",onFocus=onFocus,onClick=onClick){
  Box(Modifier.align(Alignment.Center).padding(horizontal=12.dp),contentAlignment=Alignment.Center){
   AppText(label,fontSize=12.5.sp,fontWeight=FontWeight.SemiBold,color=NextTvStyle.Text,maxLines=1,overflow=TextOverflow.Clip,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
  }
 }
}

/** Optional frameless wall clock; visibility is controlled by the Time display menu. */
@Composable private fun PlayerPersistentHud(now:Long,modifier:Modifier){
 AppText(Ui.hhmm(now),modifier.semantics{contentDescription="当前时间 ${Ui.hhmm(now)}"},fontSize=17.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1,shadow=androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha=.82f),androidx.compose.ui.geometry.Offset(0f,1f),5f))
}

private enum class BadgeTone{Neutral,Warm,Cool,Live}

@Composable private fun InfoBadge(value:String,tone:BadgeTone=BadgeTone.Neutral,modifier:Modifier=Modifier,fontSize:androidx.compose.ui.unit.TextUnit=10.sp,horizontalPadding:Dp=6.dp,bordered:Boolean=true){
 val shape=RoundedCornerShape(4.5.dp)
 val fill=when(tone){BadgeTone.Warm->NextTvStyle.SpecWarmFill;BadgeTone.Cool->NextTvStyle.SpecCoolFill;BadgeTone.Live->NextTvStyle.AccentBadge;else->NextTvStyle.SpecNeutralFill}
 val stroke=when(tone){BadgeTone.Warm->NextTvStyle.SpecWarmStroke;BadgeTone.Cool->NextTvStyle.SpecCoolStroke;BadgeTone.Live->NextTvStyle.AccentText.copy(alpha=.45f);else->NextTvStyle.SpecNeutralStroke}
 val ink=when(tone){BadgeTone.Warm->NextTvStyle.SpecWarmText;BadgeTone.Cool->NextTvStyle.SpecCoolText;BadgeTone.Live->NextTvStyle.AccentText;else->NextTvStyle.Muted}
 Box(modifier.height(19.dp*LocalDensity.current.fontScale).then(if(bordered)Modifier.background(fill,shape).border(.75.dp,stroke,shape)else Modifier).padding(horizontal=horizontalPadding),contentAlignment=Alignment.Center){
  AppText(value,fontSize=fontSize,fontWeight=FontWeight.Bold,color=if(bordered)ink else NextTvStyle.Muted,maxLines=1)
 }
}
