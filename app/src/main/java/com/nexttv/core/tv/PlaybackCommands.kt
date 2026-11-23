// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.media3.common.Player

/** Buffering is still an intention to play. A pause command must not accidentally resume it. */
internal fun Player.togglePlaybackIntent():Boolean {
 val resume=!playWhenReady
 if(resume)play()else pause()
 return resume
}
