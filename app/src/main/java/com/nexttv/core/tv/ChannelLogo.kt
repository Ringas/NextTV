// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexttv.core.data.Channel
import com.nexttv.core.ui.LogoLoader
import com.nexttv.core.ui.Ui

/** Shared channel mark for home grid, lists, and player chrome. */
@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier, showFallback: Boolean = true) {
    val context = LocalContext.current
    var logo by remember(channel.logo) { mutableStateOf<Bitmap?>(LogoLoader.cached(channel.logo)) }
    DisposableEffect(channel.logo) {
        val cancel = if (logo == null) LogoLoader.fetch(context, channel.logo) { logo = it } else ({})
        onDispose { cancel() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (logo != null) {
            Image(
                logo!!.asImageBitmap(),
                "${channel.name} 台标",
                Modifier.fillMaxSize().padding(10.dp),
                contentScale = ContentScale.Fit
            )
        } else if (showFallback) {
            AppText(
                Ui.shortName(channel.name),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0x4DFFFFFF)
            )
        }
    }
}
