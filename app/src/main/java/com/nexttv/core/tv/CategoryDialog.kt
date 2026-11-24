// SPDX-License-Identifier: GPL-3.0-only
// Next TV — the dedicated category sheet from the Nocturne design.
package com.nexttv.core.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

internal data class CategoryChoice(val id: String, val label: String, val count: Int)

/**
 * One sheet, one purpose: pick a channel group. Counts stay on the right, the
 * selected row gets a 2dp ivory rule and 6% lift instead of a check mark.
 */
@Composable
internal fun CategoryDialog(
    choices: List<CategoryChoice>,
    selectedId: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    val selected = choices.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
    val requests = remember(choices) { choices.associate { it.id to FocusRequester() } }
    val view = LocalView.current
    val inWindowModal = LocalInWindowModal.current
    LaunchedEffect(choices) {
        awaitModalFocusWindow(view, inWindowModal)
        withFrameNanos {}
        requests[choices.getOrNull(selected)?.id]?.requestFocus()
    }
    TvDialog(
        "频道分类", onDismiss, width = 410.dp,
        titleTrailing = { AppText("返回", Modifier.padding(start = 12.dp, top = 4.dp), fontSize = InteractionStyle.captionSmall, color = NextTvStyle.Muted) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                "${choices.size - 1} 个分类 · ${choices.firstOrNull { it.id == "" }?.count ?: choices.sumOf { it.count }} 个频道",
                fontSize = InteractionStyle.captionSmall,
                color = NextTvStyle.Muted,
            )
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 320.dp)
                .appVerticalScroll(rememberScrollState())
        ) {
            choices.forEachIndexed { index, choice ->
                val request = requests.getValue(choice.id)
                TvSurface(
                    Modifier.fillMaxWidth()
                        .focusRequester(request)
                        .focusEdges(up = index == 0, down = index == choices.lastIndex)
                        .focusProperties {
                            canFocus = true
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            if (index == 0) up = FocusRequester.Cancel
                            if (index == choices.lastIndex) down = FocusRequester.Cancel
                        },
                    kind = "categoryRow",
                    selected = choice.id == selectedId,
                    onClick = { onChoose(choice.id) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            choice.label,
                            Modifier.weight(1f),
                            fontSize = InteractionStyle.body,
                            maxLines = 1,
                        )
                        AppText(
                            choice.count.toString(),
                            fontSize = InteractionStyle.caption,
                            color = NextTvStyle.Muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(.5.dp).background(NextTvStyle.Divider))
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("确认进入分类", fontSize = InteractionStyle.captionSmall, color = NextTvStyle.Muted)
            Spacer(Modifier.weight(1f))
            AppText("长按无操作 · 返回关闭", fontSize = InteractionStyle.captionSmall, color = NextTvStyle.Muted)
        }
    }
}
