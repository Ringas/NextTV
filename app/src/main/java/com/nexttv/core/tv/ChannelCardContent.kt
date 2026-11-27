// Next TV — channel card visual layout.
// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.*
import com.nexttv.core.data.Channel
import com.nexttv.core.ui.Ui

private val artworkColors = listOf(
 Color(0xFF4B6C8C) to Color(0xFF89A7C2),
 Color(0xFF587E68) to Color(0xFF93AE9B),
 Color(0xFF87654A) to Color(0xFFB79E86),
 Color(0xFF66669B) to Color(0xFF9A9AC0),
 Color(0xFF8A5F66) to Color(0xFFB79B9B),
 Color(0xFF527B77) to Color(0xFF87AAA6),
 Color(0xFF7A6F55) to Color(0xFFA9A08B),
 Color(0xFF5D6E8C) to Color(0xFF8C9EB8),
)
private val artworkBrushes = artworkColors.map { (base, highlight) -> Brush.linearGradient(listOf(base, highlight)) }

private fun artworkIndex(c: Channel): Int = (if (c.number > 0) c.number - 1 else c.id.hashCode() and Int.MAX_VALUE)

/** One stable artwork tint per channel, used by the channel cards. */
internal fun channelArtworkBrush(c: Channel): Brush = artworkBrushes[artworkIndex(c) % artworkBrushes.size]

@Composable
private fun CardText(text: String, size: Int, color: Color, modifier: Modifier = Modifier, bold: Boolean = false, letterSpacing: TextUnit = 0.sp) {
 androidx.compose.foundation.text.BasicText(
  text, modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
  style = androidx.compose.ui.text.TextStyle(
   color = color, fontSize = size.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
   fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
   platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true),
   lineHeight = size.sp * if (bold) 1.2f else 1.35f,
   letterSpacing = letterSpacing
  )
 )
}

@Composable
private fun ChannelNumberBadge(channel: Channel) {
 val number = channel.number.toString().padStart(3, '0')
 val density = LocalDensity.current
 val systemScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale
 androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, systemScale)) {
  Box(
   Modifier.widthIn(min = 25.dp * systemScale.coerceAtLeast(1f)).height(19.dp * systemScale.coerceAtLeast(1f))
    .semantics { contentDescription = "频道号 $number" }
    .background(NextTvStyle.ChannelNumber, RoundedCornerShape(InteractionStyle.badgeRadius))
    .border(.75.dp, NextTvStyle.PlayerInfoBorder, RoundedCornerShape(InteractionStyle.badgeRadius))
    .padding(horizontal = 5.dp),
   contentAlignment = Alignment.Center,
  ) {
   CardText(number, 11, NextTvStyle.Text, bold = true)
  }
 }
}

/** Short, artwork-style channel mark: Latin call sign if present, else the first four CJK characters. */
internal fun channelCoverMark(name: String): String {
 val latin = name.takeWhile { it.code < 128 }.trim()
  .replace('-', ' ')
  .replace(Regex("\\s+"), " ")
 if (latin.length >= 2) return latin.uppercase()
 return Ui.displayName(name).take(4)
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
 } else Column(Modifier.fillMaxWidth()) {
  val focused = LocalSurfaceFocused.current
  val density = LocalDensity.current
  val systemScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale
  androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, systemScale)) {
   Column {
    val artworkShape = RoundedCornerShape(InteractionStyle.cardRadius)
    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp).aspectRatio(16f / 9f).clip(artworkShape)
     .background(channelArtworkBrush(c))
     .border(if(focused) InteractionStyle.focusRing else .75.dp, if(focused) NextTvStyle.Ivory.copy(alpha=.92f) else NextTvStyle.PlayerInfoBorder, artworkShape), contentAlignment = Alignment.Center) {
     ChannelLogo(c, Modifier.fillMaxSize(), showFallback = false)
     CardText(c.groupName, 10, Color.White.copy(alpha=.55f), Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 11.dp), letterSpacing = .08.sp)
     if (preview) ChannelPreview(c, previewRunning, previewSlot, Modifier.fillMaxSize())
     if (favorite && showFavorite) {
      CardText("★", 13, Color(0xFFFFD469), Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 6.dp))
     }
    }
    Row(
     Modifier.fillMaxWidth().padding(top = 8.dp, start = 6.dp, end = 6.dp),
     horizontalArrangement = Arrangement.spacedBy(6.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      CardText(Ui.displayName(c.name), 16, NextTvStyle.Text, bold = true)
      CardText(caption, 13, NextTvStyle.Muted)
     }
     ChannelNumberBadge(c)
    }
   }
  }
 }
}
