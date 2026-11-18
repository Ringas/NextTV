package com.bolttv.compose.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bolttv.compose.core.data.ChannelRepository
import com.bolttv.compose.core.data.EpgRepository
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.player.PreviewPlayerManager
import com.bolttv.compose.ui.components.AptvChannelRow
import com.bolttv.compose.ui.components.AptvNavItem
import com.bolttv.compose.ui.theme.NextTvColors
import com.bolttv.compose.ui.theme.NextTvDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * APTV 向首页：分类 | 列表 | 沉浸大预览
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: ChannelRepository,
    epgRepository: EpgRepository,
    currentChannel: Channel?,
    previewManager: PreviewPlayerManager,
    onChannelClick: (Channel) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenEpg: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val channels by repository.channels.collectAsState()
    val groups by repository.groups.collectAsState()
    val epgLoaded by epgRepository.loaded.collectAsState()
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    val focused by previewManager.focusedChannel.collectAsState()
    val previewReady by previewManager.isReady.collectAsState()

    val displayChannels = remember(channels, selectedGroup) {
        repository.getChannelsByGroup(selectedGroup)
    }

    val program = if (epgLoaded && focused != null)
        epgRepository.getCurrentProgram(focused!!.id, focused!!.tvgId) else null
    val upcoming = if (epgLoaded && focused != null)
        epgRepository.getUpcoming(focused!!.id, focused!!.tvgId, 2) else emptyList()

    val clock = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    DisposableEffect(Unit) { onDispose { previewManager.clearPreview() } }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(NextTvColors.Bg)
    ) {
        // 分类栏
        Column(
            modifier = Modifier
                .width(NextTvDimens.categoryWidth)
                .fillMaxHeight()
                .background(NextTvColors.BgElevated)
                .padding(start = 16.dp, top = 28.dp, end = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "NextTV",
                style = MaterialTheme.typography.headlineSmall,
                color = NextTvColors.Accent,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
            Text(
                text = clock,
                style = MaterialTheme.typography.labelMedium,
                color = NextTvColors.TextMuted,
                modifier = Modifier.padding(start = 6.dp, bottom = 14.dp)
            )

            AptvNavItem("全部", selectedGroup == null) {
                selectedGroup = null
                previewManager.clearPreview()
            }
            AptvNavItem("收藏", selectedGroup == "收藏") {
                selectedGroup = "收藏"
                previewManager.clearPreview()
            }
            groups.take(24).forEach { g ->
                AptvNavItem(g, selectedGroup == g) {
                    selectedGroup = g
                    previewManager.clearPreview()
                }
            }
            Spacer(Modifier.weight(1f))
            if (epgLoaded) AptvNavItem("节目表", false, onOpenEpg)
            AptvNavItem("设置", false, onOpenSettings)
        }

        // 频道列表
        Column(
            modifier = Modifier
                .width(NextTvDimens.listWidth)
                .fillMaxHeight()
                .padding(top = 28.dp, end = 10.dp, start = 6.dp)
        ) {
            Text(
                text = (selectedGroup ?: "全部频道") + "  ${displayChannels.size}",
                style = MaterialTheme.typography.titleMedium,
                color = NextTvColors.TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp, start = 6.dp)
            )
            if (displayChannels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("添加播放源后开始观看", color = NextTvColors.TextMuted)
                }
            } else {
                val listState = rememberLazyListState()
                // 焦点频道滚入可视区（跟手）
                LaunchedEffect(focused?.id, displayChannels) {
                    val id = focused?.id ?: return@LaunchedEffect
                    val index = displayChannels.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        listState.animateScrollToItem(index = index, scrollOffset = -120)
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 40.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayChannels, key = { it.id }) { ch ->
                        val title = buildString {
                            ch.number?.let { append("$it  ") }
                            append(ch.name)
                        }
                        val sub = if (epgLoaded)
                            epgRepository.getCurrentProgram(ch.id, ch.tvgId)?.title
                        else ch.group
                        AptvChannelRow(
                            title = title,
                            subtitle = sub,
                            focused = focused?.id == ch.id,
                            playing = currentChannel?.id == ch.id,
                            onFocused = { previewManager.requestPreview(ch) },
                            onClick = {
                                onChannelClick(ch)
                                onOpenPlayer()
                            }
                        )
                    }
                }
            }
        }

        // 沉浸预览
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 24.dp, end = 24.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(NextTvDimens.radiusXl))
                .background(NextTvColors.BgElevated)
        ) {
            when {
                focused != null && previewReady -> {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = {
                            it.player = previewManager.player
                            previewManager.boundPlayerView = it
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                focused != null && previewManager.getCachedFrame(focused!!.id) != null -> {
                    Image(
                        bitmap = previewManager.getCachedFrame(focused!!.id)!!.asImageBitmap(),
                        contentDescription = focused!!.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                !focused?.logo.isNullOrBlank() -> {
                    AsyncImage(
                        model = focused?.logo,
                        contentDescription = focused?.name,
                        modifier = Modifier.fillMaxSize().padding(48.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                focused?.name ?: "选择频道",
                                style = MaterialTheme.typography.headlineMedium,
                                color = NextTvColors.TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "确认键全屏播放",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NextTvColors.TextMuted
                            )
                        }
                    }
                }
            }

            // 底部信息
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(NextTvColors.ScrimBottom)
                    .padding(horizontal = 28.dp, vertical = 22.dp)
            ) {
                Column {
                    Text(
                        text = focused?.name ?: "NextTV",
                        style = MaterialTheme.typography.headlineMedium,
                        color = NextTvColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = program?.title ?: focused?.group ?: "高清直播预览",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NextTvColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (upcoming.size > 1) {
                        Text(
                            text = "稍后  ${upcoming[1].title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NextTvColors.TextMuted,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
