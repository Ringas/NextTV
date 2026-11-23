package com.nexttv

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent

internal object MotionProbe {
    @Volatile var frame: ((Float,Float,Int,Int)->Unit)? = null
}

fun fastReturnDuration(distance:Float,viewport:Int):Int =
    (220+(kotlin.math.abs(distance)/viewport.coerceAtLeast(1)*35).toInt()).coerceIn(220,420)

/** Observe actual grid draws for debug scroll tests; paints no selection layer. */
fun Modifier.observeGridDraws(state:LazyGridState):Modifier=drawWithContent {
    drawContent()
    MotionProbe.frame?.let { probe ->
        val anchor=state.layoutInfo.visibleItemsInfo.firstOrNull()
        if(anchor!=null)probe(anchor.offset.x.toFloat(),anchor.offset.y.toFloat(),state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset)
    }
}
