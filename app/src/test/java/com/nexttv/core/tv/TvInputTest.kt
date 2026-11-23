package com.nexttv.core.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvInputTest {
    @Test
    fun swipeActionsRoundTripThroughNativeFocusKeys() {
        val directions = mapOf(
            SwipeDirection.UP to InputAction.UP,
            SwipeDirection.DOWN to InputAction.DOWN,
            SwipeDirection.LEFT to InputAction.LEFT,
            SwipeDirection.RIGHT to InputAction.RIGHT,
        )
        for ((swipe, action) in directions) {
            assertEquals(action, TvInput.swipeAction(swipe))
            assertEquals(action, TvInput.action(requireNotNull(TvInput.directionKey(action))))
        }
    }

    @Test
    fun remoteAndKeyboardAliasesRetainTheirActions() {
        assertEquals(InputAction.NEXT_CHANNEL, TvInput.action(KeyEvent.KEYCODE_CHANNEL_DOWN))
        assertEquals(InputAction.PREVIOUS_CHANNEL, TvInput.action(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals(InputAction.CONFIRM, TvInput.action(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(InputAction.BACK, TvInput.action(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(8, TvInput.digit(KeyEvent.KEYCODE_NUMPAD_8))
        assertEquals(8, TvInput.digit(KeyEvent.KEYCODE_8))
        assertTrue(TvInput.confirm(KeyEvent.KEYCODE_SPACE))
        assertFalse(TvInput.confirm(KeyEvent.KEYCODE_SPACE, includeSpace = false))
        assertNull(TvInput.action(KeyEvent.KEYCODE_TAB))
    }
}
