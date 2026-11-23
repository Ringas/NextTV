// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

/** Official lifecycle-aware video surface; controls and focus remain owned by Next TV. */
@Composable internal fun PlaybackVideoSurface(player:Player,aspect:Int,keepLastFrame:Boolean,retained:RetainedVideoFrame){
 val root=LocalView.current
 DisposableEffect(root,retained){retained.surfaceRoot=root;onDispose{if(retained.surfaceRoot===root)retained.surfaceRoot=null}}
 ContentFrame(player=player,modifier=Modifier.fillMaxSize(),
  surfaceType=if(Build.VERSION.SDK_INT<24)SURFACE_TYPE_TEXTURE_VIEW else SURFACE_TYPE_SURFACE_VIEW,
  contentScale=when(aspect){1->ContentScale.Crop;2->ContentScale.FillBounds;else->ContentScale.Fit},
  keepContentOnReset=keepLastFrame)
}
