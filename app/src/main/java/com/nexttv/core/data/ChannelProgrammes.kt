// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import java.util.TreeMap

internal fun resolveChannelProgrammes(
    channel: Channel,
    customGuide: String,
    guidesBySource: Map<String, String>,
    findFeed: (String) -> XmltvFeed?,
    normalizeName: (String) -> String,
): List<Program> {
    val routes = channel.playbackRoutes
    val guides = routes.groupBy { guidesBySource[it.sourceId].orEmpty() }.filterKeys { it.isNotBlank() }
    val urls = (listOf(customGuide) + guides.keys).filter { it.isNotBlank() }.distinct()
    val mergedChannel = routes.any { it.sourceId != channel.sourceId }
    val candidates = urls.asSequence().mapNotNull { url ->
        val feed = findFeed(url) ?: return@mapNotNull null
        val localRoutes = guides[url].orEmpty()
        val matchingRoutes = if (url == customGuide) routes else localRoutes
        val ids = matchingRoutes.map { it.tvgId } +
            listOf(channel.tvgId).takeIf { url == customGuide || channel.sourceId in localRoutes.map { it.sourceId } }.orEmpty()
        val names = matchingRoutes.map { it.channelName.ifBlank { channel.name } }
        val aliases = names.flatMap { name ->
            val normalized = normalizeName(name)
            listOf(normalized, normalized.replace("综合", "").replace("频道", ""))
        }.mapNotNull(feed.aliases::get)
        val programmes = (ids + aliases).asSequence().filter { it.isNotBlank() }.distinct()
            .mapNotNull { feed.programmes[it]?.takeIf(List<Program>::isNotEmpty) }
            .firstOrNull() ?: return@mapNotNull null
        if (!mergedChannel) programmes
        else {
            val sourceIds = localRoutes.map { it.sourceId }.distinct()
            programmes.map { it.copy(guideSourceIds = sourceIds) }
        }
    }
    return if (mergedChannel) mergeProgrammeSchedules(candidates) else candidates.firstOrNull().orEmpty()
}

private fun mergeProgrammeSchedules(schedules: Sequence<List<Program>>): List<Program> {
    val timeline = TreeMap<Long, Program>()
    for (schedule in schedules) {
        for (programme in schedule) {
            if (programme.stop <= programme.start) continue
            val existing = timeline[programme.start]
            if (existing != null && existing.stop == programme.stop &&
                existing.title.trim().equals(programme.title.trim(), ignoreCase = true)
            ) {
                timeline[programme.start] = existing.copy(
                    desc = existing.desc.ifBlank { programme.desc },
                    guideSourceIds = (existing.guideSourceIds + programme.guideSourceIds).distinct(),
                )
                continue
            }
            val previous = timeline.floorEntry(programme.start)?.value
            val next = timeline.ceilingEntry(programme.start)?.value
            if ((previous == null || previous.stop <= programme.start) &&
                (next == null || programme.stop <= next.start)
            ) {
                timeline[programme.start] = programme
            }
        }
    }
    return timeline.values.toList()
}
