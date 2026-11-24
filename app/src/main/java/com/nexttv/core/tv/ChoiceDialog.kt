@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nexttv.core.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal data class ChoiceOption(val id:String,val label:String,val detail:String="",val enabled:Boolean=true)

/** Constrain the viewport, not the scroll content; open at the actual selected item. */
@Composable internal fun ChoiceDialog(title:String,options:List<ChoiceOption>,selectedId:String?,onDismiss:()->Unit,uniformRows:Boolean=false,onInspect:((String)->Unit)?=null,singleLineDetails:Boolean=false,width:Dp=440.dp,onChoose:(String)->Unit){
 val rowHeight=(64f*LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
 val at=options.indexOfFirst{it.id==selectedId&&it.enabled}.takeIf{it>=0}?:options.indexOfFirst{it.enabled}.coerceAtLeast(0)
 val list=rememberLazyListState(initialFirstVisibleItemIndex=(at-1).coerceAtLeast(0))
 val requests=remember{mutableStateMapOf<String,FocusRequester>()}
 var initialized by remember{mutableStateOf(false)}
 val navigationSerial=remember{intArrayOf(0)}
 var focusedId by remember{mutableStateOf<String?>(null)}
 val identities=options.map{it.id to it.enabled}
 TvDialog(title,onDismiss,width=width,onKeyboardNavigate={
  val old=options.indexOfFirst{it.id==focusedId}
  val index=list.visibleFocusIndex(old){options.getOrNull(it)?.enabled==true}
  val request=index?.let{options.getOrNull(it)?.id?.let{requests[it]}}
  if(index==old||request==null)false else runCatching{request.requestFocus();true}.getOrDefault(false)
 }){
  val dialogView=LocalView.current
  val inWindowModal=LocalInWindowModal.current
  LaunchedEffect(identities){
   // Async detail/selection updates must not move the user's current focus.
   if(initialized&&options.any{it.id==focusedId&&it.enabled})return@LaunchedEffect
   if(initialized)navigationSerial[0]=0
   val target=options.getOrNull(at)?.takeIf{it.enabled}?:return@LaunchedEffect
   awaitModalFocusWindow(dialogView,inWindowModal)
   if(navigationSerial[0]!=0)return@LaunchedEffect
   // Start one row before selection so focus restoration does not trigger a second edge correction.
   val targetIndex=options.indexOf(target)
   if(list.layoutInfo.visibleItemsInfo.none{it.index==targetIndex})list.scrollToItem((targetIndex-1).coerceAtLeast(0))
   if(withTimeoutOrNull(2000){snapshotFlow{requests[target.id]}.first{it!=null}}!=null){if(!inWindowModal)withFrameNanos{};if(navigationSerial[0]==0)runCatching{requests[target.id]?.requestFocus()};initialized=true}
  }
  LazyColumn(flingBehavior=appFlingBehavior(),state=list,modifier=Modifier.fillMaxWidth().onPreviewKeyEvent{event->if(event.nativeKeyEvent.action==android.view.KeyEvent.ACTION_DOWN&&TvInput.action(event.nativeKeyEvent.keyCode) in listOf(InputAction.UP,InputAction.DOWN,InputAction.LEFT,InputAction.RIGHT))navigationSerial[0]++;false}.continuousWheel(list).weight(1f,false),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   items(options,key={it.id}){option->
    val request=remember(option.id){FocusRequester()}
    DisposableEffect(option.id,request){requests[option.id]=request;onDispose{if(requests[option.id]===request)requests.remove(option.id)}}
    TvSurface(Modifier.fillMaxWidth().focusRequester(request).focusEdges(up=option.id==options.firstOrNull{it.enabled}?.id,down=option.id==options.lastOrNull{it.enabled}?.id,left=true,right=true).focusProperties{canFocus=option.enabled;left=FocusRequester.Cancel;right=FocusRequester.Cancel;if(option.id==options.firstOrNull{it.enabled}?.id)up=FocusRequester.Cancel;if(option.id==options.lastOrNull{it.enabled}?.id)down=FocusRequester.Cancel}.semantics{if(selectedId!=null){selected=option.id==selectedId;role=Role.RadioButton};stateDescription=if(!option.enabled)"不可用"else if(option.id==selectedId)"已选择"else""},kind="categoryRow",selected=option.id==selectedId,enabled=option.enabled,onFocus={focusedId=option.id},longClickLabel=if(onInspect!=null)"查看完整地址"else null,onLongClick=onInspect?.let{inspect->{inspect(option.id)}},onClick={if(option.enabled)onChoose(option.id)}){
     Row(Modifier.fillMaxWidth().then(if(uniformRows)Modifier.height(rowHeight)else Modifier).padding(horizontal=14.dp,vertical=if(uniformRows)10.dp else 12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
      Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(if(uniformRows)2.dp else 0.dp)){AppText(option.label,fontSize=InteractionStyle.body,maxLines=if(uniformRows||singleLineDetails)1 else 2,overflow=TextOverflow.Ellipsis);if(option.detail.isNotBlank()||uniformRows)AppText(option.detail.ifBlank{" "},color=if(uniformRows||singleLineDetails)if(option.enabled)NextTvStyle.Muted else NextTvStyle.Disabled else Color.Unspecified,fontSize=InteractionStyle.caption,maxLines=if(uniformRows||singleLineDetails)1 else 2,overflow=TextOverflow.Ellipsis)}
     }
    }
   }
  }
 }
}
