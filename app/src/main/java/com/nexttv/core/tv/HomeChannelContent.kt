// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.nexttv.core.App
import com.nexttv.core.data.Channel

@Composable
internal fun HomeChannelTile(
    channel: Channel,
    index: Int,
    count: Int,
    columns: Int,
    tab: Int,
    focusers: MutableMap<String, FocusRequester>,
    topFocus: FocusRequester,
    caption: String,
    favorite: Boolean,
    menuOpen: Boolean,
    previewSlot: Int?,
    previewRunning: Boolean,
    onFocus: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onMenuBounds: (Rect) -> Unit,
    onFavorite: () -> Unit,
) {
    val request = remember(channel.id) { FocusRequester() }
    DisposableEffect(channel.id, request) {
        focusers[channel.id] = request
        onDispose { if (focusers[channel.id] === request) focusers.remove(channel.id) }
    }
    val leftEdge = index % columns == 0
    val rightEdge = index % columns == columns - 1 || index == count - 1
    val bottomEdge = index + columns >= count
    TvSurface(
        Modifier.fillMaxWidth().focusRequester(request)
            .focusEdges(left = leftEdge, right = rightEdge, down = bottomEdge)
            .focusProperties {
                if (index < columns) up = topFocus
                if (leftEdge) left = FocusRequester.Cancel
                if (rightEdge) right = FocusRequester.Cancel
                if (bottomEdge) down = FocusRequester.Cancel
            },
        kind = if (columns == 1) "list" else "card",
        focusReturn = request, onFocus = onFocus, onLongClick = onLongClick,
        longClickLabel = "收藏操作", focusRing = false, onClick = onClick,
    ) {
        ChannelCardContent(channel, caption, favorite, tab != 1, columns == 1,
            preview = previewSlot != null, previewRunning = previewRunning, previewSlot = previewSlot ?: 0)
        if (menuOpen) {
            val favoriteAction = remember { FocusRequester() }
            LaunchedEffect(channel.id) { withFrameNanos {}; favoriteAction.requestFocus() }
            Box(
                Modifier.matchParentSize().onGloballyPositioned { onMenuBounds(it.boundsInWindow()) }
                    .panelSurface(RoundedCornerShape(InteractionStyle.cardRadius)).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                TvButton(
                    if (favorite) "取消收藏" else "添加收藏",
                    Modifier.fillMaxWidth().focusRequester(favoriteAction)
                        .focusEdges(up = true, down = true, left = true, right = true)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                    onClick = {
                        onFavorite()
                        if (tab == 0) runCatching { request.requestFocus() }
                    },
                )
            }
        }
    }
}

@Composable
internal fun HomeChannelDialogs(
    dialog: String,
    groups: List<String>,
    selectedGroup: String,
    onDismissSubscription: () -> Unit,
    onSubscription: (String) -> Unit,
    onDismissCategory: () -> Unit,
    onCategory: (String) -> Unit,
) {
    when (dialog) {
        "添加订阅" -> AddressInputDialog("", epg = false, onDismiss = onDismissSubscription, onConfirm = onSubscription)
        "分类" -> {
            val channels = App.get().channels.displayChannels
            CategoryDialog(
                choices = listOf(CategoryChoice("", "全部频道", channels.size)) + groups.map { group ->
                    CategoryChoice(group, group, channels.count { it.groupName == group })
                },
                selectedId = selectedGroup,
                onDismiss = onDismissCategory,
                onChoose = onCategory,
            )
        }
    }
}
