package com.bolttv.compose.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.PlaybackState
import com.bolttv.compose.core.player.DualPlayerManager
import com.bolttv.compose.ui.theme.NextTvColors
import com.bolttv.compose.ui.theme.NextTvDimens
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    dualPlayer: DualPlayerManager,
    channels: List<Channel>,
    initialChannel: Channel?,
    onChannelChanged: (Channel) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by dualPlayer.state.collectAsState()
    var showInfo by remember { mutableStateOf(true) }
    var showSidebar by remember { mutableStateOf(false) }
    var numberBuffer by remember { mutableStateOf("") }
    var numberHint by remember { mutableStateOf<String?>(null) }
    val listFocus = remember { FocusRequester() }

    LaunchedEffect(initialChannel) {
        initialChannel?.let {
            dualPlayer.play(it)
            onChannelChanged(it)
        }
    }
    LaunchedEffect(showInfo, state.currentChannel, showSidebar) {
        if (showInfo && !showSidebar) {
            delay(4500)
            showInfo = false
        }
    }
    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isEmpty()) return@LaunchedEffect
        numberHint = numberBuffer
        delay(2000)
        val num = numberBuffer.toIntOrNull()
        numberBuffer = ""
        numberHint = null
        if (num != null) {
            val target = channels.firstOrNull { it.number == num } ?: channels.getOrNull(num - 1)
            if (target != null) {
                dualPlayer.play(target)
                onChannelChanged(target)
                showInfo = true
            } else {
                numberHint = "无频道 $num"
                delay(1200)
                numberHint = null
            }
        }
    }
    LaunchedEffect(state.currentChannel, channels) {
        val cur = state.currentChannel ?: return@LaunchedEffect
        val i = channels.indexOfFirst { it.id == cur.id }
        if (i >= 0) dualPlayer.preload(channels.getOrNull(i + 1))
    }
    LaunchedEffect(showSidebar) {
        if (showSidebar) {
            delay(40)
            runCatching { listFocus.requestFocus() }
        }
    }

    fun zap(delta: Int) {
        val cur = state.currentChannel ?: return
        val i = channels.indexOfFirst { it.id == cur.id }
        if (i < 0) return
        val t = (i + delta).coerceIn(0, channels.lastIndex)
        if (t != i) {
            dualPlayer.play(channels[t])
            onChannelChanged(channels[t])
            showInfo = true
        }
    }

    Box(
        modifier = modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
            when (e.key) {
                Key.Back, Key.Escape -> {
                    if (showSidebar) { showSidebar = false; true } else { onBack(); true }
                }
                Key.DirectionUp -> if (!showSidebar) { zap(-1); true } else false
                Key.DirectionDown -> if (!showSidebar) { zap(1); true } else false
                Key.DirectionLeft, Key.Menu -> { showSidebar = true; showInfo = true; true }
                Key.DirectionRight -> if (showSidebar) { showSidebar = false; true } else { showInfo = true; true }
                Key.Enter, Key.DirectionCenter -> { showInfo = true; true }
                Key.Zero, Key.NumPad0 -> { numberBuffer += "0"; true }
                Key.One, Key.NumPad1 -> { numberBuffer += "1"; true }
                Key.Two, Key.NumPad2 -> { numberBuffer += "2"; true }
                Key.Three, Key.NumPad3 -> { numberBuffer += "3"; true }
                Key.Four, Key.NumPad4 -> { numberBuffer += "4"; true }
                Key.Five, Key.NumPad5 -> { numberBuffer += "5"; true }
                Key.Six, Key.NumPad6 -> { numberBuffer += "6"; true }
                Key.Seven, Key.NumPad7 -> { numberBuffer += "7"; true }
                Key.Eight, Key.NumPad8 -> { numberBuffer += "8"; true }
                Key.Nine, Key.NumPad9 -> { numberBuffer += "9"; true }
                else -> false
            }
        }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = dualPlayer.activePlayer },
            modifier = Modifier.fillMaxSize()
        )

        if (showSidebar) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(380.dp)
                    .background(NextTvColors.Panel)
                    .padding(top = 28.dp, bottom = 20.dp, start = 14.dp, end = 10.dp)
            ) {
                Column {
                    Text(
                        "频道",
                        style = MaterialTheme.typography.titleMedium,
                        color = NextTvColors.TextPrimary,
                        modifier = Modifier.padding(start = 10.dp, bottom = 12.dp)
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(channels, key = { it.id }) { ch ->
                            val active = state.currentChannel?.id == ch.id
                            Surface(
                                onClick = {
                                    dualPlayer.play(ch)
                                    onChannelChanged(ch)
                                    showSidebar = false
                                    showInfo = true
                                },
                                modifier = Modifier.fillMaxWidth().then(
                                    if (active) Modifier.focusRequester(listFocus) else Modifier
                                ),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (active) NextTvColors.AccentSoft else androidx.compose.ui.graphics.Color.Transparent,
                                    focusedContainerColor = NextTvColors.AccentSoft
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NextTvDimens.radiusSm)),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = androidx.tv.material3.Border(
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, NextTvColors.Accent),
                                        shape = RoundedCornerShape(NextTvDimens.radiusSm)
                                    )
                                )
                            ) {
                                Text(
                                    text = buildString {
                                        ch.number?.let { append("$it  ") }
                                        append(ch.name)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (active) NextTvColors.Accent else NextTvColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        numberHint?.let { hint ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(NextTvColors.Panel, RoundedCornerShape(NextTvDimens.radiusMd))
                    .padding(horizontal = 36.dp, vertical = 18.dp)
            ) {
                Text(hint, style = MaterialTheme.typography.displaySmall, color = NextTvColors.TextPrimary)
            }
        }

        if ((showInfo || state.playbackState is PlaybackState.Error) && !showSidebar) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(NextTvColors.ScrimBottom)
                    .padding(horizontal = 44.dp, vertical = 26.dp)
            ) {
                Column {
                    val ch = state.currentChannel
                    Text(
                        buildString {
                            ch?.number?.let { append("$it  ") }
                            append(ch?.name ?: "无信号")
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = NextTvColors.TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(ch?.group.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = NextTvColors.TextSecondary)
                    Text(
                        "← 频道列表    ↑↓ 换台    数字键跳台",
                        style = MaterialTheme.typography.bodySmall,
                        color = NextTvColors.TextMuted,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    when (val ps = state.playbackState) {
                        is PlaybackState.Buffering ->
                            Text("缓冲中…", color = NextTvColors.Accent, modifier = Modifier.padding(top = 8.dp))
                        is PlaybackState.Error -> {
                            Text(ps.message, color = NextTvColors.Danger, modifier = Modifier.padding(top = 8.dp))
                            Button(onClick = { dualPlayer.retryNow() }, modifier = Modifier.padding(top = 10.dp)) {
                                Text("立即重试")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
