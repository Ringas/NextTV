package com.bolttv.compose.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.PlaybackState
import com.bolttv.compose.core.player.DualPlayerManager
import kotlinx.coroutines.delay

/**
 * 全屏播放页
 * - 使用 DualPlayerManager 实现快速换台
 * - 遥控器上下键切换频道
 * - 信息条自动隐藏
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    dualPlayer: DualPlayerManager,
    channels: List<Channel>,
    initialChannel: Channel?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by dualPlayer.state.collectAsState()
    var showInfo by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // 初始播放
    LaunchedEffect(initialChannel) {
        initialChannel?.let { dualPlayer.play(it) }
    }

    // 信息条自动隐藏
    LaunchedEffect(showInfo, state.currentChannel) {
        if (showInfo) {
            delay(4000)
            showInfo = false
        }
    }

    // 预加载相邻频道
    LaunchedEffect(state.currentChannel, channels) {
        val current = state.currentChannel ?: return@LaunchedEffect
        val index = channels.indexOfFirst { it.id == current.id }
        if (index >= 0) {
            val next = channels.getOrNull(index + 1)
            dualPlayer.preload(next)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onBack()
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        val current = state.currentChannel ?: return@onPreviewKeyEvent false
                        val index = channels.indexOfFirst { it.id == current.id }
                        if (index < 0) return@onPreviewKeyEvent false
                        val targetIndex = when (event.key) {
                            Key.DirectionUp -> (index - 1).coerceAtLeast(0)
                            else -> (index + 1).coerceAtMost(channels.lastIndex)
                        }
                        if (targetIndex != index) {
                            dualPlayer.play(channels[targetIndex])
                            showInfo = true
                        }
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight, Key.Enter, Key.DirectionCenter -> {
                        showInfo = true
                        true
                    }
                    else -> false
                }
            }
    ) {
        // 视频 Surface
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
            update = { playerView ->
                playerView.player = dualPlayer.activePlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // 顶部信息条
        if (showInfo) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                Column {
                    val ch = state.currentChannel
                    Text(
                        text = ch?.name ?: "无信号",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ch?.group ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    when (val ps = state.playbackState) {
                        is PlaybackState.Buffering -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "缓冲中…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is PlaybackState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "错误: ${ps.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 不在这里 release，由上层管理生命周期
        }
    }
}
