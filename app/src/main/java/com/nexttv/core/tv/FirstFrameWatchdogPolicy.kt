// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

/**
 * First-frame watchdog policy for slow but healthy upstreams.
 *
 * The old watchdog killed every attempt after a fixed short deadline even when
 * bytes were still arriving. This policy keeps waiting while the source makes
 * progress, and only fails when the source stops producing data for a while or
 * the absolute first-frame budget is exhausted.
 */
internal object FirstFrameWatchdogPolicy {
    const val MAX_HARD_WAIT_MILLIS = 30_000L
    const val MAX_ZERO_DATA_WAIT_MILLIS = 10_000L
    const val MIN_ZERO_DATA_WAIT_MILLIS = 500L

    data class Failure(val code: String, val message: String)

    fun hardWaitMillis(budgetMillis: Long): Long =
        minOf(MAX_HARD_WAIT_MILLIS, budgetMillis.coerceAtLeast(1L))

    fun zeroDataWaitMillis(budgetMillis: Long): Long =
        minOf(MAX_ZERO_DATA_WAIT_MILLIS, (budgetMillis / 3).coerceAtLeast(MIN_ZERO_DATA_WAIT_MILLIS))

    fun failure(elapsedMillis: Long, sinceDataMillis: Long, budgetMillis: Long): Failure? {
        val zeroDataWait = zeroDataWaitMillis(budgetMillis)
        val hardWait = hardWaitMillis(budgetMillis)
        if (sinceDataMillis >= zeroDataWait) {
            return Failure(
                "FIRST_FRAME_NO_DATA",
                "等待首帧或缓冲超时（上游 ${zeroDataWait / 1000} 秒无数据）",
            )
        }
        if (elapsedMillis >= hardWait) {
            return Failure(
                "FIRST_FRAME_OR_BUFFER_TIMEOUT",
                "等待首帧或缓冲超时（已等待 ${hardWait / 1000} 秒）",
            )
        }
        return null
    }
}
