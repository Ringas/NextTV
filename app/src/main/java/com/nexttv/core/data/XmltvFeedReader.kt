// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import android.util.Xml
import com.nexttv.core.io.ImportBudget
import com.nexttv.core.io.ImportLimitException
import com.nexttv.core.io.ImportPolicy
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

internal data class XmltvFeed(
    val programmes: MutableMap<String, List<Program>>,
    val aliases: MutableMap<String, String>,
    val displayNames: MutableMap<String, String>
)

internal fun readXmltvFeed(
    input: InputStream,
    budget: ImportBudget,
    normalize: (String) -> String,
    now: Long = System.currentTimeMillis()
): XmltvFeed {
    val xml = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        setInput(input, null)
    }
    val byChannel = linkedMapOf<String, MutableList<Program>>()
    val aliases = linkedMapOf<String, String>()
    val names = linkedMapOf<String, String>()
    val clock = XmltvTimeParser()
    val horizon = 31 * 86_400_000L
    var eventCount = 0
    var chars = 0L
    var channels = 0
    var programmes = 0

    fun advance(): Int {
        budget.check()
        val event = xml.next()
        if (++eventCount > 1_000_000 || xml.depth > 32) {
            throw ImportLimitException("节目单结构超过上限")
        }
        if (event == XmlPullParser.TEXT) {
            val length = xml.text?.length ?: 0
            chars += length
            if (length > ImportPolicy.FIELD_CHARS || chars > ImportPolicy.TEXT_CHARS) {
                throw ImportLimitException("节目单文本超过上限")
            }
        }
        return event
    }

    fun field(): String {
        val depth = xml.depth
        val text = StringBuilder()
        while (true) {
            when (advance()) {
                XmlPullParser.TEXT -> {
                    text.append(xml.text.orEmpty())
                    if (text.length > ImportPolicy.FIELD_CHARS) throw ImportLimitException("节目单字段过长")
                }
                XmlPullParser.END_TAG -> if (xml.depth == depth) return text.toString().trim()
                XmlPullParser.END_DOCUMENT -> error("节目单字段未闭合")
            }
        }
    }

    fun record(consume: (String) -> Unit) {
        val depth = xml.depth
        while (true) {
            when (advance()) {
                XmlPullParser.START_TAG -> if (xml.depth == depth + 1) consume(xml.name)
                XmlPullParser.END_TAG -> if (xml.depth == depth) return
                XmlPullParser.END_DOCUMENT -> error("节目单记录未闭合")
            }
        }
    }

    fun id(attribute: String): String {
        val value = xml.getAttributeValue(null, attribute).orEmpty()
        if (value.length > 512) throw ImportLimitException("节目单频道标识过长")
        return value
    }

    while (advance() != XmlPullParser.END_DOCUMENT) {
        if (xml.eventType != XmlPullParser.START_TAG) continue
        when (xml.name) {
            "channel" -> {
                if (++channels > ImportPolicy.EPG_CHANNELS) throw ImportLimitException("节目单频道数量超过上限")
                val key = id("id")
                record { tag ->
                    if (tag == "display-name") {
                        val name = field()
                        if (key.isNotEmpty() && name.isNotEmpty()) {
                            val alias = normalize(name)
                            if (alias !in aliases && aliases.size == 20_000) throw ImportLimitException("节目频道别名数量超过上限")
                            aliases[alias] = key
                            names[key] = name
                        }
                    }
                }
            }
            "programme" -> {
                if (++programmes > ImportPolicy.PROGRAMMES) throw ImportLimitException("节目数量超过上限")
                val key = id("channel")
                val start = clock.parse(xml.getAttributeValue(null, "start")) ?: 0L
                val stop = clock.parse(xml.getAttributeValue(null, "stop"))
                    ?.takeIf { it > start } ?: (start + 1_800_000L)
                var title = ""
                var description = ""
                record { tag ->
                    when (tag) {
                        "title" -> field().takeIf(String::isNotBlank)?.let { title = it }
                        "desc" -> {
                            val value = field()
                            if (description.isEmpty()) description = value
                        }
                    }
                }
                if (key.isNotEmpty() && title.isNotEmpty() && start > 0 &&
                    stop > now - horizon && start < now + horizon
                ) {
                    byChannel.getOrPut(key, ::mutableListOf).add(Program(key, title, description, start, stop))
                }
            }
        }
    }
    val ordered = byChannel.mapValuesTo(linkedMapOf()) { (_, values) -> values.sortedBy(Program::start) }
    return XmltvFeed(ordered, aliases, names)
}
