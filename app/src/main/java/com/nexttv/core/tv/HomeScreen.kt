// SPDX-License-Identifier: GPL-3.0-only
// Next TV — home layout, visual constants and page flow.
@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nexttv.core.tv

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import com.nexttv.parseNamedSubscriptionAddress
import com.nexttv.R
import com.nexttv.core.App
import com.nexttv.core.data.*
import com.nexttv.core.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.nexttv.observeGridDraws

@Composable fun ChannelArtwork(channel:Channel,modifier:Modifier=Modifier){
 ChannelLogo(channel, modifier)
}
@Composable fun HomeScreen(onExit:()->Unit,onLaunchReady:(()->Unit)?=null){
 val homeCompositionCount=remember{intArrayOf(0)}
 SideEffect{homeCompositionCount[0]++}
 val homeView=LocalView.current
 val homeModal=LocalModalController.current
 val homeConfiguration=androidx.compose.ui.platform.LocalConfiguration.current
 val safeInsets=homeSafeInsets(homeConfiguration.screenWidthDp,homeConfiguration.screenHeightDp)
 val homeHorizontal=maxOf(safeInsets.horizontal,homeConfiguration.screenWidthDp*.05f)
 val app=App.get();val scope=rememberCoroutineScope();val context=LocalContext.current
 val cacheReady=app.cacheReady
 val digitCapacity=remember(cacheReady,app.channels.revision,app.channels.displayRevision,app.prefs.hideUnavailableRoutes){channelDigitCapacity(if(cacheReady)app.channels.displayChannels.maxOfOrNull{it.number}?:0 else 0)}
 val ui = remember {
  HomeUiState(initialPlaybackChannel(app.channels.displayChannels, app.prefs.startLive, app.prefs.lastChannelId))
 }
 var revision by ui.revision
 var tab by ui.tab
 var sources by ui.sources
 var group by ui.group
 var playing by ui.playing
 var status by ui.status
 var channelDigits by ui.channelDigits
 val digitLifecycle=LocalLifecycleOwner.current.lifecycle
 val probeGroup by rememberUpdatedState(group)
 // Runs while the TV is visible, including during playback. The probe itself skips
 // the playing channel and drops to one request at a time, so normal TV use still
 // exercises the recovery path.
 LaunchedEffect(cacheReady,app.channels.revision,app.prefs.hideUnavailableRoutes){
  if(!cacheReady||!app.prefs.hideUnavailableRoutes)return@LaunchedEffect
  RouteHealthProbe.run(app,{probeGroup}){digitLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
 }
 // If every channel in the open group has become unavailable, fall back to
 // "all channels" instead of leaving the user on an empty dead category.
 LaunchedEffect(cacheReady,tab,group,app.channels.displayRevision,app.prefs.hideUnavailableRoutes){
  if(!cacheReady||tab!=0||group.isBlank())return@LaunchedEffect
  // Health updates can arrive in bursts while the fast sweep hides routes.
  // Wait for one settled revision before abandoning the open group, otherwise
  // the UI can jump to "all channels" in the middle of a batch.
  delay(300)
  if(group !in app.channels.displayGroups){
   val old=group
   group=""
   if(app.prefs.selectedGroup==old)app.prefs.selectedGroup=""
  }
 }
 var firstCardFocusPending by ui.firstCardFocusPending
 var addingSubscription by ui.addingSubscription
 val emptyAction=remember{FocusRequester()}
 val inlineBounds=remember{arrayOfNulls<androidx.compose.ui.geometry.Rect>(1)}
 val gridInput=remember{GridInputBookkeeping()}
 var cardInput by ui.cardInput
 var menuChannel by ui.menuChannel
 var dialog by ui.dialog
 var firstFrameReady by ui.firstFrameReady
 val nav=remember{List(3){FocusRequester()}};val first=remember{FocusRequester()}
 var navFocused by ui.navFocused
 var homeNoticeEpoch by ui.homeNoticeEpoch
 var restoringHome by ui.restoringHome
 var homeNotice by ui.homeNotice
 LaunchedEffect(channelDigits,playing,sources,dialog,tab,app.channels.displayRevision,app.prefs.hideUnavailableRoutes){if(playing!=null||sources||dialog.isNotBlank()||tab !in 0..1){channelDigits=""}else if(channelDigits.isNotBlank()){val pending=channelDigits;val pendingRevision=app.channels.revision;delay(CHANNEL_DIGIT_WAIT_MS);if(channelDigits!=pending)return@LaunchedEffect;if(!digitLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)||playing!=null||sources||dialog.isNotBlank()||tab !in 0..1||app.channels.revision!=pendingRevision){channelDigits="";return@LaunchedEffect};val target=app.channels.displayChannels.firstOrNull{it.number==channelDigits.toIntOrNull()};channelDigits="";if(target!=null)playing=target else{homeNotice="没有该频道";homeNoticeEpoch++}}}
 val exitGate=remember{ExitConfirmation()}
 val settingsNavigation=remember{SettingsNavigation()}
 var settingsRestore by ui.settingsRestore
 var returningToTop by ui.returningToTop
 var returnGeneration by ui.returnGeneration
 var returnJob by remember{mutableStateOf<Job?>(null)}
 val returnAnchor=remember{FocusRequester()}
 // Keep a bounded recent viewport behind the cursor; returning/reversing reuses
 // measured cards instead of creating whole rows in a frame callback.
 val gridCacheWindow=remember{HomeGridCacheWindow()}
 val channelGrid=rememberLazyGridState(cacheWindow=gridCacheWindow);val favoriteGrid=rememberLazyGridState(cacheWindow=gridCacheWindow);val grid=if(tab==1)favoriteGrid else channelGrid
 LaunchedEffect(channelGrid,favoriteGrid){
  launch{snapshotFlow{channelGrid.isScrollInProgress}.first{it};gridCacheWindow.activate()}
  launch{snapshotFlow{favoriteGrid.isScrollInProgress}.first{it};gridCacheWindow.activate()}
 }
 val channelRestore=remember{FocusRestore()};val favoriteRestore=remember{FocusRestore()};var restoreId by if(tab==1)favoriteRestore else channelRestore
 // Attached nodes are bookkeeping, not UI state. Lazy item attach/dispose must not
 // invalidate the header/home while a return animation creates new rows.
 val focusers=remember{hashMapOf<String,FocusRequester>()}
 fun resetCategory(){group="";if(app.prefs.selectedGroup.isNotEmpty())app.prefs.selectedGroup=""}
 fun selectTab(index:Int){gridInput.entering=false;gridInput.entryGeneration++;settingsNavigation.cancelPendingFocus();if(index==2)settingsNavigation.resetEntry();gridInput.job?.cancel();gridInput.target=null;menuChannel=null;if(index!=tab)channelDigits="";if(index==0&&tab!=0){resetCategory();channelRestore.value="";channelGrid.requestScrollToItem(0);firstCardFocusPending=false};tab=index}
 fun refresh(onChannelsUpdated:(()->Unit)?=null){scope.launch{status="正在更新订阅…";val result=app.channels.refreshAll(){status=it};revision++;status="订阅更新：${result.ok} 个成功，${result.failed} 个失败";onChannelsUpdated?.invoke();if(app.prefs.autoUpdateEpg) {app.epg.refresh();revision++}}}
 LaunchedEffect(cacheReady){if(!cacheReady)return@LaunchedEffect;app.prefs.selectedGroup="";launch{withContext(Dispatchers.IO){app.epg.loadCache()};revision++;if(app.prefs.autoUpdateOnStart)refresh()else if(app.prefs.autoUpdateEpg){app.epg.refresh();revision++}}}
 LaunchedEffect(playing){if(playing==null){
  val serial=gridInput.serial
  if(restoreId.isNotBlank()){
   val index=app.channels.channelsIn(if(group.isBlank()||tab==1)null else group,tab==1,"").indexOfFirst{it.id==restoreId}
   if(index>=0){if(grid.layoutInfo.visibleItemsInfo.none{it.key==restoreId})grid.scrollToItem(index);withTimeoutOrNull(2000){while(!focusers.containsKey(restoreId))withFrameNanos{}}}
  }
  withFrameNanos{};withFrameNanos{}
  if(serial==gridInput.serial){runCatching{(if(restoreId.isBlank())nav[tab] else focusers[restoreId]?:nav[tab]).requestFocus()};restoringHome=false}
 }}
 var retryFromEmpty by remember{mutableStateOf(false)}
 val noSources=cacheReady&&app.channels.sources.isEmpty()
 val all=remember(cacheReady,revision,app.channels.revision,app.channels.displayRevision,app.prefs.hideUnavailableRoutes,group,tab,noSources){if(!cacheReady||noSources)emptyList() else app.channels.channelsIn(if(group.isBlank()||tab==1)null else group,tab==1,"")}
 LaunchedEffect(app.updatingAll){if(retryFromEmpty&&!app.updatingAll){retryFromEmpty=false;firstCardFocusPending=true}}
 // The filter click must not capture the previous list or race a fixed delay.
 LaunchedEffect(firstCardFocusPending,group,all,dialog,tab,sources,playing){
  if(firstCardFocusPending&&dialog.isBlank()&&tab in 0..1&&!sources&&playing==null){
   while(homeModal?.visible==true)withFrameNanos{}
   grid.scrollToItem(0)
   val target=all.firstOrNull()?.id
   val laidOut=target!=null&&withTimeoutOrNull(2000){
    while(grid.layoutInfo.visibleItemsInfo.firstOrNull()?.key!=target||!focusers.containsKey(target))withFrameNanos{};true
   }==true
   withFrameNanos{}
   ensureActive()
   val requester=if(laidOut)focusers[target]else null
   try{val target=requester?:if(tab==1)nav[1]else first;if(!target.requestFocus()){withFrameNanos{};ensureActive();target.requestFocus()}}
   catch(_:IllegalStateException){
    // A concurrent navigation/removal may detach a node after measurement.
    // The category header is the safe mounted fallback; never crash the UI.
    runCatching{(if(tab==1)nav[1]else first).requestFocus()}
   }
   firstCardFocusPending=false
   returningToTop=false
  }
 }
 // Resolve XMLTV aliases/programme searches off the scrolling composition path.
 var programmeMinute by remember{mutableLongStateOf(System.currentTimeMillis()/60000)}
 LaunchedEffect(Unit){while(true){delay(60000);programmeMinute=System.currentTimeMillis()/60000}}
 var captions by remember{mutableStateOf<Map<String,String>>(emptyMap())}
 LaunchedEffect(all,revision,app.epg.revision,programmeMinute){
  captions=withContext(Dispatchers.Default){all.associate{c->c.id to (app.epg.nowNext(c).first?.let{"${Ui.hhmm(it.start)}  ${it.title}"}?:"暂无节目信息")}}
 }
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 LaunchedEffect(lifecycle){lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){delay(com.nexttv.core.App.epgRefreshMillis);if(app.prefs.autoUpdateEpg){app.epg.refresh(true);revision++}}}}
 val columns=homeColumnCount(homeConfiguration.screenWidthDp,app.prefs.gridMode,homeConfiguration.screenHeightDp)
 LaunchedEffect(InteractionStyle.pointer){if(InteractionStyle.pointer){gridInput.job?.cancel();gridInput.target=null}}
 LaunchedEffect(tab,group,playing,sources,menuChannel?.id,all){gridInput.job?.cancel();gridInput.target=null}
 val previewEnabled = cacheReady && app.prefs.channelPreviews && playing == null && !sources &&
  tab in 0..1 && dialog.isBlank() && menuChannel == null
 val previewRunning = if (previewEnabled) previewsReady(grid) else false
 val previewSlots = if (previewEnabled) {
  var settledSlots by remember(grid, group) { mutableStateOf(emptyMap<String, Int>()) }
  LaunchedEffect(grid, group, all, previewRunning) {
   if (previewRunning) {
    val layout = grid.layoutInfo
    settledSlots = layout.visibleItemsInfo.filter {
     minOf(it.offset.y + it.size.height, layout.viewportEndOffset) -
      maxOf(it.offset.y, layout.viewportStartOffset) >= it.size.height * .5f
    }.mapNotNull { it.key as? String }.withIndex().associate { it.value to it.index }
   }
  }
  DisposableEffect(Unit) { onDispose { PreviewDiagnostics.visible(emptySet()) } }
  SideEffect { PreviewDiagnostics.visible(settledSlots.keys) }
  settledSlots
 } else emptyMap()
 fun clearExit(){exitGate.reset();homeNotice=""}
 fun cancelReturn(){returnGeneration++;returnJob?.cancel();returningToTop=false;firstCardFocusPending=false}
 fun contentFocus(){navFocused=false;clearExit()}
 fun focusNav(){gridInput.job?.cancel();gridInput.target=null;cardInput=false;cancelReturn();clearExit();if(tab in 0..1)restoreId=""else settingsNavigation.resetEntry();val selected=tab;val serial=gridInput.serial;scope.launch{while(!homeView.hasWindowFocus())withFrameNanos{};withFrameNanos{};if(serial==gridInput.serial&&selected==tab)runCatching{nav[selected].requestFocus()}}}
 fun closeSources(){sources=false;settingsRestore++;clearExit()}
 fun returnTop(){val activeMotion=gridInput.job;activeMotion?.cancel();gridInput.target=null;cardInput=false;
  if(returningToTop)return
  firstCardFocusPending=false;clearExit();navFocused=false
  returningToTop=true;runCatching{returnAnchor.requestFocus()}
  val generation=++returnGeneration
  returnJob=scope.launch{
   var focusQueued=false
   try{
    activeMotion?.join()
    val layout=grid.layoutInfo
    val rows=layout.visibleItemsInfo.distinctBy{it.row}.sortedBy{it.row}
    val stride=if(rows.size>1)(rows[1].offset.y-rows[0].offset.y).toFloat()else(rows.firstOrNull()?.size?.height?:1).toFloat()
    val distance=grid.firstVisibleItemIndex/columns*stride+grid.firstVisibleItemScrollOffset
    if(distance>0&&app.prefs.reduceMotion)grid.scrollToItem(0)else if(distance>0)grid.scroll(MutatePriority.UserInput){
     // A wall-clock tween catches up by multiple rows after a long frame,
     // forcing their composition into that same frame. Bound work per frame
     // instead; preserve continuous travel and cancellation by new input.
     var remaining=distance;var velocity=0f;var previous=0L;var stalled=0
     while(remaining>1f&&isActive){
      val frame=withFrameNanos{it}
      val dt=if(previous==0L).016f else(frame-previous)/1_000_000_000f
      previous=frame
      val scale=currentCoroutineContext()[androidx.compose.ui.MotionDurationScale]?.scaleFactor?:1f
      val step=if(app.prefs.reduceMotion||scale<=0f)FocusScrollAdvance(-remaining,0f)else advanceReturnScroll(-remaining,velocity,dt/scale,(layout.viewportEndOffset-layout.viewportStartOffset).toFloat(),stride)
      val moved=-scrollBy(step.delta)
      if(moved<.1f){if(++stalled>=3)break}else stalled=0
      remaining-=moved;velocity=step.velocity
     }
     // Only the subpixel remainder is snapped, never an off-screen row.
     if(remaining in 0f..1f)scrollBy(-remaining)
    }
    ensureActive()
    // Estimated distance can differ from the measured grid by a few pixels.
    if(generation==returnGeneration&&(grid.firstVisibleItemIndex!=0||grid.firstVisibleItemScrollOffset!=0))grid.scrollToItem(0)
    if(generation==returnGeneration){focusQueued=true;restoreId="";returningToTop=false;runCatching{nav[tab].requestFocus()}}
   }finally{if(generation==returnGeneration&&!focusQueued)returningToTop=false}
  }
 }
 fun homeBack(){
  if(menuChannel!=null){val id=menuChannel!!.id;menuChannel=null;runCatching{focusers[id]?.requestFocus()};return}
  if(channelDigits.isNotBlank()){channelDigits="";clearExit();return}
  gridInput.serial++
  if(restoringHome){restoringHome=false;focusNav();return}
  if(com.nexttv.BuildConfig.DEBUG)android.util.Log.d("NextTVBack","home tab=$tab nav=$navFocused dialog=$dialog sources=$sources")
  when{
   playing!=null->{playing=null;revision++}
   dialog.isNotBlank()->{dialog="";clearExit()}
   sources->closeSources()
   returningToTop->Unit
   navFocused&&tab in 0..1&&(grid.firstVisibleItemIndex>0||grid.firstVisibleItemScrollOffset>0)->returnTop()
   navFocused->{if(exitGate.press(android.os.SystemClock.elapsedRealtime()))onExit()else{homeNoticeEpoch++;homeNotice="再按一次退出"}}
   tab in 0..1&&(grid.firstVisibleItemIndex>0||grid.firstVisibleItemScrollOffset>0||all.indexOfFirst{it.id==restoreId}>=columns)->returnTop()
   else->focusNav()
  }
 }
 SideEffect{settingsNavigation.top=nav[2]}
 // A wheel can dispose the first/saved lazy item while the header still owns keyboard focus.
 // Enter an attached, fully visible card instead of requesting an off-screen first item.
 fun visibleCardEntry():FocusRequester?{
  val old=all.indexOfFirst{it.id==restoreId}
  val index=homeHeaderEntryIndex(grid.firstVisibleItemIndex,grid.firstVisibleItemScrollOffset,columns,old,grid.visibleFocusIndex(old),(grid.layoutInfo.beforeContentPadding-12*homeView.resources.displayMetrics.density).toInt().coerceAtLeast(1))?:return null
  return all.getOrNull(index)?.id?.let{focusers[it]}
 }
 fun guardFirstGridFrame(){
  val generation=++gridInput.entryGeneration;gridInput.entering=true
  scope.launch{withFrameNanos{};withFrameNanos{};if(generation==gridInput.entryGeneration)gridInput.entering=false}
 }
 fun enterContent(){
  val selected=tab;val serial=gridInput.serial
  scope.launch{repeat(3){
   withFrameNanos{}
   if(selected!=tab||serial!=gridInput.serial||playing!=null||sources)return@launch
   val target=when(selected){1->visibleCardEntry();2->settingsNavigation.rows["分类/订阅与节目单"];else->if(noSources)emptyAction else first}
   if(target!=null&&runCatching{target.requestFocus();true}.getOrDefault(false))return@launch
  }}
 }
 fun toggleMenuFavorite(c:Channel){
  val favoriteInputSerial=gridInput.serial
  val removing=tab==1&&app.prefs.isFavorite(c)
  val index=all.indexOfFirst{it.id==c.id}
  val adjacent=all.getOrNull(index+1)?.takeIf{focusers.containsKey(it.id)}?:all.getOrNull(index-1)
  if(removing)runCatching{(adjacent?.id?.let{focusers[it]}?:nav[1]).requestFocus()}
  app.prefs.toggleFavorite(c);revision++;dialog=""
  if(removing){restoreId=adjacent?.id.orEmpty();scope.launch{
   while(!homeView.hasWindowFocus())withFrameNanos{}
   withFrameNanos{};withFrameNanos{}
   if(favoriteInputSerial==gridInput.serial&&tab==1&&playing==null&&dialog.isBlank())runCatching{(adjacent?.id?.let{focusers[it]}?:nav[1]).requestFocus()}
  }}
 }
 fun moveGrid(action:InputAction)=moveHomeGrid(
  action,all,columns,grid,focusers,gridInput,{restoreId},
  homeView.resources.displayMetrics.density,scope,{app.prefs.reduceMotion},
 )
 val activity=context as TvActivity
 val latestGridDiagnostics by rememberUpdatedState({val layout=grid.layoutInfo;val item=layout.visibleItemsInfo.firstOrNull{it.key==restoreId};org.json.JSONObject().put("group",group).put("count",all.size).put("firstId",all.firstOrNull()?.id.orEmpty()).put("first",grid.firstVisibleItemIndex).put("offset",grid.firstVisibleItemScrollOffset).put("selectedIndex",all.indexOfFirst{it.id==restoreId}).put("navigationIndex",all.indexOfFirst{it.id==gridInput.target}).put("targetIndex",(columns*8).coerceAtMost(all.lastIndex)).put("nav",navFocused).put("returning",returningToTop).put("itemTop",item?.offset?.y?:-9999).put("itemBottom",item?.let{it.offset.y+it.size.height}?:9999).put("viewportStart",layout.viewportStartOffset).put("viewportEnd",layout.viewportEndOffset)})
 DisposableEffect(activity){activity.gridDiagnostics={latestGridDiagnostics()};onDispose{activity.gridDiagnostics=null}}

 val latestHomeBack by rememberUpdatedState<()->Unit>({homeBack()})
 val latestInteraction by rememberUpdatedState<()->Unit>({
  if(InteractionStyle.pointer||InteractionStyle.keyboardAfterPointer){gridInput.job?.cancel();gridInput.target=null}
  gridInput.serial++;restoringHome=false
  clearExit()
  if(returningToTop){
   cancelReturn()
   val visible=grid.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
   runCatching{focusers[visible]?.requestFocus()}
  }else firstCardFocusPending=false
 })
 DisposableEffect(activity){activity.homeBack={latestHomeBack()};activity.homeInteraction={latestInteraction()};onDispose{activity.homeBack=null;activity.homeInteraction=null}}
 LaunchedEffect(homeNotice,homeNoticeEpoch){if(homeNotice.isNotBlank()){delay(2000);homeNotice=""}}
 BackHandler(enabled=playing==null){homeBack()}
 if(playing==null){
  androidx.activity.compose.ReportDrawnWhen{firstFrameReady&&app.cacheReady}
  if(firstFrameReady&&cacheReady&&onLaunchReady!=null)SideEffect(onLaunchReady)
 }
 Box(Modifier.fillMaxSize().pointerInput(menuChannel?.id){
 if(menuChannel!=null)awaitPointerEventScope{while(true){val event=awaitPointerEvent(PointerEventPass.Initial)
  if(event.type==PointerEventType.Press&&event.changes.any{inlineBounds[0]?.contains(it.position)!=true}){event.changes.forEach{it.consume()};menuChannel=null}
 }}
 }.onPreviewKeyEvent{e->
 if(!cacheReady)return@onPreviewKeyEvent true
 val key=TvKey(e.nativeKeyEvent)
 if(key.tab&&key.down){gridInput.job?.cancel();gridInput.target=null}
 if(playing==null&&!sources&&dialog.isBlank()&&gridInput.entering&&key.down&&key.action==InputAction.DOWN)return@onPreviewKeyEvent true
 val digit=key.digit
 if(playing==null&&digit==null&&key.down)channelDigits=""
 if(playing==null&&!sources&&dialog.isBlank()&&navFocused&&key.down&&key.horizontal!=null){
  val target=(tab+if(key.action==InputAction.LEFT)-1 else 1).coerceIn(0,2)
  if(target!=tab){nav[target].requestFocus();selectTab(target)}
  return@onPreviewKeyEvent true
 }
 if(playing==null&&!sources&&dialog.isBlank()&&menuChannel==null&&tab==0&&!navFocused&&!cardInput&&key.action==InputAction.DOWN&&key.down){
  gridInput.job?.cancel();gridInput.target=null
  if(all.isNotEmpty()){guardFirstGridFrame();val request=visibleCardEntry();if(request==null||!runCatching{request.requestFocus();true}.getOrDefault(false))firstCardFocusPending=true}else runCatching{emptyAction.requestFocus()}
  return@onPreviewKeyEvent true
 }
 if(playing==null&&!sources&&dialog.isBlank()&&tab in 0..1&&!navFocused&&InteractionStyle.keyboardAfterPointer&&key.down&&(key.vertical||key.horizontal!=null)){
  val old=all.indexOfFirst{it.id==restoreId};val at=grid.visibleFocusIndex(old)
  if(at!=old&&visibleCardEntry()?.let{runCatching{it.requestFocus();true}.getOrDefault(false)}==true)return@onPreviewKeyEvent true
 }
 if(playing==null&&!sources&&dialog.isBlank()&&tab==2&&!navFocused&&InteractionStyle.keyboardAfterPointer&&key.down&&key.vertical&&settingsNavigation.resumeVisible())return@onPreviewKeyEvent true
 if(playing==null&&!sources&&dialog.isBlank()&&menuChannel==null&&cardInput&&tab in 0..1&&!navFocused&&key.down){
  val direction=key.action
  if(direction in listOf(InputAction.UP,InputAction.DOWN,InputAction.LEFT,InputAction.RIGHT)&&moveGrid(direction!!))return@onPreviewKeyEvent true
 }
 if(playing==null&&!sources&&dialog.isBlank()&&menuChannel==null&&tab in 0..1&&digit!=null){if(key.down&&!key.repeated)channelDigits=appendChannelDigit(channelDigits,digit,digitCapacity);return@onPreviewKeyEvent true}
 if(key.down&&key.action==InputAction.LEFT&&playing==null&&restoringHome){runCatching{nav[0].requestFocus()};true}else if(key.down&&key.action==InputAction.DOWN&&navFocused&&playing==null&&!sources&&dialog.isBlank()){enterContent();true}else false}.onGloballyPositioned{firstFrameReady=true}.focusRequester(returnAnchor).focusProperties{canFocus=returningToTop}.focusable().background(NextTvStyle.Home)){

  if(playing!=null){PlayerScreen(playing!!,onLaunchReady=onLaunchReady,onHome={resetCategory();playing=null;tab=0;restoringHome=true;revision++},onClose={current->if(app.prefs.startLive){onExit();return@PlayerScreen};if(tab==1)favoriteRestore.value=if(app.prefs.isFavorite(current))current.id else app.channels.displayChannels.filter{app.prefs.isFavorite(it)}.minByOrNull{kotlin.math.abs(it.number-current.number)}?.id.orEmpty() else channelRestore.value=current.id;resetCategory();restoringHome=true;playing=null;revision++});return@Box}
  if(channelDigits.isNotBlank())AppText(channelDigits,Modifier.align(Alignment.TopEnd).padding(20.dp),fontSize=InteractionStyle.display,color=NextTvStyle.Text)
  val currentViewport=tvViewportScale(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp)
  val homeTopPadding=safeInsets.vertical+58f
  val settingsTopPadding=if(tab==2) homeTopPadding*currentViewport else homeTopPadding
  Column(Modifier.fillMaxSize().padding(top=settingsTopPadding.dp)){
   when{
    sources->SourcesScreen(onBack={closeSources()},onChanged={revision++})
    tab==2->SettingsScreen(settingsNavigation,settingsRestore,navFocused,onContentFocus={contentFocus()},onBackToNav={focusNav()},onTopBack={homeBack()},onSources={sources=true;clearExit()},onChanged={revision++})

    else->{
     HomeHeader(
      title=if(tab==1)"我的收藏"else if(group.isBlank())"全部频道"else group,
      subtitle=if(!cacheReady)"正在读取频道…" else "共 ${all.size} 个频道"+if(app.updatingAll)" · 更新中"else"",
      showCategory=tab==0&&cacheReady&&!noSources,
      first=first,navUp=nav[tab],
      categoryDown=if(all.isEmpty())emptyAction else FocusRequester.Cancel,
      horizontalPadding=homeHorizontal.toInt(),
      minHeight=if(homeConfiguration.screenHeightDp<480)64 else 76,
      onFocus={gridInput.job?.cancel();gridInput.target=null;cardInput=false;contentFocus()},
      onCategoryClick={contentFocus();first.requestFocus();dialog="分类"}
     )

     CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides HomeGridFocusVisibility) {
     LazyVerticalGrid(GridCells.Fixed(columns),flingBehavior=appFlingBehavior(),state=grid,modifier=Modifier.fillMaxSize().continuousWheel(grid).observeGridDraws(grid),contentPadding=PaddingValues(start=homeHorizontal.dp,end=homeHorizontal.dp,top=14.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      itemsIndexed(all,key={_,c->c.id}){index,c->
       HomeChannelTile(
        channel=c,index=index,count=all.size,columns=columns,tab=tab,focusers=focusers,
        topFocus=if(tab==1)nav[1]else first,caption=captions[c.id]?:"暂无节目信息",
        favorite=app.prefs.isFavorite(c),menuOpen=menuChannel?.id==c.id,
        previewSlot=previewSlots[c.id],previewRunning=previewRunning,
        onFocus={if(menuChannel?.id!=c.id)menuChannel=null;cardInput=true;restoreId=c.id;contentFocus()},
        onLongClick={channelDigits="";restoreId=c.id;menuChannel=c},
        onClick={if(menuChannel!=null)menuChannel=null else{restoreId=c.id;playing=c}},
        onMenuBounds={inlineBounds[0]=it},
        onFavorite={toggleMenuFavorite(c);menuChannel=null},
       )
      }
      if(cacheReady&&all.isEmpty())item(span={GridItemSpan(columns)}){
       HomeEmptyState(
        message=homeEmptyMessage(tab,app.prefs.favorites.isEmpty(),noSources,addingSubscription,app.updatingAll,
         app.channels.sources.any{it.enabled},app.channels.hasLoadedChannels),
        hint=homeEmptyHint(tab,noSources,app.prefs.favorites.isEmpty()),
        actionLabel=homeEmptyActionLabel(noSources,app.channels.sources.any{it.enabled},app.channels.hasLoadedChannels,tab),
        emptyAction=emptyAction,
        navUp=if(noSources)nav[tab]else first,
        horizontalPadding=36,
        onFocus={contentFocus()},
        onAction={contentFocus();if(noSources)dialog="添加订阅"else if(app.channels.sources.any{it.enabled}&&!app.channels.hasLoadedChannels){retryFromEmpty=true;app.refreshSubscriptionsAndGuide()}else sources=true}
       )
      }
     }
     }
    }
   }
  }
  if(!sources)HomeNavigation(tab,navFocused,nav,Modifier.align(Alignment.TopCenter).padding(top=(safeInsets.vertical*.67f*currentViewport).dp),homeCompositions={homeCompositionCount[0]},onSelect={i->if(!cacheReady)return@HomeNavigation;cardInput=false;cancelReturn();clearExit();navFocused=true;selectTab(i)})
  if(homeNotice.isNotBlank())ExitNotice(homeNotice)
 }
 fun dismissAddSubscription(){
  dialog=""
  val serial=gridInput.serial;val selected=tab
  scope.launch{withFrameNanos{};withFrameNanos{};if(serial==gridInput.serial&&selected==tab&&all.isEmpty()&&!sources&&playing==null)runCatching{emptyAction.requestFocus()}}
 }
 HomeChannelDialogs(dialog, app.channels.displayGroups, group, ::dismissAddSubscription,
  onSubscription={input->
   val source=subscriptionFromAddress(input,app.channels.sources)
   scope.launch{
    app.channels.upsertSourceAsync(source)
    dialog="";tab=0;group="";app.prefs.selectedGroup="";restoreId="";cancelReturn();addingSubscription=true;revision++
    val serial=gridInput.serial
    refresh{addingSubscription=false;if(serial==gridInput.serial&&tab==0&&dialog.isBlank()&&!sources&&playing==null)firstCardFocusPending=true}
   }
  },
  onDismissCategory={dialog=""},
  onCategory={g->gridInput.job?.cancel();gridInput.target=null;group=g;restoreId="";channelGrid.requestScrollToItem(0);firstCardFocusPending=true;dialog=""},
 )
}

/** Restoration is navigation bookkeeping, not visual selection state. */
private class FocusRestore(var value:String="") {
 operator fun getValue(owner:Any?,property:kotlin.reflect.KProperty<*>)=value
 operator fun setValue(owner:Any?,property:kotlin.reflect.KProperty<*>,next:String){value=next}
}
