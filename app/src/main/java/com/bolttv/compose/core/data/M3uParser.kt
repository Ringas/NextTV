package com.bolttv.compose.core.data

import com.bolttv.compose.core.model.Channel
import java.util.UUID

/**
 * 简易 M3U / TXT 解析器
 * 参考 Bolt-IPTV 的 M3uParser，支持常见 #EXTINF 属性。
 */
object M3uParser {

    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()

        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup: String = "未分类"
        var currentTvgId: String? = null
        var currentNumber: Int? = null

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF:", ignoreCase = true) -> {
                    // 解析属性
                    currentLogo = extractAttr(trimmed, "tvg-logo")
                    currentGroup = extractAttr(trimmed, "group-title") ?: "未分类"
                    currentTvgId = extractAttr(trimmed, "tvg-id")
                    currentNumber = extractAttr(trimmed, "tvg-chno")?.toIntOrNull()
                        ?: extractAttr(trimmed, "channel-number")?.toIntOrNull()

                    // 名称在最后一个逗号后
                    val commaIndex = trimmed.lastIndexOf(',')
                    currentName = if (commaIndex >= 0 && commaIndex < trimmed.length - 1) {
                        trimmed.substring(commaIndex + 1).trim()
                    } else {
                        "未知频道"
                    }
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                    // 这是 URL
                    val name = currentName ?: "未知频道"
                    channels.add(
                        Channel(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            url = trimmed,
                            logo = currentLogo,
                            group = currentGroup,
                            tvgId = currentTvgId,
                            number = currentNumber
                        )
                    )
                    // 重置
                    currentName = null
                    currentLogo = null
                    currentGroup = "未分类"
                    currentTvgId = null
                    currentNumber = null
                }
            }
        }
        return channels
    }

    private fun extractAttr(extinf: String, key: String): String? {
        val regex = """$key="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(extinf)?.groupValues?.getOrNull(1)
    }
}
