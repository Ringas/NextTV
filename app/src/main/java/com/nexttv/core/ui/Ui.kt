// SPDX-License-Identifier: GPL-3.0-only
// Next TV — Compose for TV implementation.
package com.nexttv.core.ui

import java.util.Locale

object Ui {

    fun hhmm(millis: Long): String =
        ProgrammeTimeLabels.time(millis)

    /** Playback clock for HUDs: mm:ss below an hour, h:mm:ss above. */
    fun playbackTime(millis: Long): String {
        val total = (millis.coerceAtLeast(0L) / 1000L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        else "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    /** 台标文字缩写：CCTV-1综合 -> C1，广东卫视 -> 广东卫 */
    private val cctvShortPattern=Regex("""(?i)cctv[-\s]?(\d+\+?|4k|8k)""")
    private val shortWhitespacePattern=Regex("""\s+""")
    fun shortName(name: String): String {
        val cctv = cctvShortPattern.find(name)
        if (cctv != null) return "C" + cctv.groupValues[1].uppercase(Locale.ROOT)
        val clean = name.trim().replace(shortWhitespacePattern, "")
        return if (clean.length <= 3) clean else clean.take(3)
    }

    /** A source label is identity, not a measured video format: never strip 4K/8K/HD brands. */
    fun displayName(name: String): String = name.trim()
}
