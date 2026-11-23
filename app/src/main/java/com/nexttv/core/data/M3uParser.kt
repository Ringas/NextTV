// SPDX-License-Identifier: GPL-3.0-only
// Next TV — Compose for TV implementation.
package com.nexttv.core.data

import java.util.Locale
import com.nexttv.core.io.*

/**
 * m3u / m3u8 / txt 播放列表解析。
 * 支持 #EXTINF 属性（tvg-id / tvg-name / tvg-logo / group-title）以及 "频道名,地址" 的 txt 格式。
 */
object M3uParser {

    data class Result(val channels: List<Channel>, val epgUrl: String?)

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(raw: String, sourceId: String, baseUrl: String = "", checkBudget: () -> Unit = {}): Result =
        parse(raw.reader(), sourceId, baseUrl, checkBudget)

    internal fun parse(
        input: java.io.Reader,
        sourceId: String,
        baseUrl: String,
        checkBudget: () -> Unit
    ): Result {
        val channels = ArrayList<Channel>()
        val seen = HashSet<Pair<String, String>>()
        var guide: String? = null
        var pending: Pending? = null
        fun add(entry: Pending, url: String) {
            val name = entry.name.ifBlank { entry.tvgName }.trim().trimEnd(',').trim()
            if (name.isEmpty() || !isUrl(url) || !seen.add(name to url)) return
            if (channels.size == ImportPolicy.CHANNELS) throw ImportLimitException("播放源频道/线路数量超过上限")
            channels += Channel(
                stableChannelId(sourceId, name, entry.tvgId), name, entry.tvgId, entry.logo,
                normalizeGroup(entry.group, name), url, sourceId, catchup = entry.catchup,
                catchupSource = entry.catchupSource, catchupDays = entry.catchupDays, headers = entry.headers
            )
        }

        readPlaylistLines(input, checkBudget) { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", true) -> {
                    val attributes = readAttributes(line)
                    guide = sequenceOf("url-tvg", "x-tvg-url", "tvg-url").mapNotNull(attributes::get).firstOrNull()
                }
                line.startsWith("#EXTINF", true) -> {
                    val (attributes, title) = splitInfo(line)
                    val fields = readAttributes(attributes)
                    pending = Pending(title, fields["tvg-id"].orEmpty(), fields["tvg-name"].orEmpty(),
                        fields["tvg-logo"].orEmpty().ifBlank { fields["logo"].orEmpty() },
                        fields["group-title"].orEmpty(), fields["catchup"].orEmpty(),
                        fields["catchup-source"].orEmpty(), fields["catchup-days"]?.toIntOrNull())
                }
                line.startsWith("#EXTVLCOPT:", true) -> {
                    val option = line.substringAfter(':')
                    val header = when (option.substringBefore('=').lowercase(Locale.ROOT)) {
                        "http-user-agent" -> "User-Agent"
                        "http-referrer", "http-referer" -> "Referer"
                        else -> null
                    }
                    if (header != null) pending = pending?.let {
                        it.copy(headers = it.headers + (header to option.substringAfter('=', "")))
                    }
                }
                line.startsWith('#') -> Unit
                else -> {
                    val entry = pending
                    pending = null
                    val comma = line.indexOf(',')
                    when {
                        entry != null -> add(entry, resolve(baseUrl, line))
                        comma in 1 until line.lastIndex -> add(
                            Pending(line.substring(0, comma), "", "", "", ""),
                            line.substring(comma + 1).substringBefore('#').trim()
                        )
                        else -> add(Pending("频道 ${channels.size + 1}", "", "", "", ""), line)
                    }
                }
            }
        }
        return Result(channels, guide?.takeIf(String::isNotBlank))
    }

    private fun splitInfo(line: String): Pair<String, String> {
        var quoted = false
        for ((index, char) in line.withIndex()) {
            if (char == '"') quoted = !quoted
            if (char == ',' && !quoted) return line.substring(0, index) to line.substring(index + 1)
        }
        return line to ""
    }

    private fun readAttributes(text: String): Map<String, String> = buildMap {
        for (match in attrRegex.findAll(text)) {
            put(match.groupValues[1].lowercase(Locale.ROOT), match.groupValues[2].trim())
        }
    }

    /** 4K 频道统一归到「4K专区」，保证 4K 单独一组 */
    private fun normalizeGroup(group: String, name: String): String {
        val g = group.trim()
        val is4k = name.contains("4K", ignoreCase = true) || name.contains("UHD", ignoreCase = true)
        if (is4k && g != GROUP_4K) return GROUP_4K
        if (g.isBlank()) return Channel.DEFAULT_GROUP
        return g
    }

    private fun resolve(base:String,value:String):String = if(base.isBlank())value else runCatching{java.net.URI(base).resolve(value).toString()}.getOrDefault(value)
    private fun isUrl(v: String): Boolean {
        val s = v.trim()
        if (s.isEmpty() || s.contains(' ')) return false
        return s.startsWith("http://", true) ||
            s.startsWith("https://", true) ||
            s.startsWith("rtp://", true) ||
            s.startsWith("rtsp://", true) ||
            s.startsWith("udp://", true) ||
            s.startsWith("rtmp://", true) ||
            s.startsWith("rtmp", true) && s.contains("://") ||
            s.startsWith("/") ||
            s.startsWith("file://", true)
    }

    private val identitySpaces = Regex("\\s+")
    private val identityDigest = ThreadLocal<java.security.MessageDigest>()

    fun stableChannelId(sourceId:String,name:String,tvgId:String):String {
        val identity=sourceId+"|"+if(tvgId.isNotBlank())"id:"+tvgId.trim().lowercase(Locale.ROOT) else "name:"+name.trim().lowercase(Locale.ROOT).replace(identitySpaces,"")
        val digest = identityDigest.get() ?: java.security.MessageDigest.getInstance("SHA-256").also(identityDigest::set)
        val bytes = digest.digest(identity.toByteArray(Charsets.UTF_8))
        val hex="0123456789abcdef"
        return buildString(35){append("v2-");for(i in 0 until 16){val v=bytes[i].toInt() and 255;append(hex[v ushr 4]);append(hex[v and 15])}}
    }
    const val GROUP_4K = "4K专区"

    private data class Pending(
        val name: String,
        val tvgId: String,
        val tvgName: String,
        val logo: String,
        val group: String, val catchup:String="", val catchupSource:String="", val catchupDays:Int?=null, val headers:Map<String,String> = emptyMap()
    )
}
