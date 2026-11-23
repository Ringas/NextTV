// Next TV — channel card visual layout.
// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.nexttv.core.data.Channel
import com.nexttv.core.ui.Ui

private val artworkBrushes = listOf(
 Color(0xFF4B6C8C) to Color(0xFF89A7C2),
 Color(0xFF587E68) to Color(0xFF93AE9B),
 Color(0xFF87654A) to Color(0xFFB79E86),
 Color(0xFF66669B) to Color(0xFF9A9AC0),
 Color(0xFF8A5F66) to Color(0xFFB79B9B),
 Color(0xFF527B77) to Color(0xFF87AAA6),
 Color(0xFF7A6F55) to Color(0xFFA9A08B),
 Color(0xFF5D6E8C) to Color(0xFF8C9EB8),
).map { (base, highlight) -> Brush.linearGradient(listOf(base, highlight)) }

private val numberColors = listOf(
 Color(0xFF1D5C80), Color(0xFF2A6E5C), Color(0xFF694F86), Color(0xFF7A4A38),
 Color(0xFF2C5D9B), Color(0xFF8A5A2B), Color(0xFF39636F), Color(0xFF6B3B54),
)

@Composable
private fun CardText(text: String, size: Int, color: Color, modifier: Modifier = Modifier, bold: Boolean = false) {
 androidx.compose.foundation.text.BasicText(
  text, modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
  style = androidx.compose.ui.text.TextStyle(
   color = color, fontSize = size.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
   fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
   platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true)
  )
 )
}

@Composable
private fun ChannelNumberBadge(channel: Channel) {
 val number = channel.number.toString().padStart(3, '0')
 val paletteIndex = if (channel.number > 0) channel.number - 1 else channel.id.hashCode() and Int.MAX_VALUE
 val density = LocalDensity.current
 val systemScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale
 androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, systemScale)) {
  Box(
   Modifier.size(24.dp * systemScale.coerceAtLeast(1f))
    .semantics { contentDescription = "频道号 $number" }
    .background(numberColors[paletteIndex % numberColors.size], RoundedCornerShape(8.dp)),
   contentAlignment = Alignment.Center,
  ) {
   CardText(number, 11, Color(0xFFF2F7FC), bold = true)
  }
 }
}

/** Keep the same artwork tint each time a channel enters the viewport. */
@Composable internal fun ChannelCardContent(c:Channel,caption:String,favorite:Boolean,showFavorite:Boolean,list:Boolean,preview:Boolean=false,previewRunning:Boolean=false,previewSlot:Int=0){
 if(list)Row(Modifier.fillMaxWidth().heightIn(min=76.dp).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
  Box(Modifier.width(52.dp).height(40.dp)){PlayerLogo(c,Modifier.fillMaxSize());if(preview)ChannelPreview(c,previewRunning,previewSlot,Modifier.fillMaxSize())}
  Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
   AppText(Ui.displayName(c.name),fontSize=InteractionStyle.body,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
   AppText(caption,fontSize=InteractionStyle.caption,color=NextTvStyle.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
  }
  if(favorite&&showFavorite)AppText("★",Modifier.padding(end=8.dp),fontSize=InteractionStyle.caption,color=NextTvStyle.AccentText)
  ChannelNumberBadge(c)
 } else Column(Modifier.padding(6.dp)) {
  val paletteIndex = if (c.number > 0) c.number - 1 else c.id.hashCode() and Int.MAX_VALUE
  val density = LocalDensity.current
  val systemScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale
  androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, systemScale)) {
   Column {
    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
     .background(artworkBrushes[paletteIndex % artworkBrushes.size]), contentAlignment = Alignment.Center) {
     ChannelArtwork(c, Modifier.fillMaxSize())
     if (preview) ChannelPreview(c, previewRunning, previewSlot, Modifier.fillMaxSize())
     Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .66f))
      .padding(horizontal = 9.dp, vertical = 4.dp)) {
      CardText(caption, 11, Color(0xFFF2F5FA))
     }
     if (favorite && showFavorite) {
      CardText("★", 12, Color(0xFFFFD469), Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 6.dp))
     }
    }
    Row(
     Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp),
     horizontalArrangement = Arrangement.spacedBy(6.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     CardText(Ui.displayName(c.name), 14, NextTvStyle.Text, Modifier.weight(1f), bold = true)
     ChannelNumberBadge(c)
    }
   }
  }
 }
}
