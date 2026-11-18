package com.bolttv.compose.core.data

import android.util.Log
import android.util.Xml
import com.bolttv.compose.core.model.EpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * XMLTV EPG 仓库
 *
 * 支持标准 XMLTV 格式（programme + channel）。
 * 按 channelId / tvg-id 索引，提供「当前节目」「接下来节目」查询。
 */
class EpgRepository {

    private val TAG = "EpgRepository"

    /** channelId(or tvg-id) -> 按开始时间排序的节目列表 */
    private val programsByChannel = mutableMapOf<String, MutableList<EpgProgram>>()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _programCount = MutableStateFlow(0)
    val programCount: StateFlow<Int> = _programCount.asStateFlow()

    /**
     * 从 URL 加载 XMLTV
     */
    suspend fun loadFromUrl(url: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "BoltComposeTV/0.2")
            connection.inputStream.use { stream ->
                parseXmlTv(stream)
            }
            val total = programsByChannel.values.sumOf { it.size }
            _programCount.value = total
            _loaded.value = true
            Log.d(TAG, "EPG loaded: $total programmes, ${programsByChannel.size} channels")
            Result.success(total)
        } catch (e: Exception) {
            Log.e(TAG, "EPG load failed", e)
            Result.failure(e)
        }
    }

    /**
     * 获取某频道当前正在播出的节目
     */
    fun getCurrentProgram(channelId: String?, tvgId: String?, now: Long = System.currentTimeMillis()): EpgProgram? {
        val list = resolveList(channelId, tvgId) ?: return null
        return list.firstOrNull { now >= it.start && now < it.end }
    }

    /**
     * 获取接下来 N 个节目（含当前）
     */
    fun getUpcoming(channelId: String?, tvgId: String?, limit: Int = 5, now: Long = System.currentTimeMillis()): List<EpgProgram> {
        val list = resolveList(channelId, tvgId) ?: return emptyList()
        return list.filter { it.end > now }.take(limit)
    }

    fun clear() {
        programsByChannel.clear()
        _loaded.value = false
        _programCount.value = 0
    }

    private fun resolveList(channelId: String?, tvgId: String?): List<EpgProgram>? {
        if (!tvgId.isNullOrBlank()) {
            programsByChannel[tvgId]?.let { return it }
        }
        if (!channelId.isNullOrBlank()) {
            programsByChannel[channelId]?.let { return it }
        }
        return null
    }

    private fun parseXmlTv(input: java.io.InputStream) {
        programsByChannel.clear()
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")

        var event = parser.eventType
        var currentChannelId: String? = null
        var currentStart: Long = 0
        var currentEnd: Long = 0
        var currentTitle: String? = null
        var currentDesc: String? = null
        var inProgramme = false
        var inTitle = false
        var inDesc = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            inProgramme = true
                            currentChannelId = parser.getAttributeValue(null, "channel")
                            currentStart = parseXmlTvTime(parser.getAttributeValue(null, "start"))
                            currentEnd = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
                            currentTitle = null
                            currentDesc = null
                        }
                        "title" -> if (inProgramme) inTitle = true
                        "desc" -> if (inProgramme) inDesc = true
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        if (inTitle) currentTitle = text
                        if (inDesc) currentDesc = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "title" -> inTitle = false
                        "desc" -> inDesc = false
                        "programme" -> {
                            if (inProgramme && currentChannelId != null && currentTitle != null && currentStart > 0) {
                                val prog = EpgProgram(
                                    channelId = currentChannelId!!,
                                    title = currentTitle!!,
                                    start = currentStart,
                                    end = if (currentEnd > 0) currentEnd else currentStart + 3600_000,
                                    description = currentDesc
                                )
                                programsByChannel.getOrPut(currentChannelId!!) { mutableListOf() }.add(prog)
                            }
                            inProgramme = false
                        }
                    }
                }
            }
            event = parser.next()
        }

        // 按开始时间排序
        programsByChannel.values.forEach { it.sortBy { p -> p.start } }
    }

    /**
     * XMLTV 时间格式：YYYYMMDDhhmmss + optional timezone
     * 例：20261118153000 +0000
     */
    private fun parseXmlTvTime(raw: String?): Long {
        if (raw.isNullOrBlank() || raw.length < 14) return 0L
        return try {
            val core = raw.substring(0, 14)
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            // 若有时区偏移则使用，否则按 UTC
            if (raw.length >= 18) {
                val tzPart = raw.substring(15).replace(" ", "")
                try {
                    sdf.timeZone = TimeZone.getTimeZone("GMT$tzPart")
                } catch (_: Exception) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
            } else {
                sdf.timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(core)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
