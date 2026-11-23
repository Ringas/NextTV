@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nexttv.core.tv

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Pointer scrolling and inertia are independent of the interface animation preference. */
@Composable internal fun appFlingBehavior(): FlingBehavior = ScrollableDefaults.flingBehavior()

@Composable internal fun Modifier.appVerticalScroll(state: ScrollState): Modifier =
 continuousWheel(state).verticalScroll(state, flingBehavior = appFlingBehavior())
