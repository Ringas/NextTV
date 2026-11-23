// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import java.util.Locale

private val channelNameWhitespace = Regex("\\s+")
private fun Channel.normalizedTvgId() = tvgId.trim().lowercase(Locale.ROOT)
private fun Channel.normalizedChannelName() = name.trim().lowercase(Locale.ROOT).replace(channelNameWhitespace, "")

/** Keep the first channel's ID and display position, with each route's original source settings. */
fun mergeChannelRoutes(channels: List<Channel>, acrossSources: Boolean = false): List<Channel> {
    val sourceChannels = mergeChannelsBy(channels) { channel ->
        val id = channel.normalizedTvgId()
        channel.sourceId to if (id.isNotEmpty()) "id:$id" else "name:${channel.normalizedChannelName()}"
    }
    return if (acrossSources) mergeAcrossSources(sourceChannels) else sourceChannels
}

internal fun mergeAcrossSources(channels: List<Channel>): List<Channel> {
    val identifiedByName = channels.filter { it.normalizedTvgId().isNotEmpty() }
        .groupBy { it.normalizedChannelName() }
        .mapValues { (_, matches) ->
            matches.map { it.normalizedTvgId() }.toSet() to matches.map { it.sourceId }.toSet()
        }
    return mergeChannelsBy(channels) { channel ->
        val id = channel.normalizedTvgId()
        val name = channel.normalizedChannelName()
        val (ids, sources) = identifiedByName[name] ?: (emptySet<String>() to emptySet())
        when {
            id.isNotEmpty() -> "" to "id:$id"
            name.isEmpty() || ids.size > 1 -> channel.sourceId to "name:$name"
            ids.size == 1 && (sources.size > 1 || channel.sourceId !in sources) -> "" to "id:${ids.single()}"
            ids.size == 1 -> channel.sourceId to "name:$name"
            else -> "" to "name:$name"
        }
    }
}

private fun mergeChannelsBy(
    channels: List<Channel>,
    key: (Channel) -> Pair<String, String>,
): List<Channel> = channels.groupBy(key).values.map { group ->
    val first = group.first()
    first.copy(
        logo = group.firstNotNullOfOrNull { it.logo.takeIf(String::isNotBlank) }.orEmpty(),
        routes = group.flatMap(Channel::playbackRoutes).distinctBy { it.url to it.sourceId },
    )
}

/** One recovery pass per channel: each alternate once; a sole route gets one retry. */
class RouteRecovery {
 private val attempted=mutableSetOf<Int>()
 private var retriedSingle=false
 fun reset(index:Int){attempted.clear();attempted+=index;retriedSingle=false}
 fun next(count:Int,current:Int):Int? {
  if(count==1){if(retriedSingle)return null;retriedSingle=true;return 0}
  for(step in 1 until count){val i=(current+step)%count;if(attempted.add(i))return i}
  return null
 }
}
