@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nexttv
import com.nexttv.core.tv.HomeGridCacheWindow
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Test
class HomeGridCacheWindowTest {
 @Test fun startupDoesNotRequestOffscreenComposition(){
  val cache=HomeGridCacheWindow()
  with(cache){with(Density(1f)){assertEquals(0,calculateAheadWindow(452));assertEquals(0,calculateBehindWindow(452))}}
 }
 @Test fun firstScrollEnablesOnlyBoundedViewportCache(){
  val cache=HomeGridCacheWindow();cache.activate();cache.activate()
  with(cache){with(Density(2f)){assertEquals(113,calculateAheadWindow(452));assertEquals(904,calculateBehindWindow(452));assertEquals(0,calculateAheadWindow(-1));assertEquals(Int.MAX_VALUE,calculateBehindWindow(Int.MAX_VALUE))}}
 }
}
