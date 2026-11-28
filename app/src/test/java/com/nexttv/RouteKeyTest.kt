// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.data.ChannelRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteKeyTest {
    @Test fun healthKeyIsStableOpaqueAndCredentialSafe() {
        val route = ChannelRoute(
            url = "https://user:pass@example.test/live/index.m3u8?token=secret",
            sourceId = "source-a",
            originalId = "channel-a",
            headers = mapOf("Authorization" to "Bearer top-secret", "Referer" to "https://private.test/"),
        )
        val key = route.healthKey
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
        assertFalse(key.contains("secret"))
        assertFalse(key.contains("example.test"))
        assertEquals(key, route.copy().healthKey)
    }

    @Test fun healthKeySeparatesRoutesAndHeaderSets() {
        val first = ChannelRoute("https://example.test/a.m3u8", "s", "a")
        val second = ChannelRoute("https://example.test/b.m3u8", "s", "b")
        val withHeader = first.copy(headers = mapOf("Referer" to "https://example.test/"))
        assertNotEquals(first.healthKey, second.healthKey)
        assertNotEquals(first.healthKey, withHeader.healthKey)
    }
}
