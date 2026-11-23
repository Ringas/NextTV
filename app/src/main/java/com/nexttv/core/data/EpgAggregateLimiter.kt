// SPDX-License-Identifier: GPL-3.0-only
// Next TV — aggregate budget for concurrent XMLTV feeds.
package com.nexttv.core.data

import com.nexttv.core.io.ImportPolicy

/**
 * Atomically reserves programme/text budget per feed URL.
 *
 * Admission and accounting share one lock, so two feeds that are parsed concurrently
 * cannot both pass the aggregate check and then overrun the limit on insert.
 */
internal class EpgAggregateLimiter(
    private val maxProgrammes: Int = ImportPolicy.TOTAL_PROGRAMMES,
    private val maxText: Long = ImportPolicy.TEXT_CHARS * 2L,
) {
    private data class Usage(val programmes: Long, val text: Long)
    private val usage = HashMap<String, Usage>()

    /** Reserves [programmes]/[text] for [url], replacing its previous reservation. */
    @Synchronized
    fun admit(url: String, programmes: Int, text: Long): Boolean {
        var totalProgrammes = 0L
        var totalText = 0L
        usage.values.forEach {
            totalProgrammes += it.programmes
            totalText += it.text
        }
        val previous = usage[url]
        if (previous != null) {
            totalProgrammes -= previous.programmes
            totalText -= previous.text
        }
        totalProgrammes += programmes
        totalText += text
        if (totalProgrammes > maxProgrammes || totalText > maxText) return false
        usage[url] = Usage(programmes.toLong(), maxOf(0L, text))
        return true
    }

    @Synchronized
    fun remove(url: String) {
        usage.remove(url)
    }

    /** Drops accounting for feeds that are no longer active. */
    @Synchronized
    fun retain(urls: Set<String>) {
        usage.keys.retainAll(urls)
    }

    @Synchronized
    fun totalProgrammes(): Long = usage.values.sumOf { it.programmes }

    @Synchronized
    fun totalText(): Long = usage.values.sumOf { it.text }
}

/** Text weight used by the aggregate cap; mirrors the cache reader's accounting. */
internal fun epgTextWeight(
    programmes: Map<String, List<Program>>,
    aliases: Map<String, String>,
    displayNames: Map<String, String>,
): Long = programmes.values.sumOf { list ->
    list.sumOf { it.title.length.toLong() + it.desc.length }
} + aliases.entries.sumOf { it.key.length.toLong() + it.value.length } +
    displayNames.entries.sumOf { it.key.length.toLong() + it.value.length }
