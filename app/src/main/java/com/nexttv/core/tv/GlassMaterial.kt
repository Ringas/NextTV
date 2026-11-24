package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

/** Shared translucent glass finish. Cached gradients only: no backdrop capture, blur or refraction. */
internal fun Modifier.glassPanel(shape:Shape):Modifier = background(NextTvStyle.Panel,shape)
internal fun Modifier.glassReadingPanel(shape: Shape): Modifier = background(NextTvStyle.ReadingPanel, shape)
/** Marco Movie inspired floating playback card: flat dark surface, hairline border, no heavy glass gradient. */
internal fun Modifier.playerInfoPanel(shape: Shape): Modifier =
 background(NextTvStyle.PlayerInfoPanel, shape).border(androidx.compose.ui.unit.Dp(.75f), NextTvStyle.PlayerInfoBorder, shape)
internal fun Modifier.glassTint(color:Color,shape:Shape):Modifier = background(color,shape).glassHighlight(shape)
private fun Modifier.glassHighlight(shape:Shape):Modifier = drawWithCache {
 val outline=shape.createOutline(size,layoutDirection,this)
 val light=Brush.linearGradient(listOf(Color(0x20FFFFFF),Color(0x04FFFFFF),Color.Transparent),Offset.Zero,Offset(size.width*.65f,size.height))
 onDrawBehind{drawOutline(outline,light)}
}

/** Player browser rail: the same flat dark card, hairline border and corner radius as the info bar. */
internal fun Modifier.playerSidebarPanel(extent:()->Float):Modifier = drawWithCache {
 val radius=24.dp.toPx()
 onDrawBehind{
  val area=androidx.compose.ui.geometry.Size(extent().coerceAtLeast(radius*2),size.height)
  val corners=androidx.compose.ui.geometry.CornerRadius(radius,radius)
  drawRoundRect(NextTvStyle.PlayerInfoPanel,size=area,cornerRadius=corners)
  drawRoundRect(NextTvStyle.PlayerInfoBorder,size=area,cornerRadius=corners,style=androidx.compose.ui.graphics.drawscope.Stroke(.75.dp.toPx()))
 }
}
