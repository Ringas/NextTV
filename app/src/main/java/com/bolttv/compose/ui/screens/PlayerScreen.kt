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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bolttv.compose.core.data.ChannelRepository
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.PlaybackState
import com.bolttv.compose.core.player.DualPlayerManager
import com.bolttv.compose.ui.theme.NextTvColors
import com.bolttv.compose.ui.theme.NextTvDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ResizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "适应",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "填充",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "裁剪"
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    dualPlayer: DualPlayerManager,
    channels: List<Channel>,
    initialChannel: Channel?,
    repository: ChannelRepository? = null,
    onChannelChanged: (Channel) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by dualPlayer.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showInfo by remember { mutableStateOf(true) }
    var showSidebar by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var numberBuffer by remember { mutableStateOf("") }
    var numberHint by remember { mutableStateOf<String?>(null) }
    var resizeIndex by remember { mutableIntStateOf(2) } // default ZOOM
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val listFocus = remember { FocusRequester() }
    val controlFocus = remember { FocusRequester() }

    LaunchedEffect(initialChannel) {
        initialChannel?.let {
            dualPlayer.play(it)
            onChannelChanged(it)
        }
    }
    LaunchedEffect(showInfo, state.currentChannel, showSidebar, showControls) {
        if (showInfo && !showSidebar && !showControls) {
            delay(4500)
            showInfo = false
        }
    }
    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isEmpty()) return@LaunchedEffect
        numberHint = numberBuffer
        delay(1800)
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
                delay(1000)
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
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(40)
            runCatching { controlFocus.requestFocus() }
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

    fun applyResize(index: Int) {
        resizeIndex = index
        playerViewRef?.resizeMode = ResizeModes[index].first
    }

    Box(
        modifier = modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
            when (e.key) {
                Key.Back, Key.Escape -> {
                    when {
                        showControls -> { showControls = false; true }
                        showSidebar -> { showSidebar = false; true }
                        else -> { onBack(); true }
                    }
                }
                Key.DirectionUp -> when {
                    showControls || showSidebar -> false
                    else -> { zap(-1); true }
                }
                Key.DirectionDown -> when {
                    showControls || showSidebar -> false
                    else -> { zap(1); true }
                }
                Key.DirectionLeft, Key.Menu -> {
                    if (!showControls) {
                        showSidebar = true
                        showInfo = true
                        true
                    } else false
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
                    // 确认键：切换播控条
                    showControls = !showControls
                    showInfo = true
                    showSidebar = false
                    true
                }
                Key.Info, Key.Bookmark -> {
                    showControls = true
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
                    resizeMode = ResizeModes[resizeIndex].first
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = {
                it.player = dualPlayer.activePlayer
                it.resizeMode = ResizeModes[resizeIndex].first
                playerViewRef = it
            },
            modifier = Modifier.fillMaxSize()
        )

        // 侧栏
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
                                    containerColor = if (active) NextTvColors.AccentSoft
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    focusedContainerColor = NextTvColors.AccentSoft
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NextTvDimens.radiusSm))
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

        // 播控条
        if (showControls) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(NextTvColors.Panel)
                    .padding(horizontal = 36.dp, vertical = 20.dp)
            ) {
                Column {
                    val ch = state.currentChannel
                    Text(
                        ch?.name ?: "无信号",
                        style = MaterialTheme.typography.titleLarge,
                        color = NextTvColors.TextPrimary
                    )
                    Text(
                        ch?.url?.take(64) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = NextTvColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { dualPlayer.retryNow() },
                            modifier = Modifier.focusRequester(controlFocus)
                        ) { Text("重试") }
                        Button(
                            onClick = {
                                val next = (resizeIndex + 1) % ResizeModes.size
                                applyResize(next)
                            }
                        ) { Text("画面: ${ResizeModes[resizeIndex].second}") }
                        if (repository != null && ch != null) {
                            Button(
                                onClick = {
                                    scope.launch { repository.toggleFavorite(ch) }
                                }
                            ) {
                                Text(if (ch.isFavorite) "取消收藏" else "收藏")
                            }
                        }
                        Button(onClick = { showControls = false }) { Text("关闭") }
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

        if ((showInfo || state.playbackState is PlaybackState.Error) && !showSidebar && !showControls) {
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
                        "← 列表  ·  ↑↓ 换台  ·  确认 播控  ·  数字跳台",
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
