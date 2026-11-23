@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.nexttv.core.tv

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.*
import kotlin.math.abs

/** Accumulate wheel distance instead of producing focus keys or queuing per-notch animations. */
@Composable internal fun Modifier.continuousWheel(state:ScrollableState):Modifier {
 val step=with(LocalDensity.current){48f*density}
 return pointerInput(state,step){coroutineScope{
  var pending=0f;var job:Job?=null
  // Keyboard ownership cancels residual wheel smoothing before focus starts moving.
  launch{snapshotFlow{InteractionStyle.pointer}.collect{pointer->if(!pointer){pending=0f;job?.cancel()}}}
  awaitPointerEventScope{while(true){val e=awaitPointerEvent(PointerEventPass.Initial)
   if(e.type==PointerEventType.Press){pending=0f;job?.cancel()}
   if(e.type==PointerEventType.Scroll){val delta=TvInput.wheelDistance(e)*step
    if(delta!=0f){InteractionStyle.pointerInput();pending+=delta;e.changes.forEach{it.consume()}
     if(job?.isActive!=true)job=launch{state.scroll{
      try{while(abs(pending)>.5f){withFrameNanos{};val amount=if(abs(pending)<=2f)pending else pending*.42f;val consumed=scrollBy(amount);pending-=consumed;if(abs(consumed)<.1f){pending=0f;break}}}finally{pending=0f}
     }}
    }
   }
  }}
 }}
}
