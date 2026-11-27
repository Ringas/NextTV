// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.nexttv.R
import com.nexttv.core.App
import kotlinx.coroutines.isActive

private const val LOGO_START = .12f
private const val LOGO_END = .58f
private const val WORD_START = .34f
private const val WORD_END = .92f
private const val SWEEP_START = .90f
private const val SWEEP_END = 1.85f
private const val MIN_HOLD_END = 2.30f
private const val TIMELINE_LIMIT = 4.00f

@Composable
internal fun StartupContent(onExit: () -> Unit) {
    val app = App.get()
    val liveRequested = remember { app.prefs.startLive }
    var homeReady by remember { mutableStateOf(false) }
    var coverActive by remember { mutableStateOf(true) }
    val elapsed = remember { mutableFloatStateOf(0f) }
    val exit = remember { Animatable(0f) }
    val markReady = remember { { homeReady = true } }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive && coverActive && elapsed.floatValue < TIMELINE_LIMIT) {
            withFrameNanos { now ->
                elapsed.floatValue = ((now - start) / 1_000_000_000.0).toFloat().coerceAtMost(TIMELINE_LIMIT)
            }
        }
    }

    val canExit = homeReady && elapsed.floatValue >= MIN_HOLD_END
    LaunchedEffect(canExit) {
        if (!canExit) return@LaunchedEffect
        exit.animateTo(
            1f,
            tween(
                durationMillis = if (app.prefs.reduceMotion) 120 else 240,
                easing = LinearOutSlowInEasing,
            ),
        )
        coverActive = false
    }

    val live = liveRequested && (!app.cacheReady || app.channels.displayChannels.isNotEmpty())
    Box(Modifier.fillMaxSize()) {
        if (app.cacheReady || !liveRequested) {
            val visibility = if (coverActive) Modifier.clearAndSetSemantics {} else Modifier
            Box(Modifier.fillMaxSize().then(visibility)) {
                HomeScreen(onExit, onLaunchReady = markReady.takeIf { coverActive })
            }
        }
        if (coverActive) {
            StartupCover(
                live = live,
                light = app.prefs.lightGlass,
                reduceMotion = app.prefs.reduceMotion,
                elapsed = elapsed.floatValue,
                exitAlpha = exit.value,
            )
        }
    }
}

@Composable
internal fun StartupCover(
    live: Boolean,
    light: Boolean,
    reduceMotion: Boolean,
    elapsed: Float,
    exitAlpha: Float,
) {
    val lightCover = light && !live
    val ink = if (lightCover) Color(0xFF25446E) else Color(0xFFEAF1FF)
    val highlight = if (lightCover) Color(0xFF4F78AA) else Color(0xFFBBD9FF)
    val background = if (live) Modifier.background(Color.Black) else Modifier.background(NextTvStyle.Home)
    val density = LocalDensity.current
    val logoIn = ((elapsed - LOGO_START) / (LOGO_END - LOGO_START)).coerceIn(0f, 1f)
    val wordIn = ((elapsed - WORD_START) / (WORD_END - WORD_START)).coerceIn(0f, 1f)
    val sweep = ((elapsed - SWEEP_START) / (SWEEP_END - SWEEP_START)).coerceIn(0f, 1f)
    val motionScale = AppMotionScale.scaleFactor
    val sweepActive = !reduceMotion && motionScale > 0f

    BoxWithConstraints(
        Modifier.fillMaxSize().then(background).graphicsLayer { alpha = 1f - exitAlpha }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val markHeight = maxHeight * .135f
        val markWidth = markHeight * (108f / 112f)
        val wordSize = (maxHeight.value * .052f).sp
        val gap = maxHeight * .012f
        val wordRise = with(density) { (1f - wordIn) * wordSize.toPx() * .23f }
        val wordBlur = if (reduceMotion) 0.dp else with(density) { ((1f - wordIn) * 4.2f).toDp() }
        val logoBlur = if (reduceMotion) 0.dp else with(density) { ((1f - logoIn) * 4.5f).toDp() }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(if (lightCover) R.drawable.next_tv_brand_light else R.drawable.next_tv_brand_dark),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(markWidth).height(markHeight)
                    .graphicsLayer {
                        alpha = logoIn
                        scaleX = .92f + .08f * logoIn
                        scaleY = .92f + .08f * logoIn
                    }
                    .blur(logoBlur),
            )
            Spacer(Modifier.height(gap))
            StartupWordmark(
                ink = ink,
                highlight = highlight,
                reduceMotion = reduceMotion,
                wordSize = wordSize,
                alpha = wordIn,
                rise = wordRise,
                sweep = if (sweepActive) sweep else 0f,
                blurRadius = wordBlur,
            )
        }
    }
}

@Composable
private fun StartupWordmark(
    ink: Color,
    highlight: Color,
    reduceMotion: Boolean,
    wordSize: TextUnit,
    alpha: Float,
    rise: Float,
    sweep: Float,
    blurRadius: Dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val typeface = remember {
        ResourcesCompat.getFont(context, R.font.next_tv_sans_medium) ?: Typeface.SANS_SERIF
    }
    val paint = remember(typeface, wordSize, density) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { wordSize.toPx() }
            textAlign = Paint.Align.CENTER
            letterSpacing = .01f
        }
    }
    val textWidth = remember(paint) { paint.measureText("Next TV") }
    val width = with(density) { textWidth.toDp() }
    val height = with(density) { (paint.descent() - paint.ascent()).toDp() }

    Box(
        Modifier.width(width).height(height).semantics { contentDescription = "Next TV" }
            .graphicsLayer {
                this.alpha = alpha
                translationY = rise
            }
            .blur(blurRadius)
            .drawBehind {
                val text = "Next TV"
                paint.color = ink.toArgb()
                paint.shader = null
                if (sweep > 0f && !reduceMotion) {
                    val band = textWidth * .19f
                    val center = (sweep * 1.45f - .22f) * textWidth
                    paint.shader = LinearGradient(
                        center - band, 0f, center + band, 0f,
                        intArrayOf(ink.toArgb(), highlight.copy(alpha = .72f).toArgb(), ink.toArgb()),
                        floatArrayOf(0f, .5f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        text,
                        size.width / 2f,
                        size.height / 2f - (paint.ascent() + paint.descent()) / 2f,
                        paint,
                    )
                }
            },
    )
}
