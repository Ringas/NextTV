@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nexttv.core.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import kotlin.math.abs
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import kotlinx.coroutines.*

/** ScrollState uses whole device pixels; do not chase a fractional-pixel residual forever. */
internal fun settledScrollDistance(distance: Float): Float = if (abs(distance) <= 1f) 0f else distance

/** Keep a full row above the focus when the viewport has room for two rows. */
internal fun secondFocusRow(rowSize: Float, rowGap: Float, viewport: Float, margin: Float): Float? {
    val second = margin + rowSize + rowGap
    return second.takeIf { it + rowSize + margin <= viewport }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StableFocusScroll(content: @Composable () -> Unit) {
    val reduced=com.nexttv.core.App.get().prefs.reduceMotion
    val activity=androidx.activity.compose.LocalActivity.current as? TvActivity
    LaunchedEffect(reduced,com.nexttv.core.App.get().cacheReady){activity?.actualMotionScale=currentCoroutineContext()[androidx.compose.ui.MotionDurationScale]?.scaleFactor?:-1f}
    val margin=with(androidx.compose.ui.platform.LocalDensity.current){11.dp.toPx()}
    val rowGap = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() }
    val stable = remember(margin,rowGap,reduced) { object : BringIntoViewSpec {
        @Suppress("DEPRECATION")
        override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = if(reduced)androidx.compose.animation.core.snap()else androidx.compose.animation.core.spring(dampingRatio=1f,stiffness=650f)
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
            val anchor = if (InteractionStyle.verticalNavigation) secondFocusRow(size, rowGap, containerSize, margin) else null
            return settledScrollDistance(when {
                anchor != null -> offset - anchor
                offset < margin -> offset - margin
                offset + size > containerSize - margin -> offset + size - containerSize + margin
                else -> 0f
            })
        }
    } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides stable, content = content)
}


internal object HomeGridFocusVisibility : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        settledScrollDistance(when {
            offset < 0 -> offset
            offset + size > containerSize -> offset + size - containerSize
            else -> 0f
        })
}
