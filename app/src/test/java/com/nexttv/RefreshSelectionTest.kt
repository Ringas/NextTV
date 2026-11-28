// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.data.Channel
import com.nexttv.core.data.RefreshOutcome
import com.nexttv.core.data.Source
import com.nexttv.core.data.selectRefreshOutcomes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-source refresh planning: a failed/stale source must not discard healthy sources. */
class RefreshSelectionTest {
    private fun source(id: String, enabled: Boolean = true) = Source(id = id, name = id, url = "http://$id/list.m3u", enabled = enabled)
    private fun channel(sourceId: String, name: String) =
        Channel(id = "$sourceId-$name", name = name, tvgId = "", logo = "", group = "", url = "http://$sourceId/$name", sourceId = sourceId)

    @Test fun laterSourceIsTrimmedAtGlobalCap() {
        val s1 = source("s1")
        val s2 = source("s2")
        val previous = mapOf("s1" to listOf(channel("s1", "old")))
        val selection = selectRefreshOutcomes(
            listOf(
                RefreshOutcome(s1, listOf(channel("s1", "a"), channel("s1", "b"), channel("s1", "c")), null, true, "", null),
                RefreshOutcome(s2, listOf(channel("s2", "d"), channel("s2", "e"), channel("s2", "f")), null, true, "", null)
            ),
            previous,
            channelCap = 4
        )

        assertEquals(4, selection.channels.size)
        assertEquals(setOf("s1"), selection.refreshed)
        assertEquals(1, selection.failed)
        assertTrue(selection.expiredFavorites.isNotEmpty())
        assertTrue(selection.sources.last().lastError.contains("仅保留前 1 个"))
    }

    @Test fun failedSourceKeepsFallbackChannelsAndCountsOnce() {
        val s1 = source("s1")
        val fallback = listOf(channel("s1", "old-a"), channel("s1", "old-b"))
        val selection = selectRefreshOutcomes(
            listOf(RefreshOutcome(s1, fallback, null, false, "超时", null)),
            mapOf("s1" to fallback)
        )

        assertEquals(2, selection.channels.size)
        assertTrue(selection.refreshed.isEmpty())
        assertEquals(1, selection.failed)
        assertEquals("超时", selection.sources.single().lastError)
        assertTrue(selection.expiredFavorites.isEmpty())
    }

    @Test fun disabledSourceIsNotReportedAsFailure() {
        val s1 = source("s1", enabled = false)
        val channels = listOf(channel("s1", "off"))
        val selection = selectRefreshOutcomes(
            listOf(RefreshOutcome(s1, channels, null, false, "", null)),
            emptyMap()
        )

        assertEquals(0, selection.failed)
        assertFalse(selection.refreshed.contains("s1"))
        assertEquals(channels, selection.channels)
    }

    @Test fun skippedAutoUpdateSourceKeepsChannelsWithoutFailure() {
        val s1 = source("s1").copy(autoUpdate = false)
        val channels = listOf(channel("s1", "kept"))
        val selection = selectRefreshOutcomes(
            listOf(RefreshOutcome(s1, channels, null, false, "", null, skipped = true)),
            mapOf("s1" to channels)
        )

        assertEquals(channels, selection.channels)
        assertEquals(0, selection.failed)
        assertTrue(selection.refreshed.isEmpty())
    }

    @Test fun disabledSourcesCannotTruncateRefreshedSources() {
        val disabled = source("off", enabled = false)
        val enabled = source("live")
        val selection = selectRefreshOutcomes(
            listOf(
                RefreshOutcome(disabled, (1..5).map { channel("off", "c$it") }, null, false, "", null),
                RefreshOutcome(enabled, (1..5).map { channel("live", "c$it") }, null, true, "", null),
            ),
            emptyMap(),
            channelCap = 5,
        )

        assertEquals(5, selection.channels.size)
        assertTrue(selection.channels.all { it.sourceId == "live" })
        assertTrue(selection.refreshed.contains("live"))
        assertTrue(selection.sources.first { it.id == "off" }.lastError.contains("仅保留前 0 个"))
    }

    @Test fun guideUsesFirstNonBlankSource() {
        val s1 = source("s1")
        val s2 = source("s2")
        val selection = selectRefreshOutcomes(
            listOf(
                RefreshOutcome(s1, emptyList(), null, true, "", null),
                RefreshOutcome(s2, emptyList(), null, true, "", "http://guide")
            ),
            emptyMap()
        )

        assertEquals("http://guide", selection.guide)
    }
}
