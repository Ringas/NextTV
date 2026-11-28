// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import com.nexttv.core.data.Channel
import com.nexttv.core.data.ChannelPresentationPolicy
import com.nexttv.core.data.ChannelRepository
import com.nexttv.core.data.ChannelRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPresentationPolicyTest {
    private fun channel(id: String, number: Int, routes: Int, group: String = "央视"): Channel {
        val list = (0 until routes).map { index ->
            ChannelRoute(
                url = "http://test/$id/$index",
                sourceId = "s1",
                originalId = id,
            )
        }
        return Channel(
            id = id,
            name = id,
            tvgId = id,
            logo = "",
            group = group,
            url = list.first().url,
            sourceId = "s1",
            number = number,
            routes = list,
        )
    }

    @Test fun hiddenMultiRouteMovesToHiddenGroupAndNormalNumbersCompact() {
        val a = channel("a", 10, 2)
        val b = channel("b", 37, 2)
        val deadMulti = channel("deadMulti", 60, 2)
        val deadSingle = channel("deadSingle", 90, 1)
        val result = ChannelPresentationPolicy.build(
            channels = listOf(a, b, deadMulti, deadSingle),
            hideUnavailable = true,
            hasDisabledSource = false,
            isAvailable = { it.id == "a" || it.id == "b" },
        )

        assertEquals(listOf("a", "b"), result.normal.map { it.id })
        assertEquals(listOf(1, 2), result.normal.map { it.number })
        assertEquals(listOf("deadMulti"), result.hidden.map { it.id })
        assertEquals(ChannelRepository.GROUP_HIDDEN, result.hidden.single().group)
        assertEquals(0, result.hidden.single().number)
        assertTrue(result.renumbered)
        assertFalse(result.normal.any { it.id == "deadSingle" })
    }

    @Test fun disabledSourceCompactsNumbersEvenWhenNothingIsHidden() {
        val result = ChannelPresentationPolicy.build(
            channels = listOf(channel("a", 3, 2), channel("b", 11, 2)),
            hideUnavailable = false,
            hasDisabledSource = true,
            isAvailable = { true },
        )

        assertEquals(listOf(1, 2), result.normal.map { it.number })
        assertTrue(result.hidden.isEmpty())
        assertTrue(result.renumbered)
    }

    @Test fun nothingHiddenAndNoDisabledSourceKeepsSourceNumbers() {
        val result = ChannelPresentationPolicy.build(
            channels = listOf(channel("a", 3, 2), channel("b", 11, 2)),
            hideUnavailable = true,
            hasDisabledSource = false,
            isAvailable = { true },
        )

        assertEquals(listOf(3, 11), result.normal.map { it.number })
        assertTrue(result.hidden.isEmpty())
        assertFalse(result.renumbered)
    }
}
