package com.nexttv.core.tv

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent

/** Cancel AND consume edge arrows, so Android's outer focus search cannot handle them again. */
internal fun Modifier.focusEdges(up:Boolean=false,down:Boolean=false,left:Boolean=false,right:Boolean=false):Modifier =
 if(!up&&!down&&!left&&!right)this else focusProperties{if(up)this.up=FocusRequester.Cancel;if(down)this.down=FocusRequester.Cancel;if(left)this.left=FocusRequester.Cancel;if(right)this.right=FocusRequester.Cancel}.onPreviewKeyEvent{e->
  when(TvInput.action(e.nativeKeyEvent.keyCode)){InputAction.UP->up;InputAction.DOWN->down;InputAction.LEFT->left;InputAction.RIGHT->right;else->false}
 }

/** Run only after child widgets have handled caret/slider keys. Never catches Back/Home/volume. */
internal fun modalFocusDirection(key:android.view.KeyEvent):FocusDirection? =
 if(key.keyCode==android.view.KeyEvent.KEYCODE_TAB)if(key.isShiftPressed)FocusDirection.Previous else FocusDirection.Next
 else when(TvInput.action(key.keyCode)){InputAction.UP->FocusDirection.Up;InputAction.DOWN->FocusDirection.Down;InputAction.LEFT->FocusDirection.Left;InputAction.RIGHT->FocusDirection.Right;else->null}
