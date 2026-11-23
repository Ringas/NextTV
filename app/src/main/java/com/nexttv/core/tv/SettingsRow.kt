// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SettingsRowData(
    val title: String,
    val value: String = "",
    val explanation: String = "",
    val action: (() -> Unit)? = null,
    val disclosure: Boolean = true,
)

@Composable
fun SettingsRowContent(
    entry: SettingsRowData,
    category: String,
    narrow: Boolean,
    bodyHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(bodyHeight).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppText(entry.title, Modifier.weight(1f), color = NextTvStyle.Text, fontSize = InteractionStyle.body, maxLines = 2)
        if (entry.value.isNotBlank()) {
            AppText(
                entry.value,
                if (category == "关于") Modifier.weight(1.5f)
                else Modifier.widthIn(max = if (narrow) 94.dp else 144.dp),
                color = NextTvStyle.Muted,
                fontSize = InteractionStyle.caption,
                textAlign = TextAlign.End,
                maxLines = if (category == "关于") Int.MAX_VALUE else 2
            )
        }
        if (entry.action != null && entry.disclosure) {
            AppText("›", color = NextTvStyle.Disabled, fontSize = 18.sp)
        }
    }
}
