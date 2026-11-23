package com.nexttv

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLabelsTest {
    @Test fun resolutionKeepsRequiredSuffix() {
        mapOf(480 to "480P", 720 to "720P", 1080 to "1080P", 1440 to "1440P", 2160 to "4K", 4320 to "8K", 0 to "—", -1 to "—")
            .forEach { (height, expected) -> assertEquals(expected, resolutionLabel(height)) }
    }
}
