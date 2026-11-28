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

    data class Result(val channels: List<Channel>, val epgUrl: String?, val skippedUnsupported: Int = 0)

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
        var skippedUnsupported = 0
        var pending: Pending? = null
        fun add(entry: Pending, rawUrl: String) {
            val (url, pipeHeaders) = splitPipe(rawUrl)
            val name = entry.name.ifBlank { entry.tvgName }.trim().trimEnd(',').trim()
            if (name.isEmpty() || !isUrl(url) || !seen.add(name to url)) return
            // 播放引擎仅支持 HTTP(S)：其余协议在导入期就跳过，避免"能导入、点了必失败"。
            if (!isPlayableUrl(url)) { skippedUnsupported++; return }
            if (channels.size == ImportPolicy.CHANNELS) throw ImportLimitException("播放源频道/线路数量超过上限")
            channels += Channel(
                stableChannelId(sourceId, name, entry.tvgId), name, entry.tvgId, entry.logo,
                normalizeGroup(entry.group, name), url, sourceId, number = entry.number ?: 0,
                catchup = entry.catchup, catchupSource = entry.catchupSource,
                catchupDays = entry.catchupDays, headers = capHeaders(entry.headers + pipeHeaders)
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
                        fields["catchup-source"].orEmpty(), fields["catchup-days"]?.toIntOrNull(),
                        (fields["tvg-chno"] ?: fields["channel-number"])?.toIntOrNull()?.takeIf { it > 0 })
                }
                line.startsWith("#EXTGRP:", true) -> {
                    val group = line.substringAfter(':').trim()
                    if (group.isNotEmpty()) pending = pending?.copy(group = group)
                }
                line.startsWith("#EXTHTTP:", true) -> {
                    val headers = parseExtHttp(line.substringAfter(':'))
                    if (headers.isNotEmpty()) pending = pending?.let { it.copy(headers = it.headers + headers) }
                }
                line.startsWith("#KODIPROP:", true) -> {
                    pending = applyKodiProp(line.substringAfter(':'), pending)
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
        return Result(channels, guide?.takeIf(String::isNotBlank), skippedUnsupported)
    }

    private fun splitInfo(line: String): Pair<String, String> {
        var quote = '\u0000'
        for ((index, char) in line.withIndex()) {
            if (quote == '\u0000' && (char == '"' || char == '\'')) quote = char
            else if (char == quote) quote = '\u0000'
            if (char == ',' && quote == '\u0000') return line.substring(0, index) to line.substring(index + 1)
        }
        return line to ""
    }

    /** Accepts tvg-id="x", tvg-id='x' and the common tvg-id=x form. */
    private fun readAttributes(text: String): Map<String, String> = buildMap {
        var index = 0
        while (index < text.length) {
            while (index < text.length && (text[index].isWhitespace() || text[index] == ',')) index++
            val keyStart = index
            while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_' || text[index] == '-')) index++
            if (index == keyStart) { index++; continue }
            val key = text.substring(keyStart, index).lowercase(Locale.ROOT)
            while (index < text.length && text[index].isWhitespace()) index++
            if (index >= text.length || text[index] != '=') continue
            index++
            while (index < text.length && text[index].isWhitespace()) index++
            val value = if (index < text.length && (text[index] == '"' || text[index] == '\'')) {
                val quote = text[index++]
                val start = index
                while (index < text.length && text[index] != quote) index++
                val parsed = text.substring(start, index)
                if (index < text.length) index++
                parsed
            } else {
                val start = index
                while (index < text.length && !text[index].isWhitespace() && text[index] != ',') index++
                text.substring(start, index)
            }
            put(key, value.trim())
        }
    }

    /** Kodi/Xtream append request headers with a pipe after the stream URL. */
    private fun splitPipe(raw: String): Pair<String, Map<String, String>> {
        val separator = raw.indexOf('|')
        if (separator < 0 || separator == raw.lastIndex) return raw to emptyMap()
        val suffix = raw.substring(separator + 1)
        if ('=' !in suffix) return raw to emptyMap()
        val headers = parseHeaderPairs(suffix)
        return if (headers.isEmpty()) raw to emptyMap() else raw.substring(0, separator) to headers
    }

    private fun capHeaders(headers: Map<String, String>): Map<String, String> =
        if (headers.size <= MAX_HEADERS) headers
        else headers.entries.take(MAX_HEADERS).associate { it.key to it.value }

    private fun parseHeaderPairs(text: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        // Pipe headers are &-separated. A semicolon is legal inside Cookie /
        // Referer values, so it must never split the pair.
        text.split('&').forEach { pair ->
            if (out.size >= MAX_HEADERS) return@forEach
            val key = pair.substringBefore('=', "").trim()
            val value = pair.substringAfter('=', "").trim()
            if (key.isNotEmpty() && value.isNotEmpty() && key.length <= 64 && value.length <= 4096) out[key] = value
        }
        return out
    }

    private fun parseExtHttp(text: String): Map<String, String> = runCatching {
        val json = org.json.JSONObject(text.trim())
        val out = linkedMapOf<String, String>()
        json.keys().forEach { key ->
            if (out.size >= MAX_HEADERS) return@forEach
            val value = json.optString(key, "")
            if (key.length <= 64 && value.isNotBlank() && value.length <= 4096) out[key] = value
        }
        out
    }.getOrDefault(emptyMap())

    private fun applyKodiProp(option: String, pending: Pending?): Pending? {
        if (pending == null) return null
        val key = option.substringBefore('=').trim().lowercase(Locale.ROOT)
        val value = option.substringAfter('=', "").trim()
        if (value.isEmpty()) return pending
        return when (key) {
            "http-user-agent" -> pending.copy(headers = pending.headers + ("User-Agent" to value))
            "http-referrer", "http-referer" -> pending.copy(headers = pending.headers + ("Referer" to value))
            "inputstream.adaptive.stream_headers", "inputstream.adaptive.manifest_headers" -> {
                val headers = parseHeaderPairs(value)
                if (headers.isEmpty()) pending else pending.copy(headers = pending.headers + headers)
            }
            else -> pending
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

    /** 当前播放引擎（Media3 + DefaultHttpDataSource）实际支持的网络协议。 */
    fun isPlayableUrl(url:String):Boolean=url.trim().startsWith("http://",true)||url.trim().startsWith("https://",true)

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
    const val MAX_HEADERS = 100

    private data class Pending(
        val name: String,
        val tvgId: String,
        val tvgName: String,
        val logo: String,
        val group: String, val catchup:String="", val catchupSource:String="", val catchupDays:Int?=null,
        val number:Int?=null, val headers:Map<String,String> = emptyMap()
    )
}
