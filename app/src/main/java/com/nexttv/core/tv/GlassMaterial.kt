package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*

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

/** Animate the panel's actual contour, never scale its corner radii. No layout per animation frame. */
internal fun Modifier.glassPanelExtent(extent:()->Float):Modifier = drawWithCache {
 val radius=com.nexttv.core.tv.InteractionStyle.panelRadius.toPx()

 onDrawBehind{
  val area=androidx.compose.ui.geometry.Size(extent().coerceAtLeast(radius*2),size.height)
  val corners=androidx.compose.ui.geometry.CornerRadius(radius,radius)
  drawRoundRect(NextTvStyle.Panel,size=area,cornerRadius=corners)
 }
}
