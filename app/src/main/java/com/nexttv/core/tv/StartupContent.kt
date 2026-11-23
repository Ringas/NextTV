// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexttv.R
import com.nexttv.core.App
import kotlinx.coroutines.delay

@Composable
internal fun StartupContent(onExit: () -> Unit) {
    val app = App.get()
    val liveRequested = remember { app.prefs.startLive }
    var pending by remember { mutableStateOf(true) }
    val markReady = remember { { pending = false } }
    val live = liveRequested && (!app.cacheReady || app.channels.channels.isNotEmpty())
    Box(Modifier.fillMaxSize()) {
        if (app.cacheReady || !liveRequested) {
            val visibility = if (pending) Modifier.clearAndSetSemantics {} else Modifier
            Box(Modifier.fillMaxSize().then(visibility)) {
                HomeScreen(onExit, onLaunchReady = markReady.takeIf { pending })
            }
        }
        if (pending) {
            StartupCover(live, app.prefs.lightGlass, app.prefs.reduceMotion)
        }
    }
}

@Composable
private fun StartupCover(live: Boolean, light: Boolean, reduceMotion: Boolean) {
    val lightCover = light && !live
    val ink = if (lightCover) Color(0xFF25446E) else Color(0xFFEAF1FF)
    val highlight = if (lightCover) Color(0xFF4F78AA) else Color(0xFFBBD9FF)
    val background = if (live) Modifier.background(Color.Black) else Modifier.background(NextTvStyle.Home)
    var showBrand by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(140)
        showBrand = true
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().then(background).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (showBrand) {
            val shortSide = minOf(maxWidth, maxHeight)
            val scale = (shortSide.value / 540f).coerceIn(.72f, 1.6f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(R.drawable.next_tv_icon_monochrome),
                    contentDescription = null,
                    modifier = Modifier.size(58.dp * scale).graphicsLayer { scaleX = 3f; scaleY = 3f },
                    colorFilter = ColorFilter.tint(ink),
                )
                Spacer(Modifier.height(7.dp * scale))
                StartupWordmark(ink, highlight, reduceMotion, scale)
            }
        }
    }
}

@Composable
private fun StartupWordmark(ink: Color, highlight: Color, reduceMotion: Boolean, scale: Float) {
    val motionScale = AppMotionScale.scaleFactor
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion, motionScale) {
        phase.floatValue = 0f
        if (reduceMotion || motionScale <= 0f) return@LaunchedEffect
        val start = withFrameNanos { it }
        val period = 1_400_000_000.0 * motionScale
        while (true) {
            withFrameNanos { phase.floatValue = (((it - start) % period) / period).toFloat() }
        }
    }
    Box(
        Modifier.size(230.dp * scale, 52.dp * scale).semantics { contentDescription = "Next TV" }
            .drawWithCache {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink.toArgb()
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    textSize = (37.sp * scale).toPx()
                    textAlign = Paint.Align.CENTER
                }
                val name = "Next TV"
                val width = paint.measureText(name)
                val baseline = size.height / 2f - (paint.ascent() + paint.descent()) / 2f
                val gradient = LinearGradient(
                    0f, 0f, width * .45f, 0f,
                    intArrayOf(ink.toArgb(), highlight.toArgb(), ink.toArgb()),
                    null, Shader.TileMode.CLAMP,
                )
                val offset = Matrix()
                onDrawBehind {
                    if (!reduceMotion && motionScale > 0f) {
                        offset.setTranslate(center.x - width + width * 2f * phase.floatValue, 0f)
                        gradient.setLocalMatrix(offset)
                        paint.shader = gradient
                    }
                    drawIntoCanvas { it.nativeCanvas.drawText(name, center.x, baseline, paint) }
                }
            },
    )
}
