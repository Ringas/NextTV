package com.nexttv.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelProgrammesTest {
    private val primary = Channel("a", "News", "news", "", "", "https://example.test/a", "primary")
    private val alternate = primary.copy(
        id = "b", name = "News Live", sourceId = "alternate", url = "https://example.test/b",
    )
    private val guides = mapOf("primary" to "a.xml", "alternate" to "b.xml")
    private val channel = mergeChannelRoutes(listOf(primary, alternate), acrossSources = true).single()

    @Test
    fun combinesGapsAndKeepsHigherPriorityTimes() {
        val manual = programme("Noon", 20, 30)
        val feeds = mapOf(
            "custom.xml" to feed(listOf(manual)),
            "a.xml" to feed(listOf(programme("Morning", 10, 20), programme("Conflicts with manual", 20, 25))),
            "b.xml" to feed(listOf(programme("Overlaps morning", 15, 25), programme("Afternoon", 30, 40))),
        )
        val result = resolveChannelProgrammes(channel, "custom.xml", guides, feeds::get, String::lowercase)
        assertEquals(listOf("Morning", "Noon", "Afternoon"), result.map { it.title })
        assertEquals(listOf(listOf("primary"), emptyList(), listOf("alternate")), result.map { it.guideSourceIds })
    }

    @Test
    fun duplicateSchedulesFillMissingDescriptionAndRetainReplayPreference() {
        val first = programme("News", 10, 20)
        val result = resolveChannelProgrammes(channel, "", guides, {
            when (it) {
                "a.xml" -> feed(listOf(first))
                "b.xml" -> feed(listOf(first.copy(title = " NEWS ", desc = "Programme description")))
                else -> null
            }
        }, String::lowercase).single()
        assertEquals(first.title, result.title)
        assertEquals("Programme description", result.desc)
        assertEquals(listOf("primary", "alternate"), result.guideSourceIds)

        val replayChannel = channel.copy(routes = channel.playbackRoutes.map {
            it.copy(catchup = "append", catchupSource = "?utc={utc}")
        })
        assertEquals(1, preferredReplayRoute(replayChannel, result.copy(guideSourceIds = listOf("alternate")), 50))
        assertEquals(0, preferredReplayRoute(replayChannel, result.copy(guideSourceIds = listOf("disabled")), 50))
    }

    @Test
    fun alternateFeedUsesItsOwnChannelNameAndUnmergedChannelsUseOneGuide() {
        val fromAlias = programme("Alternate guide", 30, 40).copy(channelKey = "feed-local-id")
        val feeds = mapOf(
            "custom.xml" to feed(listOf(programme("Custom", 10, 20))),
            "a.xml" to feed(listOf(programme("Primary", 20, 30))),
            "b.xml" to XmltvFeed(
                mutableMapOf("feed-local-id" to listOf(fromAlias)),
                mutableMapOf("news live" to "feed-local-id"),
                mutableMapOf(),
            ),
        )
        val result = resolveChannelProgrammes(channel, "custom.xml", guides, feeds::get, String::lowercase)
        assertEquals(listOf("Custom", "Primary", "Alternate guide"), result.map { it.title })
        assertEquals(listOf("Custom"), resolveChannelProgrammes(primary, "custom.xml", guides, feeds::get, String::lowercase).map { it.title })
        assertEquals(listOf("Custom", "Primary"), resolveChannelProgrammes(
            channel, "custom.xml", guides - "alternate", feeds::get, String::lowercase,
        ).map { it.title })
    }

    private fun programme(title: String, start: Long, stop: Long) = Program("news", title, "", start, stop)

    private fun feed(programmes: List<Program>) =
        XmltvFeed(mutableMapOf("news" to programmes), mutableMapOf(), mutableMapOf())
}
