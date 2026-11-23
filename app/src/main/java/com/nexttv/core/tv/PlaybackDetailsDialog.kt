// SPDX-License-Identifier: GPL-3.0-only
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nexttv.core.tv

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaLibraryInfo
import com.nexttv.audioChannelLabel
import com.nexttv.audioCodecLabel
import com.nexttv.core.ui.Ui
import com.nexttv.resolutionLabel
import com.nexttv.videoCodecLabel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun PlaybackDetailsDialog(engine: PlaybackSession, onDismiss: () -> Unit) {
    var rows by remember(engine) { mutableStateOf(engine.detailRows()) }
    LaunchedEffect(engine) {
        while (true) {
            delay(1000)
            rows = engine.detailRows()
        }
    }
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val screen = LocalConfiguration.current
    val dialogWidth = 640.dp
    val panelWidth = (screen.screenWidthDp - 48).coerceIn(240, dialogWidth.value.toInt())
    val panelHeight = (screen.screenHeightDp - 48).coerceIn(160, 480)
    val scale = minOf(panelWidth / 600f, panelHeight / 440f).coerceIn(.82f, 1.09f)
    val step = with(LocalDensity.current) { (42.dp * scale).toPx() }
    fun navigate(keyCode: Int): Boolean {
        if (!TvInput.vertical(keyCode)) return false
        scroll.dispatchRawDelta(if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) step else -step)
        return true
    }
    TvDialog("播放详情", onDismiss, width = dialogWidth, onKeyboardNavigate = ::navigate, titleScale = scale) {
        val view = LocalView.current
        val inWindow = LocalInWindowModal.current
        LaunchedEffect(view, inWindow) {
            awaitModalFocusWindow(view, inWindow)
            focus.requestFocus()
        }
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth()
                .focusRequester(focus)
                .onPreviewKeyEvent { it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && navigate(it.nativeKeyEvent.keyCode) }
                .focusable()
                .appVerticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp * scale),
        ) {
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    AppText(label, Modifier.width(96.dp * scale), color = NextTvStyle.Muted, fontSize = InteractionStyle.caption * scale)
                    AppText(value, Modifier.weight(1f), color = NextTvStyle.Text,
                        fontSize = InteractionStyle.body * scale, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun PlaybackSession.detailRows(): List<Pair<String, String>> {
    val video = player.videoFormat
    val audio = player.audioFormat
    val route = current.playbackRoutes.getOrNull(routeIndex)
    val programme = replay ?: app.epg.nowNext(current).first
    val address = if (replay == null) route?.url else player.currentMediaItem?.localConfiguration?.uri?.toString()
    return listOf(
        "频道名" to Ui.displayName(current.name),
        "分组" to current.groupName,
        "节目" to programme?.title.orEmpty(),
        "最近失败原因" to latestFailureDetail.ifBlank { "暂无失败记录" },
        "地址" to address.orEmpty(),
        "视频" to (videoCodecLabel(video?.sampleMimeType) ?: video?.sampleMimeType.orEmpty()),
        "分辨率" to (video?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { "${resolutionLabel(it.height)} · ${it.width} × ${it.height}" }.orEmpty()),
        "视频解码器" to videoDecoderLabel,
        "音频" to (audioCodecLabel(audio?.sampleMimeType) ?: audio?.sampleMimeType.orEmpty()),
        "声道" to audioChannelLabel(audio?.channelCount ?: 0),
        "采样率" to (audio?.sampleRate?.takeIf { it > 0 }?.let { "$it Hz" }.orEmpty()),
        "音频解码器" to audioDecoderLabel,
        "缓冲" to String.format(Locale.ROOT, "%.1f 秒", player.totalBufferedDuration.coerceAtLeast(0) / 1000.0),
        "播放器" to "Media3 ExoPlayer ${MediaLibraryInfo.VERSION}",
        "解码策略" to when (selectedDecoderId) {
            "hardware" -> "硬件解码"
            "software" -> "软件解码"
            else -> "自动（优先硬解）"
        },
    ).map { (label, value) -> label to value.ifBlank { "—" } }
}
