package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * tvOS 27 Liquid Glass, reduced to the part that survives on a TV:
 * a tinted vertical wash, a darker edge and a narrow specular highlight.
 *
 * It is deliberately kept off content cards, list text and settings bodies.
 * Only navigation, playback chrome and dialogs receive the material.
 */
private fun Modifier.liquidGlass(
    shape: Shape,
    top: Color,
    bottom: Color,
    elevation: Dp,
): Modifier {
    val shadowColor = NextTvStyle.GlassShadow
    val base = if (elevation > 0.dp) {
        shadow(
            elevation,
            shape,
            clip = false,
            ambientColor = shadowColor.copy(alpha = shadowColor.alpha * .55f),
            spotColor = shadowColor
        )
    } else this
    return base.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val fill = Brush.verticalGradient(listOf(top, bottom))
        val rim = Brush.verticalGradient(
            listOf(NextTvStyle.GlassSpecular, Color.Transparent, NextTvStyle.GlassEdge)
        )
        onDrawBehind {
            drawOutline(outline, fill)
            drawOutline(outline, rim, style = Stroke(.9.dp.toPx()))
        }
    }
}

/** Settings and content-adjacent menus: flat graphite card, hairline only. */
internal fun Modifier.glassPanel(shape: Shape): Modifier =
    background(NextTvStyle.Panel, shape).border(.75.dp, NextTvStyle.PlayerInfoBorder, shape)

/** Dialogs, reading panels and transient notices stay glassy but with a lighter lift. */
internal fun Modifier.glassReadingPanel(shape: Shape): Modifier =
    liquidGlass(shape, NextTvStyle.GlassReadingTop, NextTvStyle.GlassReadingBottom, 14.dp)

/** Floating playback info bar above video: glass, but never a heavy gloss card. */
internal fun Modifier.playerInfoPanel(shape: Shape): Modifier =
    liquidGlass(shape, NextTvStyle.PlayerGlassTop, NextTvStyle.PlayerGlassBottom, 10.dp)

/** Player browser rail: flat dark surface, one hairline, no rounded glass card. */
internal fun Modifier.playerSidebarPanel(extent: () -> Float): Modifier = drawBehind {
    val width = extent().coerceAtLeast(1f)
    drawRect(NextTvStyle.Panel, size = androidx.compose.ui.geometry.Size(width, size.height))
    drawLine(
        NextTvStyle.PlayerInfoBorder,
        start = androidx.compose.ui.geometry.Offset(width - .75.dp.toPx(), 0f),
        end = androidx.compose.ui.geometry.Offset(width - .75.dp.toPx(), size.height),
        strokeWidth = .75.dp.toPx()
    )
}
