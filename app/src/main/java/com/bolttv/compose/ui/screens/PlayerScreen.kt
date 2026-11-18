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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.PlaybackState
import com.bolttv.compose.core.player.DualPlayerManager
import kotlinx.coroutines.delay

/**
 * 全屏播放：上下键换台、数字键跳台、错误重试、信息条
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
    var numberBuffer by remember { mutableStateOf("") }
    var numberHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialChannel) {
        initialChannel?.let {
            dualPlayer.play(it)
            onChannelChanged(it)
        }
    }

    LaunchedEffect(showInfo, state.currentChannel) {
        if (showInfo) {
            delay(4000)
            showInfo = false
        }
    }

    // 数字键缓冲：2 秒内输入，超时按频道号跳转
    LaunchedEffect(numberBuffer) {
        if (numberBuffer.isEmpty()) return@LaunchedEffect
        numberHint = numberBuffer
        delay(2000)
        val num = numberBuffer.toIntOrNull()
        numberBuffer = ""
        numberHint = null
        if (num != null) {
            val target = channels.firstOrNull { it.number == num }
                ?: channels.getOrNull(num - 1) // 无 chno 时按列表序号（1-based）
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
        if (index >= 0) {
            dualPlayer.preload(channels.getOrNull(index + 1))
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
                        onBack()
                        true
                    }
                    Key.DirectionUp -> {
                        switchByOffset(-1)
                        true
                    }
                    Key.DirectionDown -> {
                        switchByOffset(1)
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight, Key.Enter, Key.DirectionCenter -> {
                        showInfo = true
                        true
                    }
                    Key.Menu -> {
                        dualPlayer.retryNow()
                        showInfo = true
                        true
                    }
                    // 数字键 0-9
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

        // 数字键提示
        numberHint?.let { hint ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
            }
        }

        if (showInfo || state.playbackState is PlaybackState.Error) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 32.dp)
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
                                text = ps.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { dualPlayer.retryNow() }) {
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
