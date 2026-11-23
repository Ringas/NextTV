// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/** Discrete input follows row/column indices, never a nearest currently composed rectangle. */
internal fun gridFocusTarget(index:Int,count:Int,columns:Int,action:InputAction):Int?{
 if(index !in 0 until count||columns<1)return null
 return when(action){
  InputAction.DOWN->(index+columns).takeIf{it<count}
  InputAction.UP->(index-columns).takeIf{it>=0}
  InputAction.LEFT->(index-1).takeIf{index%columns>0}
  InputAction.RIGHT->(index+1).takeIf{index%columns<columns-1&&it<count}
  else->null
 }
}
