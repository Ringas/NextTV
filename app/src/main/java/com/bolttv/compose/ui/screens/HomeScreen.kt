package com.bolttv.compose.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bolttv.compose.core.data.ChannelRepository
import com.bolttv.compose.core.data.EpgRepository
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.player.PreviewPlayerManager

/**
 * APTV 风格首页：
 * 左分类 | 中频道列表 | 右大预览（焦点频道实时画面 + 节目信息）
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

    val focusedPreviewChannel by previewManager.focusedChannel.collectAsState()
    val previewReady by previewManager.isReady.collectAsState()

    val displayChannels = remember(channels, selectedGroup) {
        repository.getChannelsByGroup(selectedGroup)
    }

    val focused = focusedPreviewChannel
    val program = if (epgLoaded && focused != null) {
        epgRepository.getCurrentProgram(focused.id, focused.tvgId)
    } else null
    val upcoming = if (epgLoaded && focused != null) {
        epgRepository.getUpcoming(focused.id, focused.tvgId, limit = 2)
    } else emptyList()

    DisposableEffect(Unit) {
        onDispose { previewManager.clearPreview() }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // —— 左：分类 ——
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(Color(0xFF12151A))
                .padding(start = 20.dp, top = 40.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "NextTV",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            )

            GroupChip("全部", selectedGroup == null) {
                selectedGroup = null
                previewManager.clearPreview()
            }
            GroupChip("收藏", selectedGroup == "收藏") {
                selectedGroup = "收藏"
                previewManager.clearPreview()
            }
            groups.forEach { group ->
                GroupChip(group, selectedGroup == group) {
                    selectedGroup = group
                    previewManager.clearPreview()
                }
            }

            Spacer(Modifier.weight(1f))
            if (epgLoaded) {
                GroupChip("节目表", false, onOpenEpg)
            }
            GroupChip("设置", false, onOpenSettings)
        }

        // —— 中：频道列表 ——
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .padding(top = 40.dp, end = 12.dp)
        ) {
            Text(
                text = when (selectedGroup) {
                    null -> "全部频道"
                    else -> selectedGroup!!
                } + " · ${displayChannels.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            if (displayChannels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "请先在设置中添加播放源",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayChannels, key = { it.id }) { channel ->
                        val isFocused = focusedPreviewChannel?.id == channel.id
                        val isPlaying = currentChannel?.id == channel.id
                        ChannelListRow(
                            channel = channel,
                            isFocused = isFocused,
                            isPlaying = isPlaying,
                            epgTitle = if (epgLoaded) {
                                epgRepository.getCurrentProgram(channel.id, channel.tvgId)?.title
                            } else null,
                            onFocused = { previewManager.requestPreview(channel) },
                            onClick = {
                                onChannelClick(channel)
                                onOpenPlayer()
                            }
                        )
                    }
                }
            }
        }

        // —— 右：大预览（APTV 核心观感） ——
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 32.dp, end = 32.dp, bottom = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0C10))
        ) {
            // 视频 / 缓存 / Logo
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
                        update = { view ->
                            view.player = previewManager.player
                            previewManager.boundPlayerView = view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                focused != null && previewManager.getCachedFrame(focused.id) != null -> {
                    val bmp = previewManager.getCachedFrame(focused.id)!!
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmapSafe(),
                        contentDescription = focused.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                !focused?.logo.isNullOrBlank() -> {
                    AsyncImage(
                        model = focused?.logo,
                        contentDescription = focused?.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = focused?.name?.take(1) ?: "选中频道预览",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 底部玻璃信息条
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 28.dp, vertical = 24.dp)
            ) {
                Column {
                    Text(
                        text = focused?.name ?: "移动焦点预览频道",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = program?.title
                            ?: focused?.group
                            ?: "确认键全屏播放",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (upcoming.size > 1) {
                        Text(
                            text = "稍后：${upcoming[1].title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun android.graphics.Bitmap.asImageBitmapSafe(): androidx.compose.ui.graphics.ImageBitmap =
    this.asImageBitmap()

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelListRow(
    channel: Channel,
    isFocused: Boolean,
    isPlaying: Boolean,
    epgTitle: String?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                isPlaying -> MaterialTheme.colorScheme.surfaceVariant
                else -> Color.Transparent
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        channel.number?.let { append("$it  ") }
                        append(channel.name)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        isFocused -> MaterialTheme.colorScheme.primary
                        isPlaying -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!epgTitle.isNullOrBlank()) {
                    Text(
                        text = epgTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isPlaying) {
                Text(
                    "▶",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}
