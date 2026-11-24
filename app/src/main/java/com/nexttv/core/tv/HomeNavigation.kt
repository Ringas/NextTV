@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nexttv.core.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Nocturne: plain text tabs with one sliding ivory light line, never a capsule. */
@Composable internal fun HomeNavigation(tab:Int,focused:Boolean,requests:List<FocusRequester>,modifier:Modifier=Modifier,homeCompositions:()->Int,onSelect:(Int)->Unit){
 val positions=remember{mutableStateMapOf<Int,Pair<Float,Float>>()}
 val density=LocalDensity.current
 val reduced=com.nexttv.core.App.get().prefs.reduceMotion
 val indicatorWidth=with(density){InteractionStyle.navIndicatorWidth.toPx()}
 Box(modifier.height(InteractionStyle.navHeight)){
  if(positions.size==3){
   val target=((positions[tab]?.first?:0f)+(positions[tab]?.second?:0f)/2f-indicatorWidth/2f).coerceAtLeast(0f)
   val x=animateFloatAsState(target,if(reduced)snap()else tween(220,easing=FastOutSlowInEasing),label="tab-light")
   val host=androidx.activity.compose.LocalActivity.current as? TvActivity
   val sample by rememberUpdatedState({org.json.JSONObject().put("tab",tab).put("x",x.value).put("target",target).put("scale",1f).put("width",positions[tab]?.second?:0f).put("height",InteractionStyle.navHeight.value).put("motionOff",reduced).put("homeCompositions",homeCompositions())})
   DisposableEffect(host){host?.navigationDiagnostics={sample()};onDispose{host?.navigationDiagnostics=null}}
   Box(Modifier.align(Alignment.BottomStart).offset{with(density){IntOffset(x.value.roundToInt(),(-3).dp.roundToPx())}}.width(InteractionStyle.navIndicatorWidth).height(InteractionStyle.navIndicatorHeight).background(NextTvStyle.Ivory.copy(alpha=if(focused)1f else .55f),RoundedCornerShape(1.dp)))
  }
  Row(Modifier.fillMaxHeight()){listOf("频道","收藏","设置").forEachIndexed{i,label->
   TvSurface(Modifier.fillMaxHeight().focusRequester(requests[i]).focusEdges(up=true,left=i==0,right=i==2).focusProperties{up=FocusRequester.Cancel;left=if(i==0)FocusRequester.Cancel else requests[i-1];right=if(i==2)FocusRequester.Cancel else requests[i+1]}.onGloballyPositioned{val value=it.positionInParent().x to it.size.width.toFloat();if(positions[i]!=value)positions[i]=value},kind="nav",selected=tab==i,focusReturn=requests[i],onFocus={onSelect(i)},onClick={onSelect(i);requests[i].requestFocus()}){
    Box(Modifier.fillMaxHeight().padding(horizontal=18.dp),contentAlignment=Alignment.Center){
     AppText(label,fontSize=InteractionStyle.body,fontWeight=if(tab==i)FontWeight.SemiBold else FontWeight.Normal,color=if(tab==i)NextTvStyle.Ivory.copy(alpha=if(focused)1f else .72f) else NextTvStyle.Muted,maxLines=1)
    }
   }
  }}
 }
}
