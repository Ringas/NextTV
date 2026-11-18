package com.bolttv.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bolttv.compose.core.data.ChannelRepository
import com.bolttv.compose.core.data.EpgRepository
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.player.PreviewPlayerManager
import com.bolttv.compose.ui.components.ChannelCard

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

    val configuration = LocalConfiguration.current
    val contentWidthDp = configuration.screenWidthDp - 220 - 72
    val adaptiveColumns = remember(contentWidthDp) {
        (contentWidthDp / 260).coerceIn(3, 6)
    }

    DisposableEffect(Unit) {
        onDispose { previewManager.clearPreview() }
    }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .padding(start = 32.dp, top = 48.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "分类",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            GroupChip(
                label = "全部",
                selected = selectedGroup == null,
                onClick = {
                    selectedGroup = null
                    previewManager.clearPreview()
                }
            )
            groups.forEach { group ->
                GroupChip(
                    label = group,
                    selected = selectedGroup == group,
                    onClick = {
                        selectedGroup = group
                        previewManager.clearPreview()
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (epgLoaded) {
                GroupChip(label = "节目表", selected = false, onClick = onOpenEpg)
            }
            GroupChip(label = "设置", selected = false, onClick = onOpenSettings)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 48.dp, end = 40.dp)
        ) {
            Text(
                text = buildString {
                    append(selectedGroup ?: "全部频道")
                    append(" (${displayChannels.size})")
                    append(" · ${adaptiveColumns}列")
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            if (displayChannels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "请先在设置中添加播放源",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(adaptiveColumns),
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        end = 4.dp,
                        bottom = 48.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = displayChannels,
                        key = { it.id }
                    ) { channel ->
                        val isPreview = focusedPreviewChannel?.id == channel.id && previewReady

                        ChannelCard(
                            channel = channel,
                            isPlaying = currentChannel?.id == channel.id,
                            isPreviewActive = isPreview,
                            previewManager = previewManager,
                            currentProgram = if (epgLoaded) {
                                epgRepository.getCurrentProgram(channel.id, channel.tvgId)
                            } else null,
                            onClick = {
                                onChannelClick(channel)
                                onOpenPlayer()
                            },
                            onFocused = { focused ->
                                previewManager.requestPreview(focused)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
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
            else MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
