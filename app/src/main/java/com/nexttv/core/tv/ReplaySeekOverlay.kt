package com.nexttv.core.tv
import androidx.compose.foundation.background
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
internal data class ReplaySeekFeedback(val target:Long,val duration:Long,val delta:Long)
internal fun replaySeekTarget(position:Long,delta:Long,duration:Long):Long=(position.coerceAtLeast(0)+delta).coerceIn(0,(duration-1000).coerceAtLeast(0))
internal fun replayElapsed(millis:Long):String{val seconds=millis.coerceAtLeast(0)/1000;return if(seconds>=3600)"%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)else"%02d:%02d".format(seconds/60,seconds%60)}
@Composable internal fun ReplaySeekOverlay(feedback:ReplaySeekFeedback,modifier:Modifier){
 BoxWithConstraints(modifier.fillMaxWidth().padding(bottom=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.05f),contentAlignment=Alignment.Center){
  Column(Modifier.width(maxWidth*playerPanelFraction(maxWidth.value)).glassReadingPanel(RoundedCornerShape(InteractionStyle.panelRadius)).padding(horizontal=22.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){AppText(if(feedback.delta>0)"快进 ${feedback.delta/1000} 秒 ▷▷"else"◁◁ 快退 ${-feedback.delta/1000} 秒",fontWeight=FontWeight.SemiBold,color=NextTvStyle.Text);AppText(replayElapsed(feedback.target)+" / "+replayElapsed(feedback.duration),color=NextTvStyle.Muted)}
   Box(Modifier.fillMaxWidth().progressSemantics((feedback.target.toFloat()/feedback.duration.coerceAtLeast(1)).coerceIn(0f,1f)).height(4.dp).background(NextTvStyle.Stroke,RoundedCornerShape(2.dp))){Box(Modifier.fillMaxWidth((feedback.target.toFloat()/feedback.duration.coerceAtLeast(1)).coerceIn(0f,1f)).fillMaxHeight().background(NextTvStyle.Accent,RoundedCornerShape(2.dp)))}
  }
 }
}
