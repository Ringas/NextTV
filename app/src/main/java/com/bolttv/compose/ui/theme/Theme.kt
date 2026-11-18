package com.bolttv.compose.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** APTV 向设计色板 */
object NextTvColors {
    val Bg = Color(0xFF0B0D10)
    val BgElevated = Color(0xFF12151C)
    val Panel = Color(0xE612151C)
    val PanelSolid = Color(0xFF161A22)
    val PanelBorder = Color(0x33FFFFFF)
    val Accent = Color(0xFF5AC8FA)
    val AccentDim = Color(0xFF2A9FD6)
    val AccentSoft = Color(0x335AC8FA)
    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFF9AA3B2)
    val TextMuted = Color(0xFF6B7380)
    val Danger = Color(0xFFFF6B6B)
    val NowLine = Color(0xFFFF5252)
    val Playing = Color(0xFF5AC8FA)
    val FocusGlow = Color(0x665AC8FA)

    val ScrimBottom = Brush.verticalGradient(
        listOf(Color.Transparent, Color(0xCC000000))
    )
    val ScrimTop = Brush.verticalGradient(
        listOf(Color(0x99000000), Color.Transparent)
    )
}

object NextTvDimens {
    val radiusSm = 8.dp
    val radiusMd = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 20.dp
    val padScreen = 28.dp
    val categoryWidth = 196.dp
    val listWidth = 360.dp
    val focusScale = 1.03f
}

private val AptvDarkScheme = darkColorScheme(
    primary = NextTvColors.Accent,
    onPrimary = Color(0xFF00344D),
    secondary = NextTvColors.AccentDim,
    onSecondary = Color.White,
    background = NextTvColors.Bg,
    onBackground = NextTvColors.TextPrimary,
    surface = NextTvColors.PanelSolid,
    onSurface = NextTvColors.TextPrimary,
    surfaceVariant = NextTvColors.BgElevated,
    onSurfaceVariant = NextTvColors.TextSecondary,
    border = NextTvColors.PanelBorder,
    error = NextTvColors.Danger
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BoltComposeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AptvDarkScheme,
        content = content
    )
}
