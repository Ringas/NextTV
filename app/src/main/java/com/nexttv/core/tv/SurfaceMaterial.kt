package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Nocturne: content brings the colour, chrome stays graphite. One flat fill and one
 * hairline per surface. Glass and gloss are not part of the system.
 */
internal fun Modifier.panelSurface(shape: Shape): Modifier =
    background(NextTvStyle.Panel, shape).border(.75.dp, NextTvStyle.PlayerInfoBorder, shape)

/** Transient notices and replay feedback: flat, hairline, no lift. */
internal fun Modifier.readingSurface(shape: Shape): Modifier = panelSurface(shape)

/** Modal sheets are the only layer allowed a soft shadow, so they read above the scrim. */
internal fun Modifier.dialogSurface(shape: Shape): Modifier =
    shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .45f))
        .background(NextTvStyle.Surface, shape)
        .border(.75.dp, NextTvStyle.PlayerInfoBorder, shape)

/** Player chrome: a full-width cinematic scrim instead of an opaque rounded panel. */
internal fun Modifier.playerScrim(): Modifier = drawBehind {
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            .28f to Color.Black.copy(alpha = .35f),
            1f to Color.Black.copy(alpha = .9f)
        )
    )
}

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
