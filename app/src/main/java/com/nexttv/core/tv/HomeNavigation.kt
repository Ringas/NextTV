@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nexttv.core.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexttv.R

/** Animation state is read only by this navigation's render layer, never by the home page. */
@Composable internal fun HomeNavigation(tab:Int,focused:Boolean,requests:List<FocusRequester>,modifier:Modifier=Modifier,homeCompositions:()->Int,onSelect:(Int)->Unit){
 val positions=remember{mutableStateMapOf<Int,Pair<Float,Float>>()}
 val density=LocalDensity.current
 val reduced=com.nexttv.core.App.get().prefs.reduceMotion
 Box(modifier.height(56.dp).glassTint(NextTvStyle.Nav,RoundedCornerShape(InteractionStyle.pillRadius),elevation=10.dp).padding(5.dp)){
  if(positions.size==3){
   val target=positions[tab]?.first?:0f
   val width=positions.values.maxOf{it.second}
   // Retarget the same spring; neither focus ownership nor a page return changes capsule size.
   val x=animateFloatAsState(target,if(reduced)snap()else spring(dampingRatio=.78f,stiffness=1100f),label="nav-translation")
   val host=androidx.activity.compose.LocalActivity.current as? TvActivity
   val sample by rememberUpdatedState({org.json.JSONObject().put("tab",tab).put("x",x.value).put("target",target).put("scale",1f).put("width",width).put("height",46f).put("motionOff",reduced).put("homeCompositions",homeCompositions())})
   DisposableEffect(host){host?.navigationDiagnostics={sample()};onDispose{host?.navigationDiagnostics=null}}
   Box(Modifier.width(with(density){width.toDp()}).height(46.dp).graphicsLayer{translationX=x.value}.glassTint(if(focused)NextTvStyle.NavFocused else NextTvStyle.NavActive,RoundedCornerShape(InteractionStyle.pillInnerRadius)))
  }
  Row{listOf("频道","收藏","设置").forEachIndexed{i,label->
   TvSurface(Modifier.height(46.dp).focusRequester(requests[i]).focusEdges(up=true,left=i==0,right=i==2).focusProperties{up=FocusRequester.Cancel;left=if(i==0)FocusRequester.Cancel else requests[i-1];right=if(i==2)FocusRequester.Cancel else requests[i+1]}.onGloballyPositioned{val value=it.positionInParent().x to it.size.width.toFloat();if(positions[i]!=value)positions[i]=value},kind="nav",selected=tab==i,focusReturn=requests[i],onFocus={onSelect(i)},onClick={onSelect(i);requests[i].requestFocus()}){
    Row(Modifier.padding(horizontal=18.dp).fillMaxHeight(),verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.size(28.dp).glassTint(NextTvStyle.ChannelNumber,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){
      Image(painterResource(listOf(R.drawable.ic_nav_channels,R.drawable.ic_nav_favorites,R.drawable.ic_nav_settings)[i]),null,Modifier.size(18.dp),colorFilter=ColorFilter.tint(if(tab==i)NextTvStyle.AccentText else NextTvStyle.Text))
     }
     AppText(label,Modifier.padding(start=8.dp),fontSize=InteractionStyle.body*(minOf(density.fontScale,2f)/density.fontScale),fontWeight=FontWeight.Bold)
    }
   }
  }}
 }
}
