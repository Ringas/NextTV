// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

internal data class VisibleFocusItem(val index:Int,val start:Int,val end:Int)
/** Preserve a visible focused item; never jump to an off-screen saved item on input handoff. */
internal fun visibleFocusIndex(items:List<VisibleFocusItem>,start:Int,end:Int,preferred:Int=-1):Int?{
 val intersecting=items.filter{it.end>start&&it.start<end}
 val complete=intersecting.filter{it.start>=start&&it.end<=end}
 return (complete.firstOrNull{it.index==preferred}?:complete.firstOrNull()?:intersecting.firstOrNull{it.index==preferred}?:intersecting.firstOrNull())?.index
}
internal fun LazyListState.visibleFocusIndex(preferred:Int=-1,enabled:(Int)->Boolean={true}):Int?{
 val l=layoutInfo
 return visibleFocusIndex(l.visibleItemsInfo.filter{enabled(it.index)}.map{VisibleFocusItem(it.index,it.offset,it.offset+it.size)},l.viewportStartOffset+l.beforeContentPadding,l.viewportEndOffset-l.afterContentPadding,preferred)
}
internal fun LazyGridState.visibleFocusIndex(preferred:Int=-1):Int?{
 val l=layoutInfo
 return visibleFocusIndex(l.visibleItemsInfo.map{VisibleFocusItem(it.index,it.offset.y,it.offset.y+it.size.height)},l.viewportStartOffset+l.beforeContentPadding,l.viewportEndOffset-l.afterContentPadding,preferred)
}

/** Header entry at page top must not restore a stale second-row card. Wheel-scrolled entry stays visible. */
internal fun homeHeaderEntryIndex(first:Int,offset:Int,columns:Int,preferred:Int,visible:Int?,topTolerance:Int=1):Int? =
 if(first==0&&offset<=topTolerance.coerceAtLeast(1))preferred.takeIf{it in 0 until columns}?:0 else visible
