@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.nexttv.core.tv

import android.view.KeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import com.nexttv.core.data.playbackFraction
import kotlin.math.roundToLong

/** Direct pointer input is hosted by the information panel, so a thin line can
 * have a usable hit area without changing the information layout or clipping taps. */
internal class ReplayTimelineInput {
 var panelOrigin=Offset.Zero
 var bounds:Rect?=null
 var seek:((Float)->Unit)?=null
 var dragging:((Boolean)->Unit)?=null
 var focus:(()->Unit)?=null
}
internal fun Modifier.replayTimelineInput(input:ReplayTimelineInput,enabled:Boolean):Modifier=if(!enabled)this else onGloballyPositioned{input.panelOrigin=it.boundsInWindow().topLeft}.pointerInput(input){
 awaitEachGesture{
  val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
  val bounds=input.bounds
  val point=down.position+input.panelOrigin
  if(bounds==null||!bounds.contains(point)||currentEvent.buttons.isSecondaryPressed)return@awaitEachGesture
  fun seekAt(position:Offset){input.seek?.invoke(((position.x+input.panelOrigin.x-bounds.left)/bounds.width.coerceAtLeast(1f)).coerceIn(0f,1f))}
  down.consume();input.focus?.invoke();input.dragging?.invoke(true);seekAt(down.position)
  try{
   while(true){
    val event=awaitPointerEvent(PointerEventPass.Initial)
    val change=event.changes.firstOrNull{it.id==down.id}?:break
    change.consume();seekAt(change.position)
    if(!change.pressed)break
   }
  }finally{input.dragging?.invoke(false)}
 }
}

/** Same thin programme progress line; the larger transparent input target never affects layout. */
@Composable internal fun ReplayTimeline(position:Long,duration:Long,enabled:Boolean,request:FocusRequester,input:ReplayTimelineInput,returnToAction:()->Unit,onSeek:(Long)->Unit,modifier:Modifier=Modifier){
 var focused by remember{mutableStateOf(false)}
 var directDragging by remember{mutableStateOf(false)}
 var markerVisible by remember{mutableStateOf(false)}
 var markerEpoch by remember{mutableIntStateOf(0)}
 var fraction by remember{mutableFloatStateOf(playbackFraction(position,duration))}
 LaunchedEffect(position,duration){fraction=playbackFraction(position,duration)}
 val interactions=remember{MutableInteractionSource()}
 val dragging by interactions.collectIsDraggedAsState()
 val pressed by interactions.collectIsPressedAsState()
 val visibleFocus=(focused&&!InteractionStyle.pointer)||dragging||pressed||directDragging
 val reduced=com.nexttv.core.App.get().prefs.reduceMotion
 val thickness=animateFloatAsState(if(visibleFocus)4f else 3f,if(reduced||!visibleFocus)snap()else tween(60),label="replay-line-emphasis")
 val host=androidx.activity.compose.LocalActivity.current as? TvActivity
 val hitTop=with(LocalDensity.current){32.dp.toPx()}
 val hitBottom=with(LocalDensity.current){12.dp.toPx()}
 DisposableEffect(host,enabled){onDispose{host?.directPointerRegion=null;input.bounds=null;input.seek=null;input.dragging=null;input.focus=null}}
 val textMeasurer=rememberTextMeasurer()
 val markerLabel=replayElapsed((fraction*duration).roundToLong())+" / "+replayElapsed(duration)
 val markerText=textMeasurer.measure(AnnotatedString(markerLabel),style=TextStyle(color=NextTvStyle.Text,fontSize=InteractionStyle.caption,fontWeight=FontWeight.Medium))
 LaunchedEffect(markerEpoch,directDragging,dragging,pressed){if(!directDragging&&!dragging&&!pressed){delay(900);markerVisible=false}}
 LaunchedEffect(enabled){if(!enabled)markerVisible=false}
 fun change(value:Float){fraction=value.coerceIn(0f,1f);markerVisible=true;markerEpoch++;onSeek((fraction*duration).roundToLong())}
 SideEffect{input.seek={change(it)};input.dragging={directDragging=it};input.focus={runCatching{request.requestFocus()}}}
 Box(modifier.onGloballyPositioned{if(enabled){val bounds=it.boundsInWindow();input.bounds=Rect(bounds.left,bounds.center.y-hitTop,bounds.right,bounds.center.y+hitBottom);host?.directPointerRegion=input.bounds}},contentAlignment=Alignment.Center){
  if(enabled)Slider(value=fraction,onValueChange={change(it)},interactionSource=interactions,modifier=Modifier.fillMaxWidth().requiredHeight(40.dp).focusRequester(request).onFocusChanged{focused=it.isFocused;if(!it.hasFocus&&!directDragging)markerVisible=false}.focusProperties{up=FocusRequester.Cancel;down=FocusRequester.Cancel}.onPreviewKeyEvent{event->
   val key=event.nativeKeyEvent
   when(TvInput.action(key.keyCode)){
    InputAction.UP->true
    InputAction.DOWN->{if(key.action==KeyEvent.ACTION_DOWN&&key.repeatCount==0)returnToAction();true}
    InputAction.LEFT,InputAction.RIGHT->{if(key.action==KeyEvent.ACTION_DOWN){val delta=if(TvInput.action(key.keyCode)==InputAction.RIGHT)30000L else -30000L;change(playbackFraction(replaySeekTarget((fraction*duration).roundToLong(),delta,duration),duration))};true}
    else->false
   }
  }.semantics{contentDescription="回放进度";stateDescription=replayElapsed((fraction*duration).roundToLong())+"，共 "+replayElapsed(duration)},thumb={Spacer(Modifier.size(0.dp))},track={
   Canvas(Modifier.fillMaxWidth().height(3.dp)){
    val stroke=thickness.value.dp.toPx();val start=stroke/2;val end=(size.width-stroke/2).coerceAtLeast(start);val y=size.height/2
    drawLine(NextTvStyle.Stroke,Offset(start,y),Offset(end,y),strokeWidth=stroke,cap=StrokeCap.Round)
    if(fraction>0f)drawLine(if(visibleFocus)NextTvStyle.AccentText else NextTvStyle.Accent,Offset(start,y),Offset(start+(end-start)*fraction,y),strokeWidth=stroke,cap=StrokeCap.Round)
   }
  })else Box(Modifier.fillMaxSize().progressSemantics(fraction).background(NextTvStyle.Stroke,CircleShape)){Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(NextTvStyle.Accent,CircleShape))}
  if(enabled&&markerVisible)Canvas(Modifier.matchParentSize()){
   val markerX=(size.width*fraction).coerceIn(0f,size.width)
   drawCircle(NextTvStyle.Text,radius=3.dp.toPx(),center=Offset(markerX,size.height/2))
   val padX=8.dp.toPx();val padY=4.dp.toPx();val width=markerText.size.width+padX*2;val height=markerText.size.height+padY*2
   val x=(markerX-width/2).coerceIn(0f,(size.width-width).coerceAtLeast(0f));val y= -height-8.dp.toPx()
   drawRoundRect(NextTvStyle.MediaBadge,topLeft=Offset(x,y),size=Size(width,height),cornerRadius=CornerRadius(InteractionStyle.badgeRadius.toPx()))
   drawText(markerText,topLeft=Offset(x+padX,y+padY))
  }
 }
}
