@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nexttv.core.tv

import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.ui.unit.Density

/** No off-screen composition on startup. A first actual scroll enables bounded reuse. */
internal class HomeGridCacheWindow : LazyLayoutCacheWindow {
 private var active=false
 fun activate(){active=true}
 override fun Density.calculateAheadWindow(viewport:Int):Int = if(active)viewport.coerceAtLeast(0)/4 else 0
 override fun Density.calculateBehindWindow(viewport:Int):Int = if(active)(viewport.coerceAtLeast(0).toLong()*2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
}
