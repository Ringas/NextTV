package com.bolttv.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

// APTV / Bolt 风格的深色现代主题
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF003544),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003732),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    border = Color(0xFF30363D),
    error = Color(0xFFF85149)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0277BD),
    onPrimary = Color.White,
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    onSurface = Color(0xFF24292F)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BoltComposeTheme(
    darkTheme: Boolean = true, // TV 默认深色
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// 常用尺寸（电视 10-foot UI）
object Dimens {
    val cardCorner = 12
    val screenPadding = 48
    val focusScale = 1.08f
}
