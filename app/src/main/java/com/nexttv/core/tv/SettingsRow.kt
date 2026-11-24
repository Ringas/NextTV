// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexttv.R

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
        val focused = LocalSurfaceFocused.current
        AppText(entry.title, Modifier.weight(1f), color = if (focused) NextTvStyle.Ivory else NextTvStyle.Text, fontSize = InteractionStyle.body, maxLines = 2)
        if (entry.value.isNotBlank()) {
            AppText(
                entry.value,
                if (category == "关于") Modifier.weight(1.5f)
                else Modifier.widthIn(max = if (narrow) 94.dp else 144.dp),
                color = if (focused) NextTvStyle.Ivory else NextTvStyle.Muted,
                fontSize = InteractionStyle.caption,
                textAlign = TextAlign.End,
                maxLines = if (category == "关于") Int.MAX_VALUE else 2
            )
        }
        if (entry.action != null && entry.disclosure) {
            Image(
                painterResource(R.drawable.ic_settings_disclosure),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                colorFilter = ColorFilter.tint(if (focused) NextTvStyle.Ivory else NextTvStyle.Disabled)
            )
        }
    }
}
