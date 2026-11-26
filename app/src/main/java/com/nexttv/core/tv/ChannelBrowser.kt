// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.nexttv.core.tv

import android.view.KeyEvent as K
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import com.nexttv.R
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.nexttv.core.App
import com.nexttv.core.data.Channel
import com.nexttv.core.data.Program
import com.nexttv.core.data.programmeProgress
import com.nexttv.core.data.replayAddress
import com.nexttv.core.ui.Ui
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@Stable internal class BrowserController{var pending by mutableIntStateOf(0);var move:((Int)->Unit)?=null;var resumeVisible:(()->Boolean)?=null;var back:(()->Boolean)?=null;fun navigate(delta:Int){val action=move;if(action==null)pending=delta else action(delta)}}

/** Same header rhythm in every browser column, including enlarged system fonts. */
@Composable private fun BrowserHeading(text:String,centered:Boolean=false){
 val scale=androidx.compose.ui.platform.LocalDensity.current.fontScale
 Column(Modifier.fillMaxWidth().padding(start=if(centered)0.dp else 12.dp,bottom=8.dp)){
  AppText(text,Modifier.fillMaxWidth().semantics{heading()}.heightIn(min=24.dp*scale).wrapContentHeight(Alignment.CenterVertically),fontSize=if(centered)InteractionStyle.caption else InteractionStyle.body,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,color=if(centered)NextTvStyle.Muted else NextTvStyle.Text,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=if(centered)androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)
  if(!centered)Box(Modifier.fillMaxWidth().padding(top=7.dp).height(.75.dp).background(NextTvStyle.PlayerInfoBorder))
 }
}

/** Programme status: live is a red dot and plain text; replay is quiet secondary text. */
@Composable private fun BrowserBadge(text:String,accent:Boolean){
 if(accent){
  Row(Modifier.height(19.dp*androidx.compose.ui.platform.LocalDensity.current.fontScale).padding(horizontal=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
   Box(Modifier.size(5.dp).background(NextTvStyle.Live,RoundedCornerShape(50)))
   AppText(if(text=="直播中")"直播" else text,fontSize=InteractionStyle.micro,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,color=NextTvStyle.Live,maxLines=1)
  }
 }else{
  AppText(text,Modifier.padding(horizontal=2.dp),fontSize=InteractionStyle.micro,fontWeight=androidx.compose.ui.text.font.FontWeight.Medium,color=NextTvStyle.Muted.copy(alpha=.82f),maxLines=1)
 }
}

/** Right-edge hint kept vertical and tight so every glyph fits on narrow TV rail. */
@Composable internal fun BrowserEdgeHint(width:Dp,modifier:Modifier=Modifier){
 Box(modifier.width(width).fillMaxHeight().padding(vertical=4.dp),contentAlignment=Alignment.Center){
  AppText(
   "向右查看节目单".toCharArray().joinToString("\n"),
   fontSize=InteractionStyle.captionSmall,
   lineHeight=InteractionStyle.captionSmall*1.05f,
   letterSpacing=0.sp,
   color=NextTvStyle.Text.copy(alpha=.8f),
   textAlign=androidx.compose.ui.text.style.TextAlign.Center,
   maxLines=9,
   overflow=TextOverflow.Clip,
  )
 }
}

/** One rail, channel stack on top and the programme stack underneath. */
@Composable internal fun ProgrammeSideSheet(width:Dp,candidate:Channel,channels:List<Channel>,showLogos:Boolean,list:LazyListState,requests:MutableMap<String,FocusRequester>,programs:List<Program>,epgList:LazyListState,epgRequests:MutableMap<Long,FocusRequester>,days:List<Int>,dateList:LazyListState,dateRequests:Map<Int,FocusRequester>,epgDay:Int,now:Long,region:BrowserRegion,onChannelFocus:(Channel)->Unit,onChannelClick:(Channel)->Unit,onProgramFocus:(Program)->Unit,onProgramClick:(Channel,Program)->Unit,onDate:(Int)->Unit,onDateFocus:(Int)->Unit){
 val scale=androidx.compose.ui.platform.LocalDensity.current.fontScale
 val density=androidx.compose.ui.platform.LocalDensity.current
 val logoWidth=(width.value*.14f).coerceIn(24f,44f).coerceAtMost(width.value*.4f).dp
 // Entering the EPG shrinks the channel viewport; keep the active channel fully visible above the programme stack.
 LaunchedEffect(candidate.id,channels.size){
  val index=channels.indexOfFirst{it.id==candidate.id}
  if(index>=0){
   snapshotFlow{list.layoutInfo.totalItemsCount>0}.first{it}
   withFrameNanos{}
   list.revealFocusRow(index)
  }
 }
 Box(Modifier.width(width).fillMaxHeight().playerSidebarSurface{with(density){width.toPx()}}){
  Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=12.dp)){
   Row(Modifier.fillMaxWidth().padding(start=10.dp),verticalAlignment=Alignment.Top){
    Box(Modifier.padding(top=2.dp).width(34.dp).height(28.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFF6C8CA6),Color(0xFF95AFC2)))),contentAlignment=Alignment.Center){
     PlayerLogo(candidate,Modifier.fillMaxSize())
    }
    Column(Modifier.weight(1f).padding(start=8.dp)){
     AppText("频道与节目单",fontSize=InteractionStyle.captionSmall,color=NextTvStyle.Muted,maxLines=1)
     AppText(Ui.displayName(candidate.name),fontSize=InteractionStyle.display,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
   }
   Spacer(Modifier.height(8.dp))
   LazyColumn(flingBehavior=appFlingBehavior(),state=list,modifier=Modifier.fillMaxWidth().heightIn(max=156.dp).continuousWheel(list),contentPadding=PaddingValues(bottom=2.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
    itemsIndexed(channels,key={_,it->it.id}){index,c->
     val request=remember(c.id){FocusRequester()}
     DisposableEffect(c.id){requests[c.id]=request;onDispose{if(requests[c.id]===request)requests.remove(c.id)}}
     TvSurface(Modifier.fillMaxWidth().heightIn(min=44.dp).focusRequester(request).focusEdges(up=index==0,down=index==channels.lastIndex).focusProperties{canFocus=region==BrowserRegion.CHANNELS;if(index==0)up=FocusRequester.Cancel;if(index==channels.lastIndex)down=FocusRequester.Cancel},kind="categoryRow",selected=c.id==candidate.id&&!InteractionStyle.pointer,onFocus={onChannelFocus(c)},onClick={onChannelClick(c)}){
      Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
       val slot=Modifier.width(logoWidth).height(38.dp)
       if(showLogos)PlayerLogo(c,slot,fallback=c.number.toString().padStart(3,'0'))
       else Box(slot,contentAlignment=Alignment.Center){AppText(c.number.toString().padStart(3,'0'),fontSize=InteractionStyle.captionSmall,color=NextTvStyle.Muted,maxLines=1,fontFeatureSettings="tnum")}
       AppText(Ui.displayName(c.name),Modifier.weight(1f).padding(start=8.dp),fontSize=InteractionStyle.body,maxLines=1,overflow=TextOverflow.Ellipsis)
      }
     }
    }
   }
   Box(Modifier.fillMaxWidth().padding(top=10.dp,bottom=10.dp).height(.5.dp).background(NextTvStyle.Divider))
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    AppText("节目单",fontSize=InteractionStyle.heading,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,maxLines=1)
    Spacer(Modifier.weight(1f))
    LazyRow(flingBehavior=appFlingBehavior(),state=dateList,modifier=Modifier.continuousWheel(dateList),horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(start=4.dp,end=2.dp)){
     items(days,key={it}){day->
      val date=remember(day,now){java.util.Calendar.getInstance().apply{timeInMillis=now;add(java.util.Calendar.DAY_OF_YEAR,day)}.time}
      val label=when(day){0->"今天 "+java.text.SimpleDateFormat("EEE",java.util.Locale.SIMPLIFIED_CHINESE).format(date);1->"明天 "+java.text.SimpleDateFormat("EEE",java.util.Locale.SIMPLIFIED_CHINESE).format(date);else->java.text.SimpleDateFormat("EEE MM-dd",java.util.Locale.SIMPLIFIED_CHINESE).format(date)}
      TvSurface(Modifier.focusRequester(dateRequests.getValue(day)).focusEdges(left=day==days.firstOrNull(),right=day==days.lastOrNull()).focusProperties{canFocus=region==BrowserRegion.DATES;if(day==days.firstOrNull())left=FocusRequester.Cancel;if(day==days.lastOrNull())right=FocusRequester.Cancel}.semantics{stateDescription=if(day==epgDay)"已选择"else""},kind="date",selected=false,focusRing=false,onFocus={onDateFocus(day)},onClick={onDate(day)}){
       Box(Modifier.padding(horizontal=2.dp,vertical=3.dp)){
        AppText(label,fontSize=InteractionStyle.captionSmall,color=if(day==epgDay)NextTvStyle.Text else NextTvStyle.Muted,maxLines=1)
        if(day==epgDay)Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(top=4.dp).height(1.5.dp).background(NextTvStyle.Ivory))
       }
      }
     }
    }
   }
   Spacer(Modifier.height(4.dp))
   if(programs.isEmpty())Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){AppText("暂无节目单",fontSize=InteractionStyle.body,color=NextTvStyle.Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
   else LazyColumn(flingBehavior=appFlingBehavior(),state=epgList,modifier=Modifier.fillMaxWidth().weight(1f).continuousWheel(epgList),contentPadding=PaddingValues(bottom=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
    itemsIndexed(programs,key={_,it->"${it.channelKey}:${it.start}:${it.stop}"}){index,p->
     val request=remember(p.start){FocusRequester()}
     DisposableEffect(p.start,request){epgRequests[p.start]=request;onDispose{if(epgRequests[p.start]===request)epgRequests.remove(p.start)}}
     val live=now in p.start until p.stop
     val replay=p.stop<=now&&candidate.playbackRoutes.any{replayAddress(it,p,now)!=null}
     TvSurface(Modifier.fillMaxWidth().heightIn(min=34.dp*scale).focusRequester(request).focusEdges(up=index==0,down=index==programs.lastIndex).focusProperties{canFocus=region==BrowserRegion.PROGRAMMES;if(index==0)up=FocusRequester.Cancel;if(index==programs.lastIndex)down=FocusRequester.Cancel},kind="playerRow",onFocus={onProgramFocus(p)},onClick={onProgramClick(candidate,p)}){
      Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
       AppText(Ui.hhmm(p.start),Modifier.width(44.dp*scale),fontSize=InteractionStyle.captionSmall,color=NextTvStyle.Muted,maxLines=1,fontFeatureSettings="tnum")
       AppText(p.title,Modifier.weight(1f).padding(start=8.dp,end=6.dp),fontSize=InteractionStyle.body,color=NextTvStyle.Text,maxLines=1,overflow=TextOverflow.Ellipsis)
       if(live)BrowserBadge("直播中",true)else if(replay)BrowserBadge("回放",false)
      }
     }
    }
   }
   Box(Modifier.fillMaxWidth().padding(top=6.dp,bottom=6.dp).height(.5.dp).background(NextTvStyle.Divider))
   AppText("左右切换日期·上下选择节目·确认播放·返回键回频道",fontSize=11.sp,color=NextTvStyle.Muted,maxLines=1)
  }
 }
}

/** Move once when the target is clipped, then let the mounted item take focus. */
private suspend fun LazyListState.revealFocusRow(index: Int) {
 val layout = layoutInfo
 val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
 val start = layout.viewportStartOffset + layout.beforeContentPadding
 val end = layout.viewportEndOffset - layout.afterContentPadding
 when {
  item == null -> scrollToItem((index - 1).coerceAtLeast(0))
  item.offset < start -> scrollBy((item.offset - start).toFloat())
  item.offset + item.size > end -> scrollBy((item.offset + item.size - end).toFloat())
 }
}

/** GROUPS ↔ CHANNELS ↔ EPG. Stable channel column and list state; never mount all three. */
@Composable internal fun ChannelSidePanel(current:Channel,group:String?,generation:Int,onGroup:(String?)->Unit,onSelect:(Channel)->Unit,onProgram:(Channel,Program)->Unit,controller:BrowserController,onCloseAll:()->Unit,playingReplay:Boolean=false,playingPaused:Boolean=false,modifier:Modifier){
 val app=App.get();val scope=rememberCoroutineScope();val hostView=androidx.compose.ui.platform.LocalView.current
 var epgDay by remember{mutableIntStateOf(0)}
 var now by remember{mutableLongStateOf(System.currentTimeMillis())}
 LaunchedEffect(Unit){while(true){delay(15000);now=System.currentTimeMillis()}}
 var activeGroup by remember{mutableStateOf(group)}
 // Navigation owns the active region; delayed focus events from the outgoing column must not steal it.
 var navigation by remember{mutableStateOf(BrowserNavigation())}
 val sourceId=current.sourceId
 val sourceChannels=remember(sourceId,app.channels.revision,app.channels.displayRevision,app.prefs.hideUnavailableRoutes){app.channels.displayChannels.filter{it.sourceId==sourceId}}
 val categories=remember(sourceId,app.channels.revision,app.channels.displayRevision,app.prefs.hideUnavailableRoutes){listOf<String?>(null)+sourceChannels.map{it.groupName}.distinct()}
 var groupFocus by remember{mutableIntStateOf(categories.indexOf(group).coerceAtLeast(0))}
 val groupList=rememberLazyListState((categories.indexOf(group)-1).coerceAtLeast(0))
 val channels=remember(sourceId,activeGroup,app.channels.revision,app.channels.displayRevision,app.prefs.hideUnavailableRoutes){sourceChannels.filter{activeGroup==null||it.groupName==activeGroup}}
 val latestChannels by rememberUpdatedState(channels)
 val list=rememberLazyListState((channels.indexOfFirst{it.id==current.id}.coerceAtLeast(0)-2).coerceAtLeast(0))
 LaunchedEffect(app.channels.revision,app.channels.displayRevision,app.prefs.hideUnavailableRoutes){if(sourceChannels.isEmpty())onCloseAll()else if(activeGroup!=null&&activeGroup !in categories){activeGroup=null;onGroup(null)}}
 var candidateId by remember{mutableStateOf(current.id)}
 val candidate=channels.firstOrNull{it.id==candidateId}?:channels.firstOrNull()?:current
 val requests=remember{mutableStateMapOf<String,FocusRequester>()};val groupRequests=remember(categories){categories.map{FocusRequester()}}
 val days=remember(candidate.id,app.epg.revision,now){app.epg.availableDays(candidate)}
 LaunchedEffect(days){if(epgDay !in days)epgDay=0}
 val epgPrograms=remember(candidate,epgDay,app.epg.revision,now){app.epg.programsForDay(candidate,epgDay)}
 val initialProgram=epgPrograms.indexOfFirst{it.stop>now}.coerceAtLeast(0)
 val epgList=remember(candidate.id,epgDay,epgPrograms.isEmpty()){LazyListState(initialProgram)}
 var epgFocusStart by remember(candidate.id,epgDay){mutableStateOf<Long?>(null)}
 val latestEpgList by rememberUpdatedState(epgList)
 val latestEpgFocusStart by rememberUpdatedState(epgFocusStart)
 var dateFocus by remember(candidate.id){mutableIntStateOf(epgDay)}
 val dateList=remember(candidate.id){LazyListState(days.indexOf(epgDay).coerceAtLeast(0))}
 val dateRequests=remember(candidate.id,days){days.associateWith{FocusRequester()}}
 // Keep the selected date in view no matter whether it changed from the date rail or a left/right shortcut.
 LaunchedEffect(epgDay,days,navigation.pane){
  if(navigation.pane!=BrowserPane.PROGRAMMES)return@LaunchedEffect
  val at=days.indexOf(epgDay)
  if(at>=0){withFrameNanos{};dateList.animateScrollToItem(at)}
 }
 val epgRequests=remember{mutableStateMapOf<Long,FocusRequester>()}
 val browserActivity=checkNotNull(androidx.activity.compose.LocalActivity.current as? TvActivity)
 val diagnostics by rememberUpdatedState({org.json.JSONObject().put("epgDay",epgDay).put("pane",navigation.pane.wire).put("region",navigation.region.wire).put("group",activeGroup?:"").put("candidate",candidate.id).put("playing",current.id).put("epgFirst",epgList.firstVisibleItemIndex).put("epgOffset",epgList.firstVisibleItemScrollOffset).put("channelFirst",list.firstVisibleItemIndex).put("channelOffset",list.firstVisibleItemScrollOffset)})
 DisposableEffect(browserActivity){browserActivity.browserDiagnostics={diagnostics()};onDispose{browserActivity.browserDiagnostics=null}}
 var focusJob by remember{mutableStateOf<Job?>(null)}
 fun focusChannel(id:String,closeAdjacent:Boolean=false){focusJob?.cancel();navigation=navigation.focusChannels(closeAdjacent);focusJob=scope.launch{
  withFrameNanos{}
  val choices=latestChannels
  val index=choices.indexOfFirst{it.id==id}.takeIf{it>=0}?:choices.indexOfFirst{it.id==current.id}.coerceAtLeast(0);val target=choices.getOrNull(index)?:return@launch
  candidateId=target.id
  snapshotFlow{list.layoutInfo.totalItemsCount>0}.first{it}
  list.revealFocusRow(index)
  snapshotFlow{list.layoutInfo.visibleItemsInfo.any{it.key==target.id}&&requests.containsKey(target.id)}.first{it}
  withFrameNanos{};if(navigation.region.wire=="channels")requests[target.id]?.requestFocus()
 }}
 fun openGroups(){focusJob?.cancel();navigation=BrowserNavigation.Groups;focusJob=scope.launch{val at=categories.indexOf(activeGroup).coerceAtLeast(0);snapshotFlow{groupList.layoutInfo.totalItemsCount>0}.first{it};groupList.revealFocusRow(at);withFrameNanos{};withFrameNanos{};if(navigation.region.wire=="groups")groupRequests[at].requestFocus()}}
 fun focusEpg(preferLive:Boolean=false){focusJob?.cancel();navigation=BrowserNavigation.Programmes;focusJob=scope.launch{
  while(!hostView.hasWindowFocus())withFrameNanos{}
  withFrameNanos{}
  val programs=app.epg.programsForDay(candidate,epgDay)
  val layout=latestEpgList.layoutInfo
  val visible=layout.visibleItemsInfo.filter{it.offset>=layout.viewportStartOffset+layout.beforeContentPadding&&it.offset+it.size<=layout.viewportEndOffset-layout.afterContentPadding}
  val saved=programs.indexOfFirst{it.start==latestEpgFocusStart}
  val live=programs.indexOfFirst{System.currentTimeMillis() in it.start until it.stop}
  val index=if(preferLive)live.takeIf{it>=0}?:programs.indexOfFirst{it.stop>System.currentTimeMillis()}.coerceAtLeast(0)else if(visible.any{it.index==saved})saved else visible.firstOrNull()?.index?:latestEpgList.firstVisibleItemIndex
  if(preferLive&&programs.isNotEmpty()&&visible.none{it.index==index}){latestEpgList.revealFocusRow(index);withFrameNanos{}}
  val target=programs.getOrNull(index.coerceIn(0,programs.lastIndex.coerceAtLeast(0)))
  if(target!=null){snapshotFlow{epgRequests[target.start]}.first{it!=null};withFrameNanos{};if(navigation.region.wire=="epg")epgRequests[target.start]?.requestFocus()}else if(navigation.region.wire=="epg"){navigation=BrowserNavigation.Dates;withFrameNanos{};dateRequests[epgDay]?.requestFocus()}
 }}
 fun openEpg(){epgDay=0;navigation=BrowserNavigation.Programmes;focusEpg(preferLive=true)}
 fun focusDate(day:Int){focusJob?.cancel();navigation=BrowserNavigation.Dates;focusJob=scope.launch{
  val at=days.indexOf(day).coerceAtLeast(0);snapshotFlow{dateList.layoutInfo.totalItemsCount>0}.first{it};dateList.revealFocusRow(at);withFrameNanos{};withFrameNanos{};if(navigation.region.wire=="dates")dateRequests[day]?.requestFocus()
 }}
 fun focusDates(){focusDate(epgDay)}
 fun selectDate(delta:Int){
  if(days.size<2)return
  val at=days.indexOf(epgDay).coerceAtLeast(0);val next=(at+delta).coerceIn(0,days.lastIndex)
  if(next==at)return
  val stayOnDates=navigation.region==BrowserRegion.DATES
  epgDay=days[next];dateFocus=days[next]
  if(stayOnDates)focusDate(days[next])
  else focusEpg(preferLive=true)
 }
 fun horizontal(delta:Int){
 if(delta!=0&&navigation.pane==BrowserPane.PROGRAMMES){
  when(navigation.region){
   BrowserRegion.DATES->when{
    delta<0&&epgDay==days.firstOrNull()->focusEpg()
    delta>0&&epgDay==days.lastOrNull()->focusEpg()
    else->selectDate(delta)
   }
   // The EPG keeps focus on its programme list. Left/right switch the day
   // directly; up/down stay inside the programme list. Only BACK leaves it.
   BrowserRegion.PROGRAMMES->if(delta!=0)selectDate(delta)
   BrowserRegion.CHANNELS->if(delta<0)focusChannel(candidateId,closeAdjacent=true)else focusEpg()
   BrowserRegion.GROUPS->Unit
  }
  return
 }
 when(navigation.horizontal(delta,app.epg.programsForDay(candidate,epgDay).isEmpty())){
  BrowserMove.NONE->Unit
  BrowserMove.CHANNELS->focusChannel(candidate.id,closeAdjacent=true)
  BrowserMove.GROUPS->openGroups()
  BrowserMove.PROGRAMMES->if(navigation.region==BrowserRegion.CHANNELS)openEpg()else focusEpg()
  BrowserMove.DATES->focusDates()
 }}
 // All three columns and the date rail use the same viewport-based input handoff.
 fun resumeVisible():Boolean{
  val region=navigation.region
  val index:Int?;val old:Int;val request:FocusRequester?
  when(region){
   BrowserRegion.CHANNELS->{old=channels.indexOfFirst{it.id==candidateId};index=list.visibleFocusIndex(old);request=index?.let{channels.getOrNull(it)?.id?.let{requests[it]}}}
   BrowserRegion.GROUPS->{old=groupFocus;index=groupList.visibleFocusIndex(old);request=index?.let{groupRequests.getOrNull(it)}}
   BrowserRegion.PROGRAMMES->{old=epgPrograms.indexOfFirst{it.start==epgFocusStart};index=epgList.visibleFocusIndex(old);request=index?.let{epgPrograms.getOrNull(it)?.start?.let{epgRequests[it]}}}
   BrowserRegion.DATES->{old=days.indexOf(dateFocus);index=dateList.visibleFocusIndex(old);request=index?.let{days.getOrNull(it)?.let{dateRequests[it]}}}
  }
  if(index==old||request==null)return false
  focusJob?.cancel()
  return runCatching{request.requestFocus();true}.getOrDefault(false)
 }
 fun backPane():Boolean{
  if(navigation.pane!=BrowserPane.PROGRAMMES)return false
  focusChannel(candidateId,closeAdjacent=true)
  return true
 }
 SideEffect {
  controller.move = { horizontal(it) }
  controller.resumeVisible = { resumeVisible() }
  controller.back = { backPane() }
  browserActivity.browserGesture = { delta -> horizontal(delta) }
 }
 DisposableEffect(browserActivity){onDispose{browserActivity.browserGesture=null}}
 DisposableEffect(controller){onDispose{controller.move=null;controller.resumeVisible=null;controller.back=null}}
 LaunchedEffect(generation){if(navigation.pane.wire=="channels")focusChannel(current.id)}
 LaunchedEffect(controller.pending){if(controller.pending!=0){val delta=controller.pending;controller.pending=0;horizontal(delta)}}
 DisposableEffect(Unit){onDispose{focusJob?.cancel()}}
 val paneFocusManager=androidx.compose.ui.platform.LocalFocusManager.current
 BoxWithConstraints(modifier.fillMaxSize().padding(16.dp).onPreviewKeyEvent { e->
  if(e.nativeKeyEvent.action!=K.ACTION_DOWN)return@onPreviewKeyEvent false
  if(e.nativeKeyEvent.keyCode==K.KEYCODE_TAB){paneFocusManager.moveFocus(if(e.nativeKeyEvent.isShiftPressed)androidx.compose.ui.focus.FocusDirection.Up else androidx.compose.ui.focus.FocusDirection.Down);return@onPreviewKeyEvent true}
  if(navigation.pane==BrowserPane.PROGRAMMES){
   val action=TvInput.action(e.nativeKeyEvent.keyCode)
   if(action==InputAction.DOWN&&(navigation.region==BrowserRegion.DATES||(navigation.region==BrowserRegion.CHANNELS&&candidateId==channels.lastOrNull()?.id))){focusEpg();return@onPreviewKeyEvent true}
   if(navigation.region==BrowserRegion.PROGRAMMES&&epgPrograms.isNotEmpty()){
    if(action==InputAction.UP&&epgFocusStart==epgPrograms.first().start)return@onPreviewKeyEvent true
    if(action==InputAction.DOWN&&epgFocusStart==epgPrograms.last().start)return@onPreviewKeyEvent true
   }
  }
  val direction=TvInput.horizontal(e.nativeKeyEvent.keyCode)
  if(direction!=null){horizontal(direction);true}else false
 }){
  val showLogos=app.prefs.showChannelLogos
  val widths=browserWidths(maxWidth.value,fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale)
  val gap=0.dp;val groupWidth=widths.group.dp;val channelWidth=widths.channel.dp;val dateWidth=widths.date.dp;val epgWidth=widths.programme.dp
  val hintWidth=(18.dp*androidx.compose.ui.platform.LocalDensity.current.fontScale).coerceIn(18.dp,30.dp)
  val reduced=app.prefs.reduceMotion
  val largeNarrowDate=dateWidth<72.dp&&androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
  val anchor=if(navigation.pane.wire=="groups")groupWidth else 0.dp
  val anchorPosition=animateFloatAsState(anchor.value,if(reduced)snap()else tween(160,easing=FastOutSlowInEasing),label="browser-anchor")
  val browserDensity=androidx.compose.ui.platform.LocalDensity.current
  if(navigation.pane.wire=="epg"){
   ProgrammeSideSheet(
    width=channelWidth,candidate=candidate,channels=channels,showLogos=showLogos,list=list,requests=requests,
    programs=epgPrograms,epgList=epgList,epgRequests=epgRequests,days=days,dateList=dateList,dateRequests=dateRequests,epgDay=epgDay,now=now,region=navigation.region,
    onChannelFocus={c->navigation=BrowserNavigation(BrowserPane.PROGRAMMES,BrowserRegion.CHANNELS);candidateId=c.id},
    onChannelClick=onSelect,
    onProgramFocus={p->navigation=BrowserNavigation.Programmes;epgFocusStart=p.start},
    onProgramClick=onProgram,
    onDate={day->epgDay=day;dateFocus=day;navigation=BrowserNavigation.Programmes;focusEpg(preferLive=true)},
    onDateFocus={day->navigation=BrowserNavigation.Dates;dateFocus=day}
   )
   return@BoxWithConstraints
  }
  val panelWidth=channelWidth+when(navigation.pane.wire){"groups"->groupWidth;else->0.dp}
  val panelExtent=animateFloatAsState(panelWidth.value,if(reduced)snap()else tween(160,easing=FastOutSlowInEasing),label="browser-panel")
  Box(Modifier.width(panelWidth).fillMaxHeight().playerSidebarSurface{with(browserDensity){panelExtent.value.dp.toPx()}})
  if(navigation.pane.wire!="epg")AnimatedVisibility(visible=navigation.pane.wire=="groups",enter=fadeIn(tween(if(reduced)0 else 120)),exit=fadeOut(tween(if(reduced)0 else 100))){Column(Modifier.width(groupWidth).fillMaxHeight().padding(12.dp)){
   BrowserHeading(app.channels.sources.firstOrNull{it.id==sourceId}?.name?:"订阅")
   LazyColumn(flingBehavior=appFlingBehavior(),state=groupList,modifier=Modifier.continuousWheel(groupList),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){itemsIndexed(categories){i,g->TvButton(g?:"全部频道",Modifier.fillMaxWidth().focusEdges(up=i==0,down=i==categories.lastIndex).focusProperties{canFocus=navigation.region.wire=="groups";if(i==0)up=FocusRequester.Cancel;if(i==categories.lastIndex)down=FocusRequester.Cancel}.focusRequester(groupRequests[i]),selected=g==activeGroup,kind="playerRow",onFocus={if(navigation.region==BrowserRegion.GROUPS){groupFocus=i;if(activeGroup!=g){activeGroup=g;onGroup(g);candidateId=sourceChannels.firstOrNull{g==null||it.groupName==g}?.id.orEmpty();scope.launch{list.scrollToItem(0)}}}},onClick={groupFocus=i;activeGroup=g;onGroup(g);candidateId=sourceChannels.firstOrNull{g==null||it.groupName==g}?.id.orEmpty();navigation=BrowserNavigation.Groups})}}
  }
  }
  Column(Modifier.offset{IntOffset(with(browserDensity){anchorPosition.value.dp.roundToPx()},0)}.width(channelWidth).fillMaxHeight().padding(start=12.dp,top=12.dp,end=hintWidth+2.dp,bottom=12.dp)){
   BrowserHeading(activeGroup?:if(app.channels.sources.count{it.enabled}>1)"本订阅全部频道"else"全部频道")
   LazyColumn(flingBehavior=appFlingBehavior(),state=list,modifier=Modifier.continuousWheel(list),contentPadding=PaddingValues(start=12.dp,end=8.dp,top=12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
    itemsIndexed(channels,key={_,it->it.id}){index,c->val request=remember(c.id){FocusRequester()}
     DisposableEffect(c.id){requests[c.id]=request;onDispose{if(requests[c.id]===request)requests.remove(c.id)}}
     TvSurface(Modifier.fillMaxWidth().semantics{stateDescription=if(c.id==current.id)if(playingPaused)"已暂停"else if(playingReplay)"正在播放回放"else"正在播放直播"else if(c.id==candidate.id)"正在浏览"else""}.heightIn(min=64.dp).focusEdges(up=index==0,down=index==channels.lastIndex).focusProperties{canFocus=navigation.region.wire=="channels";if(index==0)up=FocusRequester.Cancel;if(index==channels.lastIndex)down=FocusRequester.Cancel}.focusRequester(request),kind="channelRow",selected=c.id==candidate.id&&!InteractionStyle.pointer,onFocus={if(navigation.region.wire=="channels")candidateId=c.id},onClick={onSelect(c)}){
      Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
       if(showLogos)PlayerLogo(c,Modifier.width(widths.logo.dp).height(38.dp))
       Column(Modifier.weight(1f).padding(start=if(showLogos)10.dp else 0.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){AppText(Ui.displayName(c.name),Modifier.weight(1f),fontSize=InteractionStyle.body,color=NextTvStyle.Text,maxLines=1,overflow=TextOverflow.Ellipsis);if(c.id==current.id)AppText(if(playingPaused)"Ⅱ"else"▶",Modifier.padding(start=6.dp).semantics{contentDescription=if(playingPaused)"已暂停"else if(playingReplay)"正在播放回放"else"正在播放"},fontSize=InteractionStyle.caption,color=NextTvStyle.AccentText)}
        val program=app.epg.nowNext(c).first
        AppText(program?.title?:"暂无节目信息",fontSize=InteractionStyle.caption,color=NextTvStyle.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        val progress=programmeProgress(program,now)
        Box(Modifier.fillMaxWidth().padding(top=5.dp).height(3.dp).background(NextTvStyle.PlayerProgressTrack,RoundedCornerShape(2.dp))){Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(NextTvStyle.PlayerProgressFill,RoundedCornerShape(2.dp)))}
       }
      }
     }
    }
   }
  }
  if(navigation.pane.wire!="epg")BrowserEdgeHint(hintWidth,Modifier.offset{IntOffset(with(browserDensity){(anchorPosition.value.dp+channelWidth-hintWidth-2.dp).roundToPx()},0)})
  if(navigation.pane.wire=="epg"){
   Column(Modifier.offset{IntOffset(with(browserDensity){(anchorPosition.value.dp+channelWidth+gap).roundToPx()},0)}.width(epgWidth).fillMaxHeight().padding(start=0.dp,top=12.dp,end=12.dp,bottom=12.dp)){
    BrowserHeading(Ui.displayName(candidate.name))
    val programs=app.epg.programsForDay(candidate,epgDay)
    if(programs.isEmpty())Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){AppText("暂无节目单",textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=InteractionStyle.body,color=NextTvStyle.Muted)}
    LazyColumn(flingBehavior=appFlingBehavior(),state=epgList,modifier=Modifier.continuousWheel(epgList),contentPadding=PaddingValues(start=6.dp,end=12.dp,top=12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){itemsIndexed(programs,key={_,it->"${it.channelKey}:${it.start}:${it.stop}"}){index,p->
     val request=remember(p.start){FocusRequester()};DisposableEffect(p.start,request){epgRequests[p.start]=request;onDispose{if(epgRequests[p.start]===request)epgRequests.remove(p.start)}}
     val live=now in p.start until p.stop
     val badge=if(live)"直播中"else if(p.stop<=now&&candidate.playbackRoutes.any{replayAddress(it,p,now)!=null})"回放"else""
     TvSurface(Modifier.fillMaxWidth().heightIn(min=48.dp).focusRequester(request).focusEdges(up=index==0,down=index==programs.lastIndex).focusProperties{canFocus=navigation.region.wire=="epg";if(index==0)up=FocusRequester.Cancel;if(index==programs.lastIndex)down=FocusRequester.Cancel},kind="playerRow",selected=live,onFocus={if(navigation.region.wire=="epg")epgFocusStart=p.start},onClick={onProgram(candidate,p)}){
      Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
       AppText(Ui.hhmm(p.start),Modifier.width(48.dp*androidx.compose.ui.platform.LocalDensity.current.fontScale),fontSize=InteractionStyle.caption,color=NextTvStyle.Muted,maxLines=1,textAlign=androidx.compose.ui.text.style.TextAlign.End,fontFeatureSettings="tnum")
       AppText(p.title,Modifier.weight(1f).padding(start=10.dp,end=10.dp),fontSize=InteractionStyle.body,color=NextTvStyle.Text,maxLines=1,overflow=TextOverflow.Ellipsis)
       if(badge.isNotBlank())BrowserBadge(badge,live)
      }
     }
    }}
   }
   Column(Modifier.offset{IntOffset(with(browserDensity){(anchorPosition.value.dp+channelWidth+epgWidth).roundToPx()},0)}.width(dateWidth).fillMaxHeight().padding(vertical=12.dp,horizontal=if(largeNarrowDate)2.dp else 4.dp)){
    BrowserHeading("日期",centered=true)
    LazyColumn(flingBehavior=appFlingBehavior(),state=dateList,modifier=Modifier.continuousWheel(dateList),contentPadding=PaddingValues(horizontal=if(largeNarrowDate)2.dp else 6.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){items(days,key={it}){day->
     val date=remember(day,now){java.util.Calendar.getInstance().apply{timeInMillis=now;add(java.util.Calendar.DAY_OF_YEAR,day)}.time}
     TvSurface(Modifier.fillMaxWidth().heightIn(min=60.dp).focusRequester(dateRequests.getValue(day)).focusEdges(up=day==days.firstOrNull(),down=day==days.lastOrNull()).focusProperties{canFocus=navigation.region.wire=="dates";if(day==days.firstOrNull())up=FocusRequester.Cancel;if(day==days.lastOrNull())down=FocusRequester.Cancel},kind="date",selected=day==epgDay,onFocus={if(navigation.region==BrowserRegion.DATES)dateFocus=day},onClick={dateFocus=day;epgDay=day;focusEpg()}){
      Column(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally){
       AppText(if(day==0)"今天"else java.text.SimpleDateFormat("EEE",java.util.Locale.SIMPLIFIED_CHINESE).format(date),fontSize=InteractionStyle.body,maxLines=1,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
       AppText(java.text.SimpleDateFormat("MM-dd",java.util.Locale.ROOT).format(date),fontSize=InteractionStyle.caption,color=NextTvStyle.Muted,maxLines=1,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
      }
     }
    }}
   }
  }
 }

 // Group selection updates candidates; wait for new LazyColumn keys instead of old focus references.
 LaunchedEffect(activeGroup){if(navigation.region.wire=="channels"&&navigation.pane.wire=="channels")focusChannel(candidateId)}
}
