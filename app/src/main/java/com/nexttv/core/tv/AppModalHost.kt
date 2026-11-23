// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.nexttv.core.tv

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex

private data class ModalEntry(val owner:Any,val content:@Composable ()->Unit)
internal class ModalController {
 private var current by mutableStateOf<ModalEntry?>(null)
 val visible get()=current!=null
 val ownerToken get()=current?.owner
 var focusDefault:(()->Boolean)?=null
 var lastBackgroundFocus:FocusRequester?=null
 private var returnFocus:FocusRequester?=null
 private var pendingReturn=false
 fun restoreBackgroundFocus():Boolean{
  if(!pendingReturn)return false
  pendingReturn=false;val target=returnFocus;returnFocus=null
  return target?.let{runCatching{it.requestFocus()}.getOrDefault(false)}?:false
 }
 fun show(owner:Any,content:@Composable ()->Unit){if(current==null)returnFocus=lastBackgroundFocus;pendingReturn=false;current=ModalEntry(owner,content)}
 fun hide(owner:Any){if(current?.owner===owner){pendingReturn=true;current=null}}
 @Composable fun Render(){current?.content?.invoke()}
}
internal val LocalModalController=staticCompositionLocalOf<ModalController?>{null}
internal val LocalInWindowModal=staticCompositionLocalOf{false}

/** One Android input channel for content and modal cards: no native-window dismissal gap. */
@Composable internal fun AppModalHost(content:@Composable ()->Unit){
 val controller=remember{ModalController()}
 val anchor=remember{FocusRequester()}
 val focusManager=LocalFocusManager.current
 var modalHasFocus by remember{mutableStateOf(false)}
 var anchorFocused by remember{mutableStateOf(false)}
 SideEffect{controller.focusDefault={
  if(!controller.visible||modalHasFocus&&!anchorFocused)true
  else if(runCatching{anchor.requestFocus()}.getOrDefault(false)){focusManager.moveFocus(FocusDirection.Next);true}else false
 }}
 CompositionLocalProvider(LocalModalController provides controller){
  Box(Modifier.fillMaxSize()){
   Box((if(controller.visible)Modifier.fillMaxSize().clearAndSetSemantics{}else Modifier.fillMaxSize()).focusProperties{onEnter={if(controller.visible)cancelFocusChange()else if(controller.restoreBackgroundFocus())cancelFocusChange()}}.focusGroup()){content()}
   if(controller.visible)Box(Modifier.fillMaxSize().zIndex(20f).background(Color.Black.copy(alpha=.40f)).imePadding().onFocusChanged{modalHasFocus=it.hasFocus}.onKeyEvent{event->val direction=modalFocusDirection(event.nativeKeyEvent);if(direction!=null){if(event.nativeKeyEvent.action==android.view.KeyEvent.ACTION_DOWN)focusManager.moveFocus(direction);true}else false}.focusProperties{onExit={if(controller.visible)cancelFocusChange()}}.focusGroup()){
    // A static information sheet has no buttons: it must still own keyboard focus.
    LaunchedEffect(controller.ownerToken){withFrameNanos{};if(controller.focusDefault?.invoke()==false){withFrameNanos{};controller.focusDefault?.invoke()}}
    Box(Modifier.size(1.dp).clearAndSetSemantics{}.onFocusChanged{anchorFocused=it.isFocused}.focusRequester(anchor).focusProperties{canFocus=!modalHasFocus||anchorFocused}.focusable())
    CompositionLocalProvider(LocalInWindowModal provides true){controller.Render()}
   }
  }
 }
}

/** Stable registration; child recomposition updates content without replacing the modal owner. */
@Composable internal fun AppModalContent(content:@Composable ()->Unit){
 val controller=checkNotNull(LocalModalController.current)
 val owner=remember{Any()}
 val latest by rememberUpdatedState(content)
 val render=remember<@Composable ()->Unit>{{latest()}}
 DisposableEffect(controller,owner){controller.show(owner,render);onDispose{controller.hide(owner)}}
}

/** Same-window nodes are attached before effects run; only a native fallback waits for a window. */
internal suspend fun awaitModalFocusWindow(view:android.view.View,inWindow:Boolean){
 if(!inWindow){while(!view.hasWindowFocus())withFrameNanos{};withFrameNanos{};withFrameNanos{}}
}
