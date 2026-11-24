package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * APTV/iPlayTV direction: one flat dark surface, one hairline border, no glass,
 * no shadow and no nested material. Used by content panels, dialogs and player chrome.
 */
internal fun Modifier.panelSurface(shape: Shape): Modifier =
    background(NextTvStyle.Panel, shape).border(.75.dp, NextTvStyle.PlayerInfoBorder, shape)

/** Dialogs and transient notices share the same flat surface as the rest of the app. */
internal fun Modifier.readingSurface(shape: Shape): Modifier = panelSurface(shape)

/** Floating player bar: flat panel over video, radius from the shared 16/12/6 scale. */
internal fun Modifier.playerBarSurface(shape: Shape): Modifier = panelSurface(shape)

/** Player browser rail: full-height flat surface with one vertical hairline. */
internal fun Modifier.playerSidebarSurface(extent: () -> Float): Modifier = drawBehind {
    val width = extent().coerceAtLeast(1f)
    drawRect(NextTvStyle.Panel, size = androidx.compose.ui.geometry.Size(width, size.height))
    drawLine(
        NextTvStyle.PlayerInfoBorder,
        start = androidx.compose.ui.geometry.Offset(width - .75.dp.toPx(), 0f),
        end = androidx.compose.ui.geometry.Offset(width - .75.dp.toPx(), size.height),
        strokeWidth = .75.dp.toPx()
    )
}
