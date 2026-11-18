package com.bolttv.compose.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bolttv.compose.ui.theme.NextTvColors
import com.bolttv.compose.ui.theme.NextTvDimens

private val FocusAnim = tween<Float>(durationMillis = 160, easing = FastOutSlowInEasing)
private val ColorAnim = tween<Color>(durationMillis = 160, easing = FastOutSlowInEasing)

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(NextTvDimens.radiusLg))
            .background(NextTvColors.Panel)
            .border(1.dp, NextTvColors.PanelBorder, RoundedCornerShape(NextTvDimens.radiusLg))
            .padding(16.dp)
    ) { content() }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AptvNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused = false
    // Surface handles focus; animate selected bg
    val bg by animateColorAsState(
        targetValue = when {
            selected -> NextTvColors.Accent
            else -> Color.Transparent
        },
        animationSpec = ColorAnim,
        label = "navBg"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = if (selected) NextTvColors.Accent else NextTvColors.AccentSoft
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NextTvDimens.radiusSm)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Color(0xFF00344D) else NextTvColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AptvChannelRow(
    title: String,
    subtitle: String?,
    focused: Boolean,
    playing: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = FocusAnim,
        label = "rowScale"
    )
    val bg by animateColorAsState(
        targetValue = when {
            focused -> NextTvColors.AccentSoft
            playing -> NextTvColors.BgElevated
            else -> Color.Transparent
        },
        animationSpec = ColorAnim,
        label = "rowBg"
    )
    val titleColor by animateColorAsState(
        targetValue = if (focused || playing) NextTvColors.Accent else NextTvColors.TextPrimary,
        animationSpec = ColorAnim,
        label = "titleColor"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { if (it.isFocused) onFocused() },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = NextTvColors.AccentSoft
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NextTvDimens.radiusMd)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f), // 自管 scale 更顺
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, NextTvColors.Accent),
                shape = RoundedCornerShape(NextTvDimens.radiusMd)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NextTvColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (playing) {
                Text("●", color = NextTvColors.Playing, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
