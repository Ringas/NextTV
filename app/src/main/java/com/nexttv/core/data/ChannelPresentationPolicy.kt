// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

/**
 * Presentation rules for the home/browse channel list.
 *
 * Normal channels stay in their original groups. Channels whose routes are all
 * marked unavailable become a separate [GROUP_HIDDEN] group when the hide switch
 * is on. Single-route channels are deliberately not added to that group: without
 * a backup route there is nothing useful to open there.
 *
 * A disabled subscription leaves permanent gaps in the numbering space, and a
 * hidden group removes cards from the normal sequence. Whenever either happens
 * the remaining normal channels are renumbered from 1 so numeric shortcuts can
 * never target a missing/hidden channel.
 */
internal object ChannelPresentationPolicy {
    const val GROUP_HIDDEN = "隐藏"

    data class Presentation(
        val normal: List<Channel>,
        val hidden: List<Channel>,
        val renumbered: Boolean,
    )

    fun build(
        channels: List<Channel>,
        hideUnavailable: Boolean,
        hasDisabledSource: Boolean,
        isAvailable: (Channel) -> Boolean,
    ): Presentation {
        val allHidden = if (hideUnavailable) channels.filterNot(isAvailable) else emptyList()
        val normal = if (hideUnavailable) channels.filter(isAvailable) else channels
        val hidden = allHidden
            .filter { it.playbackRoutes.size > 1 }
            .map { it.copy(group = GROUP_HIDDEN, number = 0) }
        val renumber = hasDisabledSource || allHidden.isNotEmpty()
        val numbered = if (renumber) normal.mapIndexed { index, channel -> channel.copy(number = index + 1) } else normal
        return Presentation(numbered, hidden, renumber)
    }
}
