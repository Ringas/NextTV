package com.nexttv.core.tv
import androidx.compose.foundation.background
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
internal data class ReplaySeekFeedback(val target:Long,val duration:Long,val delta:Long,val multiplier:Int=1)
internal const val REPLAY_SEEK_STEP_MS=10_000L
internal const val REPLAY_SEEK_HOLD_INTERVAL_MS=220L
/** Short presses stay at 1x; holding ramps 2x/4x/8x instead of fixed 30-second jumps. */
internal fun replayHoldMultiplier(heldMillis:Long):Int=when{heldMillis<700L->1;heldMillis<1600L->2;heldMillis<3000L->4;else->8}
/** Large accelerated offsets read better as minutes and hours than as raw seconds. */
internal fun replaySeekAmount(millis:Long):String{
 val total=kotlin.math.abs(millis)/1000
 val hours=total/3600
 val minutes=total/60%60
 val seconds=total%60
 return when{
  total<60->"$total 秒"
  hours>0&&minutes>0&&seconds>0->"$hours 小时 $minutes 分 $seconds 秒"
  hours>0&&minutes>0->"$hours 小时 $minutes 分"
  hours>0&&seconds>0->"$hours 小时 $seconds 秒"
  hours>0->"$hours 小时"
  seconds>0->"$minutes 分 $seconds 秒"
  else->"$minutes 分"
 }
}
internal fun replaySeekTarget(position:Long,delta:Long,duration:Long):Long=(position.coerceAtLeast(0)+delta).coerceIn(0,(duration-1000).coerceAtLeast(0))
internal fun replayElapsed(millis:Long):String{val seconds=millis.coerceAtLeast(0)/1000;return if(seconds>=3600)"%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)else"%02d:%02d".format(seconds/60,seconds%60)}
@Composable internal fun ReplaySeekOverlay(feedback:ReplaySeekFeedback,modifier:Modifier){
 BoxWithConstraints(modifier.fillMaxWidth().padding(bottom=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.05f),contentAlignment=Alignment.Center){
  Column(Modifier.width(maxWidth*playerPanelFraction(maxWidth.value)).readingSurface(RoundedCornerShape(InteractionStyle.panelRadius)).padding(horizontal=22.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){val amount=replaySeekAmount(feedback.delta);val speed=if(feedback.multiplier>1)" ×${feedback.multiplier}" else "";AppText((if(feedback.delta>0)"快进 $amount ▷▷"else"◁◁ 快退 $amount")+speed,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=NextTvStyle.Text,maxLines=1);AppText(replayElapsed(feedback.target)+" / "+replayElapsed(feedback.duration),color=NextTvStyle.Muted,maxLines=1)}
   Box(Modifier.fillMaxWidth().progressSemantics((feedback.target.toFloat()/feedback.duration.coerceAtLeast(1)).coerceIn(0f,1f)).height(4.dp).background(NextTvStyle.Stroke,RoundedCornerShape(2.dp))){Box(Modifier.fillMaxWidth((feedback.target.toFloat()/feedback.duration.coerceAtLeast(1)).coerceIn(0f,1f)).fillMaxHeight().background(NextTvStyle.Accent,RoundedCornerShape(2.dp)))}
  }
 }
}
