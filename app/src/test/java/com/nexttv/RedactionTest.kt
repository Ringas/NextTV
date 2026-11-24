package com.nexttv

import com.nexttv.core.data.redactSensitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactionTest {
    @Test fun crashLogDropsUrlCredentialsAndQuery() {
        val report = "GET https://user:pass@example.test/live/list.m3u?token=secret&x=1 failed\nAuthorization=Bearer abc"
        val redacted = redactSensitive(report)
        assertFalse(redacted.contains("user:pass"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("Bearer"))
        assertEquals("GET https://example.test/live/list.m3u?<redacted> failed\nAuthorization=<redacted>", redacted)
    }
}
