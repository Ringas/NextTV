@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nexttv.core.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** APTV/iPlayTV direction: plain text tabs with one flat moving selection pill, no icon tiles. */
@Composable internal fun HomeNavigation(tab:Int,focused:Boolean,requests:List<FocusRequester>,modifier:Modifier=Modifier,homeCompositions:()->Int,onSelect:(Int)->Unit){
 val positions=remember{mutableStateMapOf<Int,Pair<Float,Float>>()}
 val density=LocalDensity.current
 val reduced=com.nexttv.core.App.get().prefs.reduceMotion
 Box(modifier.height(46.dp)){
  if(positions.size==3){
   val target=positions[tab]?.first?:0f
   val width=positions.values.maxOf{it.second}
   val x=animateFloatAsState(target,if(reduced)snap()else spring(dampingRatio=.82f,stiffness=1200f),label="tab-translation")
   val host=androidx.activity.compose.LocalActivity.current as? TvActivity
   val sample by rememberUpdatedState({org.json.JSONObject().put("tab",tab).put("x",x.value).put("target",target).put("scale",1f).put("width",width).put("height",46f).put("motionOff",reduced).put("homeCompositions",homeCompositions())})
   DisposableEffect(host){host?.navigationDiagnostics={sample()};onDispose{host?.navigationDiagnostics=null}}
   Box(Modifier.align(Alignment.CenterStart).width(with(density){width.toDp()}).height(34.dp).graphicsLayer{translationX=x.value}.background(if(focused)NextTvStyle.TabActive else NextTvStyle.TabActive.copy(alpha=.88f),RoundedCornerShape(InteractionStyle.tabRadius)))
  }
  Row(Modifier.fillMaxHeight()){listOf("频道","收藏","设置").forEachIndexed{i,label->
   TvSurface(Modifier.fillMaxHeight().focusRequester(requests[i]).focusEdges(up=true,left=i==0,right=i==2).focusProperties{up=FocusRequester.Cancel;left=if(i==0)FocusRequester.Cancel else requests[i-1];right=if(i==2)FocusRequester.Cancel else requests[i+1]}.onGloballyPositioned{val value=it.positionInParent().x to it.size.width.toFloat();if(positions[i]!=value)positions[i]=value},kind="nav",selected=tab==i,focusReturn=requests[i],onFocus={onSelect(i)},onClick={onSelect(i);requests[i].requestFocus()}){
    Box(Modifier.fillMaxHeight().padding(horizontal=18.dp),contentAlignment=Alignment.Center){
     AppText(label,fontSize=InteractionStyle.body,fontWeight=if(tab==i)FontWeight.Bold else FontWeight.Medium,color=if(tab==i)NextTvStyle.OnTabActive else NextTvStyle.Muted,maxLines=1)
    }
   }
  }}
 }
}
