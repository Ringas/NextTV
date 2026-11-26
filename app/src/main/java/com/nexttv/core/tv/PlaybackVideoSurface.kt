// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

/**
 * Crop keeps a little overscan even when the source and screen share the same aspect.
 * A plain [ContentScale.Crop] is mathematically identical to Fit on a 16:9 channel,
 * which made the "裁切填充" action look like it did nothing.
 */
private object CropOverscan : ContentScale {
 override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
  val base = ContentScale.Crop.computeScaleFactor(srcSize, dstSize)
  val zoom = 1.04f
  return ScaleFactor(base.scaleX * zoom, base.scaleY * zoom)
 }
}

/**
 * FillBounds is also an identity transform for a 16:9 source on a 16:9 screen.
 * A small vertical overscan makes "拉伸全屏" visibly distinct without changing
 * the Fit path; the parent clips the extra pixels.
 */
private object StretchOverscan : ContentScale {
 override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
  val base = ContentScale.FillBounds.computeScaleFactor(srcSize, dstSize)
  return ScaleFactor(base.scaleX, base.scaleY * 1.03f)
 }
}

internal fun playbackContentScale(aspect: Int): ContentScale = when (aspect) {
 1 -> CropOverscan
 2 -> StretchOverscan
 else -> ContentScale.Fit
}

/** Official lifecycle-aware video surface; controls and focus remain owned by Next TV. */
@Composable internal fun PlaybackVideoSurface(player:Player,aspect:Int,keepLastFrame:Boolean,retained:RetainedVideoFrame){
 val root=LocalView.current
 DisposableEffect(root,retained){retained.surfaceRoot=root;onDispose{if(retained.surfaceRoot===root)retained.surfaceRoot=null}}
 Box(Modifier.fillMaxSize().clipToBounds(),contentAlignment=Alignment.Center){
  ContentFrame(player=player,modifier=Modifier.fillMaxSize(),
   surfaceType=if(Build.VERSION.SDK_INT<24)SURFACE_TYPE_TEXTURE_VIEW else SURFACE_TYPE_SURFACE_VIEW,
   contentScale=playbackContentScale(aspect),
   keepContentOnReset=keepLastFrame)
 }
}
