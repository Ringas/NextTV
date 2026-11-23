// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import com.nexttv.core.data.Channel
import kotlinx.coroutines.*

/** Input cancellation guards do not affect layout and must not recompose the whole home per key. */
internal class GridInputBookkeeping {
 var job:Job?=null
 var target:String?=null
 var serial=0
 var entering=false
 var entryGeneration=0
}

internal fun moveHomeGrid(
 action: InputAction,
 all: List<Channel>,
 columns: Int,
 grid: LazyGridState,
 focusers: Map<String, FocusRequester>,
 gridInput: GridInputBookkeeping,
 currentFocusId: () -> String,
 density: Float,
 scope: CoroutineScope,
 reduceMotion: () -> Boolean,
): Boolean {
  val start=all.indexOfFirst{it.id==(gridInput.target?:currentFocusId())}
  if(start<0)return false
  if(action==InputAction.UP&&start<columns){gridInput.job?.cancel();gridInput.target=null;return false}
  val target=gridFocusTarget(start,all.size,columns,action)?:return true
  val id=all[target].id
  gridInput.target=id
  val margin=12*density
  fun complete(item:LazyGridItemInfo,layout:LazyGridLayoutInfo)=item.offset.y>=layout.viewportStartOffset+margin-1&&item.offset.y+item.size.height<=layout.viewportEndOffset-margin+1
  val vertical = action == InputAction.UP || action == InputAction.DOWN
  fun distance(top: Float, height: Float, layout: LazyGridLayoutInfo): Float {
   val start = layout.viewportStartOffset + margin
   val end = layout.viewportEndOffset - margin
   val anchor = if (vertical) secondFocusRow(
    height, 16 * density,
    (layout.viewportEndOffset - layout.viewportStartOffset).toFloat(), margin
   ) else null
   val delta = when {
    anchor != null -> top - layout.viewportStartOffset - anchor
    top < start -> top - start
    top + height > end -> top + height - end
    else -> 0f
   }
   return if ((delta < 0 && !grid.canScrollBackward) || (delta > 0 && !grid.canScrollForward)) 0f else settledScrollDistance(delta)
  }
  fun ready(key: String): Boolean {
   val layout = grid.layoutInfo
   return layout.visibleItemsInfo.any {
    it.key == key && complete(it, layout) && distance(it.offset.y.toFloat(), it.size.height.toFloat(), layout) == 0f
   } && focusers.containsKey(key)
  }
  if(ready(id)){
   gridInput.job?.cancel()
   runCatching{focusers[id]?.requestFocus()}
  }else if(gridInput.job?.isActive!=true)gridInput.job=scope.launch{
   // One scroll owner retains velocity while repeated keys only update its goal.
   // Never cancel/restart a tween for every remote repeat.
   var velocity=0f;var previousFrame=0L;var stalled=0
   grid.scroll(MutatePriority.UserInput){
    while(isActive&&!InteractionStyle.pointer){
     val frame=withFrameNanos{it}
     if(InteractionStyle.pointer)break
     val elapsed=if(previousFrame==0L).016f else (frame-previousFrame)/1_000_000_000f
     previousFrame=frame
     val goal=gridInput.target?:break
     val goalIndex=all.indexOfFirst{it.id==goal};if(goalIndex<0)break
     if(ready(goal)){
      runCatching{focusers[goal]?.requestFocus()}
      if(currentFocusId()==goal)break
      if(++stalled>=30)break
      continue
     }
     val layout=grid.layoutInfo
     val firstItem=layout.visibleItemsInfo.firstOrNull()?:break
     val actual=layout.visibleItemsInfo.firstOrNull{it.key==goal}
     val stride=(layout.visibleItemsInfo.firstOrNull{it.index/columns>firstItem.index/columns}?.let{it.offset.y-firstItem.offset.y}?:firstItem.size.height+16*density).toFloat()
     val top=actual?.offset?.y?.toFloat()?:firstItem.offset.y+(goalIndex/columns-firstItem.index/columns)*stride
     val height=actual?.size?.height?:firstItem.size.height
     val distance=distance(top,height.toFloat(),layout)
     if(distance==0f){velocity=0f;if(++stalled>=30)break;continue}
     val motionScale=currentCoroutineContext()[androidx.compose.ui.MotionDurationScale]?.scaleFactor?:1f
     val step=if(reduceMotion()||motionScale<=0f)FocusScrollAdvance(distance,0f)else advanceFocusScroll(distance,velocity,elapsed/motionScale,layout.viewportEndOffset.toFloat()-layout.viewportStartOffset,height.toFloat())
     val moved=scrollBy(step.delta)
     velocity=step.velocity
     if(kotlin.math.abs(moved)<.1f){if(++stalled>=6)break}else stalled=0
     // Focus stays on complete same-column rows; this never pins an off-screen
     // old cell or requests a clipped target while the viewport catches up.
     val updated=grid.layoutInfo
     val currentIndex=all.indexOfFirst{it.id==currentFocusId()}.coerceAtLeast(0)
     var candidate:LazyGridItemInfo?=null
     for(item in updated.visibleItemsInfo){
      if(item.index%columns==goalIndex%columns&&item.index in minOf(currentIndex,goalIndex)..maxOf(currentIndex,goalIndex)&&complete(item,updated)){
       if(candidate==null||if(goalIndex>=currentIndex)item.index>candidate.index else item.index<candidate.index)candidate=item
      }
     }
     val next=candidate?.key as? String
     if(next!=null&&next!=currentFocusId()&&gridInput.target==goal&&!InteractionStyle.pointer)runCatching{focusers[next]?.requestFocus()}
    }
   }
  }
  return true
 }
