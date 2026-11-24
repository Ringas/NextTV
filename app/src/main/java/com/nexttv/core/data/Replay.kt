package com.nexttv.core.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val replayTokens = listOf(
    "\${(b)yyyyMMddHHmmss}", "\${(e)yyyyMMddHHmmss}", "{utc}", "{utcend}", "{lutc}",
    "{duration}", "{start}", "{end}", "{Y}", "{m}", "{d}", "{H}", "{M}", "{S}"
)

private fun replaySource(route: ChannelRoute): String? {
    val type = route.catchup.lowercase()
    if (type !in setOf("append", "default", "shift")) return null
    val source = route.catchupSource.ifBlank { if (type == "default") "?utc={utc}&lutc={lutc}" else return null }
    if (!source.startsWith("?")) return null
    if (route.catchupDays?.let { it <= 0 } == true) return null
    val remainder = replayTokens.fold(source) { value, token -> value.replace(token, "") }
    return source.takeIf { '{' !in remainder && '}' !in remainder }
}

internal fun supportsReplay(route: ChannelRoute): Boolean = replaySource(route) != null

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
    var suffix=replaySource(channel) ?: return null
    val zone=ZoneId.of("Asia/Shanghai")
    val format=DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(zone)
    val date=Instant.ofEpochMilli(programme.start).atZone(zone)
    suffix=suffix.replace("\${(b)yyyyMMddHHmmss}",format.format(Instant.ofEpochMilli(programme.start)))
        .replace("\${(e)yyyyMMddHHmmss}",format.format(Instant.ofEpochMilli(programme.stop)))
        .replace("{utcend}",(programme.stop/1000).toString())
        .replace("{utc}",(programme.start/1000).toString())
        .replace("{lutc}",(now/1000).toString())
        .replace("{duration}",((programme.stop-programme.start)/1000).toString())
        .replace("{start}",(programme.start/1000).toString())
        .replace("{end}",(programme.stop/1000).toString())
        .replace("{Y}",date.year.toString())
        .replace("{m}",date.monthValue.toString().padStart(2,'0'))
        .replace("{d}",date.dayOfMonth.toString().padStart(2,'0'))
        .replace("{H}",date.hour.toString().padStart(2,'0'))
        .replace("{M}",date.minute.toString().padStart(2,'0'))
        .replace("{S}",date.second.toString().padStart(2,'0'))
    if(suffix.contains('{') || !suffix.startsWith("?"))return null
    val base=channel.url.substringBefore('#')
    return base+(if('?' in base)"&" else "?")+suffix.drop(1)
}
