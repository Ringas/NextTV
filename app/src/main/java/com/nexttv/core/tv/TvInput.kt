// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.view.KeyEvent as K
import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType

/** Shared semantic vocabulary. Tab and text-editing keys remain owned by native controls. */
internal enum class InputAction{UP,DOWN,LEFT,RIGHT,CONFIRM,BACK,MENU,INFO,PLAY_PAUSE,SEEK_FORWARD,SEEK_BACKWARD,NEXT_CHANNEL,PREVIOUS_CHANNEL}

@JvmInline
internal value class TvKey(private val event: K) {
 val action get()=TvInput.action(event.keyCode)
 val down get()=event.action==K.ACTION_DOWN
 val up get()=event.action==K.ACTION_UP
 val repeated get()=event.repeatCount>0
 val digit get()=TvInput.digit(event.keyCode)
 val horizontal get()=TvInput.horizontal(event.keyCode)
 val vertical get()=TvInput.vertical(event.keyCode)
 val space get()=event.keyCode==K.KEYCODE_SPACE
 val tab get()=event.keyCode==K.KEYCODE_TAB
 val escape get()=event.keyCode==K.KEYCODE_ESCAPE
}

internal object TvInput {
 fun action(code:Int):InputAction?=when(code){
  K.KEYCODE_DPAD_UP->InputAction.UP;K.KEYCODE_DPAD_DOWN->InputAction.DOWN
  K.KEYCODE_DPAD_LEFT->InputAction.LEFT;K.KEYCODE_DPAD_RIGHT->InputAction.RIGHT
  K.KEYCODE_DPAD_CENTER,K.KEYCODE_ENTER,K.KEYCODE_NUMPAD_ENTER,K.KEYCODE_SPACE->InputAction.CONFIRM
  K.KEYCODE_BACK,K.KEYCODE_ESCAPE->InputAction.BACK
  K.KEYCODE_MENU->InputAction.MENU;K.KEYCODE_INFO->InputAction.INFO
  K.KEYCODE_MEDIA_PLAY_PAUSE->InputAction.PLAY_PAUSE
  K.KEYCODE_MEDIA_FAST_FORWARD->InputAction.SEEK_FORWARD;K.KEYCODE_MEDIA_REWIND->InputAction.SEEK_BACKWARD
  K.KEYCODE_CHANNEL_DOWN,K.KEYCODE_MEDIA_NEXT->InputAction.NEXT_CHANNEL
  K.KEYCODE_CHANNEL_UP,K.KEYCODE_MEDIA_PREVIOUS->InputAction.PREVIOUS_CHANNEL
  else->null
 }
 fun confirm(code:Int,includeSpace:Boolean=true)=action(code)==InputAction.CONFIRM&&(includeSpace||code!=K.KEYCODE_SPACE)
 fun back(code:Int)=action(code)==InputAction.BACK
 fun vertical(code:Int)=when(action(code)){InputAction.UP,InputAction.DOWN->true;else->false}
 fun horizontal(code:Int):Int?=horizontal(action(code))
 fun digit(code:Int):Int?=when(code){in K.KEYCODE_0..K.KEYCODE_9->code-K.KEYCODE_0;in K.KEYCODE_NUMPAD_0..K.KEYCODE_NUMPAD_9->code-K.KEYCODE_NUMPAD_0;else->null}
 fun swipeAction(direction:SwipeDirection)=when(direction){
  SwipeDirection.UP->InputAction.UP
  SwipeDirection.DOWN->InputAction.DOWN
  SwipeDirection.LEFT->InputAction.LEFT
  SwipeDirection.RIGHT->InputAction.RIGHT
 }
 fun directionKey(direction:InputAction):Int?=when(direction){
  InputAction.UP->K.KEYCODE_DPAD_UP
  InputAction.DOWN->K.KEYCODE_DPAD_DOWN
  InputAction.LEFT->K.KEYCODE_DPAD_LEFT
  InputAction.RIGHT->K.KEYCODE_DPAD_RIGHT
  else->null
 }
 fun horizontal(direction:InputAction?):Int?=when(direction){
  InputAction.LEFT->-1
  InputAction.RIGHT->1
  else->null
 }
 fun wheelDistance(event:PointerEvent):Float=
  if(event.type==PointerEventType.Scroll)event.changes.sumOf{it.scrollDelta.y.toDouble()}.toFloat()else 0f
 fun pointerInteraction(event:MotionEvent)=
  event.actionMasked==MotionEvent.ACTION_SCROLL||event.actionMasked==MotionEvent.ACTION_HOVER_MOVE
}
