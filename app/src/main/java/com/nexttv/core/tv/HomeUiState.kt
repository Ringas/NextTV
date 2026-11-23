// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.nexttv.core.data.Channel

/**
 * Navigation and overlay state for [HomeScreen], kept out of the giant composable body.
 * Exposes Compose MutableState so callers can write `var tab by ui.tab`.
 */
class HomeUiState(initialPlaying: Channel? = null) {
    val revision = mutableIntStateOf(0)
    val tab = mutableIntStateOf(0)
    val sources = mutableStateOf(false)
    val group = mutableStateOf("")
    val playing = mutableStateOf(initialPlaying)
    val status = mutableStateOf("")
    val channelDigits = mutableStateOf("")
    val firstCardFocusPending = mutableStateOf(false)
    val addingSubscription = mutableStateOf(false)
    val cardInput = mutableStateOf(false)
    val menuChannel = mutableStateOf<Channel?>(null)
    val dialog = mutableStateOf("")
    val firstFrameReady = mutableStateOf(false)
    val navFocused = mutableStateOf(true)
    val homeNoticeEpoch = mutableIntStateOf(0)
    val restoringHome = mutableStateOf(true)
    val homeNotice = mutableStateOf("")
    val settingsRestore = mutableIntStateOf(0)
    val returningToTop = mutableStateOf(false)
    val returnGeneration = mutableIntStateOf(0)
}
