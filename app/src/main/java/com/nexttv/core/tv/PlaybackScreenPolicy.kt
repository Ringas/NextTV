// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nexttv.core.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.Player

/** Window-owned screen policy: no wake-lock permission and no keep-awake leakage into Home. */
@Composable internal fun KeepScreenOnWhilePlayback(player:Player,foreground:Boolean){
 val view=LocalView.current
 DisposableEffect(view,player,foreground){
  val previous=view.keepScreenOn
  fun update(){view.keepScreenOn=foreground&&player.playWhenReady&&player.playbackState!=Player.STATE_ENDED}
  val listener=object:Player.Listener{
   override fun onPlayWhenReadyChanged(value:Boolean,reason:Int)=update()
   override fun onPlaybackStateChanged(state:Int)=update()
  }
  player.addListener(listener);update()
  onDispose{player.removeListener(listener);view.keepScreenOn=previous}
 }
}
