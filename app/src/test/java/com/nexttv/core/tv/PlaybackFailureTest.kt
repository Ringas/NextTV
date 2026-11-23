package com.nexttv.core.tv

import androidx.media3.common.PlaybackException
import com.nexttv.StreamHttpStatusException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFailureTest {
    @Test
    fun resolvesHttpStatusFromNestedCause() {
        val error = IllegalStateException(StreamHttpStatusException(403))
        assertEquals("播放源拒绝访问（HTTP 403）", playbackFailureMessage(error))
    }

    @Test
    fun usesSpecificNetworkCauseBeforeGenericPlayerCode() {
        val error = IllegalStateException("Source error", UnknownHostException("Internal host"))
        assertEquals(
            "无法解析播放源域名",
            playbackFailureMessage(error, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
        )
    }

    @Test
    fun fallsBackToChineseWithoutExposingRawErrorMessage() {
        val error = IllegalStateException("Raw error")
        assertEquals("播放时发生未知错误", playbackFailureMessage(error, PlaybackException.ERROR_CODE_UNSPECIFIED))
        assertEquals("无法打开播放源", playbackFailureMessage(error))
    }
}
