// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow

/** Independent page-local exit gates. Time is supplied by the monotonic platform clock. */
class ExitConfirmation(private val windowMs:Long=2000){
 private var armedAt:Long?=null
 fun press(now:Long):Boolean{
  val previous=armedAt
  if(previous!=null&&now>=previous&&now-previous<=windowMs){reset();return true}
  armedAt=now;return false
 }
 fun reset(){armedAt=null}
}

/** Settings headers are static labels; only actual setting rows own focus. */
@Stable class SettingsNavigation {
 var cancelPendingFocus:()->Unit={}
 var top:FocusRequester?=null
 val scroll=ScrollState(0)
 val rows=mutableMapOf<String,FocusRequester>()
 val coordinates=mutableMapOf<String,LayoutCoordinates>()
 var viewport:LayoutCoordinates?=null
 private fun visibleKey():String?{
  val bounds=viewport?.takeIf{it.isAttached}?.boundsInWindow()?:return null
  val keys=coordinates.entries.filter{it.value.isAttached}.sortedBy{it.value.positionInWindow().y}
  val entries=keys.mapIndexed{i,e->val y=e.value.positionInWindow().y.toInt();VisibleFocusItem(i,y,y+e.value.size.height)}
  return visibleFocusIndex(entries,bounds.top.toInt(),bounds.bottom.toInt(),keys.indexOfFirst{it.key==row})?.let{keys[it].key}
 }
 fun resumeVisible():Boolean{
  val key=visibleKey()?:return false
  if(key==row)return false
  return runCatching{rows[key]?.requestFocus()?:return false;true}.getOrDefault(false)
 }
 var group by mutableStateOf("订阅与节目单")
 var row by mutableStateOf("分类/订阅与节目单")
 var onContentFocus:()->Unit={}
 var onRowFocus:(String,String?)->Unit={_,_->}
 fun focused(section:String,key:String?){group=section;if(key!=null)row=key;onContentFocus();onRowFocus(section,key)}
 fun resetEntry(){group="订阅与节目单";row="分类/订阅与节目单"}
 fun focusRow(){runCatching{rows[row]?.requestFocus()}}
}
