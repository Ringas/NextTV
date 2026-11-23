package com.nexttv.core.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val replayTokens = listOf(
    "\${(b)yyyyMMddHHmmss}", "\${(e)yyyyMMddHHmmss}", "{utc}", "{utcend}", "{duration}"
)

internal fun supportsReplay(route: ChannelRoute): Boolean {
    if (!route.catchup.equals("append", ignoreCase = true) || !route.catchupSource.startsWith("?")) return false
    if (route.catchupDays?.let { it <= 0 } == true) return false
    val remainder = replayTokens.fold(route.catchupSource) { value, token -> value.replace(token, "") }
    return '{' !in remainder && '}' !in remainder
}

internal fun preferredReplayRoute(channel: Channel, programme: Program, now: Long): Int {
    val routes = channel.playbackRoutes
    val eligible = routes.indices.filter { replayAddress(routes[it], programme, now) != null }
    return eligible.firstOrNull { routes[it].sourceId in programme.guideSourceIds }
        ?: eligible.firstOrNull() ?: -1
}

/** The supplied gateway's calendar placeholders use China Standard Time. */
fun replayAddress(channel:ChannelRoute, programme:Program, now:Long):String? {
    if(!supportsReplay(channel) || programme.stop>now || programme.stop<=programme.start)return null
    if(channel.catchupDays!=null && (channel.catchupDays<=0 || now-programme.start>channel.catchupDays.toLong()*86400000))return null
    var suffix=channel.catchupSource
    val format=DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Shanghai"))
    suffix=suffix.replace("\${(b)yyyyMMddHHmmss}",format.format(Instant.ofEpochMilli(programme.start)))
        .replace("\${(e)yyyyMMddHHmmss}",format.format(Instant.ofEpochMilli(programme.stop)))
        .replace("{utc}",(programme.start/1000).toString())
        .replace("{utcend}",(programme.stop/1000).toString())
        .replace("{duration}",((programme.stop-programme.start)/1000).toString())
    if(suffix.contains('{') || !suffix.startsWith("?"))return null
    val base=channel.url.substringBefore('#')
    return base+(if('?' in base)"&" else "?")+suffix.drop(1)
}
