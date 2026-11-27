// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/**
 * The background probe is a guest on the TV: it may use more workers only while
 * the home screen is idle. During stable playback it may run a tiny low-rate
 * background sweep, but while a route switch/recovery is in flight it sends nothing.
 */
internal object ProbeGatePolicy {
    enum class Mode { IDLE, PLAYING, RECOVERING }

    fun mode(playbackActive: Boolean, starting: Boolean, quietRecovery: Boolean): Mode = when {
        !playbackActive -> Mode.IDLE
        starting || quietRecovery -> Mode.RECOVERING
        else -> Mode.PLAYING
    }

    fun allowsFullScan(mode: Mode): Boolean = mode == Mode.IDLE

    /** A stable playback may still run a tiny background sweep; recovery may not. */
    fun allowsBackgroundSweep(mode: Mode): Boolean = mode != Mode.RECOVERING

    fun concurrency(mode: Mode, idle: Int, playing: Int): Int = when (mode) {
        Mode.IDLE -> idle
        Mode.PLAYING -> playing
        Mode.RECOVERING -> 0
    }
}
