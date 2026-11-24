// SPDX-License-Identifier: GPL-3.0-only
// Next TV — compact vector glyphs for the player quick-action dock.
package com.nexttv.core.tv

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

internal enum class ControlGlyph {
    Play, Pause, Live, Route, Audio, Aspect, Favorite, FavoriteOn, Codec, Info, Home
}

/**
 * A single 24-unit outline family so every quick action reads as one icon set.
 * Drawn locally to avoid pulling the entire material-icons-extended artifact into the TV APK.
 */
@Composable
internal fun ControlIcon(glyph: ControlGlyph, modifier: Modifier = Modifier) {
    val tint = LocalContentColor.current
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val stroke = 1.65f * s
        val path = Path()
        when (glyph) {
            ControlGlyph.Play -> {
                path.moveTo(8f * s, 5f * s)
                path.lineTo(19f * s, 12f * s)
                path.lineTo(8f * s, 19f * s)
                path.close()
                drawPath(path, tint)
            }
            ControlGlyph.Pause -> {
                drawRoundRect(tint, Offset(8f * s, 5.5f * s), Size(2.8f * s, 13f * s), CornerRadius(1.1f * s))
                drawRoundRect(tint, Offset(13.2f * s, 5.5f * s), Size(2.8f * s, 13f * s), CornerRadius(1.1f * s))
            }
            ControlGlyph.Live -> {
                drawCircle(tint, 2.1f * s, Offset(12f * s, 14f * s))
                drawArc(tint, -135f, 90f, false, Offset(7.5f * s, 9.5f * s), Size(9f * s, 9f * s), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(tint, -135f, 90f, false, Offset(4.5f * s, 6.5f * s), Size(15f * s, 15f * s), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ControlGlyph.Route -> {
                drawRoundRect(tint, Offset(4f * s, 6f * s), Size(11f * s, 3.2f * s), CornerRadius(1.6f * s))
                drawRoundRect(tint, Offset(7f * s, 10.5f * s), Size(13f * s, 3.2f * s), CornerRadius(1.6f * s), style = Stroke(stroke, cap = StrokeCap.Round))
                drawRoundRect(tint, Offset(10f * s, 15f * s), Size(10f * s, 3.2f * s), CornerRadius(1.6f * s), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ControlGlyph.Audio -> {
                path.moveTo(4f * s, 9.5f * s)
                path.lineTo(8f * s, 9.5f * s)
                path.lineTo(12.5f * s, 6f * s)
                path.lineTo(12.5f * s, 18f * s)
                path.lineTo(8f * s, 14.5f * s)
                path.lineTo(4f * s, 14.5f * s)
                path.close()
                drawPath(path, tint)
                drawArc(tint, -52f, 104f, false, Offset(10f * s, 7f * s), Size(10f * s, 10f * s), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(tint, -52f, 104f, false, Offset(8.5f * s, 4.5f * s), Size(13f * s, 15f * s), style = Stroke(stroke * .8f, cap = StrokeCap.Round))
            }
            ControlGlyph.Aspect -> {
                drawRoundRect(tint, Offset(3.5f * s, 5.5f * s), Size(17f * s, 13f * s), CornerRadius(2f * s), style = Stroke(stroke, cap = StrokeCap.Round))
                drawRoundRect(tint, Offset(8f * s, 9f * s), Size(8f * s, 6f * s), CornerRadius(1f * s), style = Stroke(stroke * .85f))
                line(5.5f, 8f, 8.5f, 8f, s, stroke * .8f, tint)
                line(5.5f, 8f, 5.5f, 11f, s, stroke * .8f, tint)
                line(18.5f, 16f, 15.5f, 16f, s, stroke * .8f, tint)
                line(18.5f, 16f, 18.5f, 13f, s, stroke * .8f, tint)
            }
            ControlGlyph.Favorite, ControlGlyph.FavoriteOn -> {
                starPath(s, path)
                if (glyph == ControlGlyph.FavoriteOn) drawPath(path, tint)
                else drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ControlGlyph.Codec -> {
                drawRoundRect(tint, Offset(6.5f * s, 6.5f * s), Size(11f * s, 11f * s), CornerRadius(1.6f * s), style = Stroke(stroke, cap = StrokeCap.Round))
                drawRoundRect(tint, Offset(9.5f * s, 9.5f * s), Size(5f * s, 5f * s), CornerRadius(.8f * s))
                for (p in listOf(8.5f, 12f, 15.5f)) {
                    line(p, 3.8f, p, 6f, s, stroke * .8f, tint)
                    line(p, 18f, p, 20.2f, s, stroke * .8f, tint)
                    line(3.8f, p, 6f, p, s, stroke * .8f, tint)
                    line(18f, p, 20.2f, p, s, stroke * .8f, tint)
                }
            }
            ControlGlyph.Info -> {
                drawCircle(tint, 8.2f * s, Offset(12f * s, 12f * s), style = Stroke(stroke, cap = StrokeCap.Round))
                drawCircle(tint, 1.35f * s, Offset(12f * s, 7.9f * s))
                line(12f, 10.8f, 12f, 16.5f, s, stroke, tint)
            }
            ControlGlyph.Home -> {
                path.moveTo(3.8f * s, 11.2f * s)
                path.lineTo(12f * s, 4f * s)
                path.lineTo(20.2f * s, 11.2f * s)
                drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round))
                path.reset()
                path.moveTo(6f * s, 9.6f * s)
                path.lineTo(6f * s, 19.5f * s)
                path.lineTo(18f * s, 19.5f * s)
                path.lineTo(18f * s, 9.6f * s)
                drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round))
                drawRoundRect(tint, Offset(9.8f * s, 13.8f * s), Size(4.4f * s, 5.7f * s), CornerRadius(.6f * s), style = Stroke(stroke * .8f))
            }
        }
    }
}

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, s: Float, stroke: Float, tint: androidx.compose.ui.graphics.Color) {
    drawLine(tint, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun starPath(s: Float, path: Path) {
    val outer = 9f
    val inner = 3.9f
    repeat(10) { i ->
        val angle = Math.toRadians((-90 + i * 36).toDouble())
        val radius = if (i % 2 == 0) outer else inner
        val x = (12 + cos(angle) * radius).toFloat() * s
        val y = (12 + sin(angle) * radius).toFloat() * s
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}
