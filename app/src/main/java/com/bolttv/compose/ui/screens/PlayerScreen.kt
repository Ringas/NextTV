package com.bolttv.compose.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay

/**
 * 全屏播放 + APTV/Bolt 式左侧频道浮层
 * 左键或菜单打开列表；数字键跳台；上下键换台
 */
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
            delay(4000)
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
            val target = channels.firstOrNull { it.number == num }
                ?: channels.getOrNull(num - 1)
            if (target != null) {
                dualPlayer.play(target)
                onChannelChanged(target)
                showInfo = true
            } else {
                numberHint = "无频道 $num"
                delay(1500)
                numberHint = null
            }
        }
    }

    LaunchedEffect(state.currentChannel, channels) {
        val current = state.currentChannel ?: return@LaunchedEffect
        val index = channels.indexOfFirst { it.id == current.id }
        if (index >= 0) dualPlayer.preload(channels.getOrNull(index + 1))
    }

    LaunchedEffect(showSidebar) {
        if (showSidebar) {
            delay(50)
            runCatching { listFocus.requestFocus() }
        }
    }

    fun switchByOffset(delta: Int) {
        val current = state.currentChannel ?: return
        val index = channels.indexOfFirst { it.id == current.id }
        if (index < 0) return
        val targetIndex = (index + delta).coerceIn(0, channels.lastIndex)
        if (targetIndex != index) {
            val ch = channels[targetIndex]
            dualPlayer.play(ch)
            onChannelChanged(ch)
            showInfo = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        if (showSidebar) {
                            showSidebar = false
                            true
                        } else {
                            onBack()
                            true
                        }
                    }
                    Key.DirectionUp -> {
                        if (!showSidebar) {
                            switchByOffset(-1)
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (!showSidebar) {
                            switchByOffset(1)
                            true
                        } else false
                    }
                    Key.DirectionLeft, Key.Menu -> {
                        showSidebar = true
                        showInfo = true
                        true
                    }
                    Key.DirectionRight -> {
                        if (showSidebar) {
                            showSidebar = false
                            true
                        } else {
                            showInfo = true
                            true
                        }
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        showInfo = true
                        true
                    }
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

        // 左侧频道浮层（半透明）
        if (showSidebar) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(Color(0xE612151A))
                    .padding(top = 32.dp, bottom = 24.dp, start = 16.dp, end = 12.dp)
            ) {
                Column {
                    Text(
                        text = "频道列表",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                    )
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(channels, key = { it.id }) { channel ->
                            val active = state.currentChannel?.id == channel.id
                            Surface(
                                onClick = {
                                    dualPlayer.play(channel)
                                    onChannelChanged(channel)
                                    showSidebar = false
                                    showInfo = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (active) Modifier.focusRequester(listFocus)
                                        else Modifier
                                    ),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (active) MaterialTheme.colorScheme.primary.copy(0.25f)
                                    else Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(0.35f)
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = buildString {
                                        channel.number?.let { append("$it  ") }
                                        append(channel.name)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        numberHint?.let { hint ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text(hint, style = MaterialTheme.typography.displaySmall, color = Color.White)
            }
        }

        if ((showInfo || state.playbackState is PlaybackState.Error) && !showSidebar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
                Column {
                    val ch = state.currentChannel
                    Text(
                        text = buildString {
                            ch?.number?.let { append("$it  ") }
                            append(ch?.name ?: "无信号")
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = ch?.group ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(0.8f)
                    )
                    Text(
                        text = "← 频道列表  ·  上下换台  ·  数字键跳台",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.5f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    when (val ps = state.playbackState) {
                        is PlaybackState.Buffering -> {
                            Text(
                                "缓冲中…",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        is PlaybackState.Error -> {
                            Text(
                                ps.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(
                                onClick = { dualPlayer.retryNow() },
                                modifier = Modifier.padding(top = 10.dp)
                            ) { Text("立即重试") }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
