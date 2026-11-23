// SPDX-License-Identifier: GPL-3.0-only
// Next TV — home chrome: header, empty state and their pure message policy.
package com.nexttv.core.tv

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 空列表提示的唯一来源，便于单测覆盖优先级。 */
internal fun homeEmptyMessage(
    tab: Int,
    favoritesEmpty: Boolean,
    noSources: Boolean,
    addingSubscription: Boolean,
    updating: Boolean,
    anyEnabled: Boolean,
    hasLoadedChannels: Boolean,
): String = when {
    tab == 1 && favoritesEmpty -> "还没有收藏频道"
    tab == 1 -> "收藏的频道暂不可用，请更新订阅"
    noSources -> "添加订阅后开始使用"
    addingSubscription || updating -> "正在更新订阅…"
    !anyEnabled -> "所有订阅均已停用"
    hasLoadedChannels -> "当前分组下没有频道"
    else -> "订阅暂无可用频道，请重试"
}

internal fun homeEmptyHint(tab: Int, noSources: Boolean, favoritesEmpty: Boolean): String? = when {
    tab == 0 && noSources -> "粘贴 M3U 地址即可导入频道，无需经过多层设置。"
    tab == 1 && favoritesEmpty -> "在频道上长按可添加收藏。"
    else -> null
}

internal fun homeEmptyActionLabel(noSources: Boolean, anyEnabled: Boolean, hasLoadedChannels: Boolean): String =
    if (noSources) "添加订阅" else if (anyEnabled && !hasLoadedChannels) "重试" else "管理订阅"

/** 顶部标题与「分类」入口，和网格焦点顺序解耦。 */
@Composable
internal fun HomeHeader(
    title: String,
    subtitle: String,
    showCategory: Boolean,
    first: FocusRequester,
    navUp: FocusRequester,
    categoryDown: FocusRequester,
    horizontalPadding: Int,
    minHeight: Int,
    onFocus: () -> Unit,
    onCategoryClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .padding(horizontal = horizontalPadding.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, fontSize = InteractionStyle.title, fontWeight = FontWeight.Bold, color = NextTvStyle.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AppText(subtitle, fontSize = InteractionStyle.body, color = NextTvStyle.Muted, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
        }
        if (showCategory) {
            TvButton(
                "分类",
                Modifier
                    .semantics { contentDescription = "频道分类：$title" }
                    .focusRequester(first)
                    .focusProperties {
                        up = navUp
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = categoryDown
                    },
                onFocus = onFocus,
                focusReturn = first,
                onClick = onCategoryClick
            )
        }
    }
}

/** 空列表：主提示 + 可选说明 + 一个恢复入口。 */
@Composable
internal fun HomeEmptyState(
    message: String,
    hint: String?,
    actionLabel: String,
    emptyAction: FocusRequester,
    navUp: FocusRequester,
    horizontalPadding: Int,
    onFocus: () -> Unit,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppText(
            message,
            textAlign = TextAlign.Center,
            color = NextTvStyle.Text,
            fontSize = InteractionStyle.subheading,
            fontWeight = FontWeight.SemiBold
        )
        if (hint != null) {
            AppText(hint, textAlign = TextAlign.Center, color = NextTvStyle.Muted, fontSize = InteractionStyle.caption)
        }
        TvButton(
            actionLabel,
            Modifier
                .focusRequester(emptyAction)
                .focusEdges(down = true, left = true, right = true)
                .focusProperties {
                    up = navUp
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                },
            onFocus = onFocus,
            onClick = onAction
        )
    }
}
