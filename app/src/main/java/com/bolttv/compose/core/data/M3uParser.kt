package com.bolttv.compose.core.data

import com.bolttv.compose.core.model.Channel
import java.security.MessageDigest

/**
 * M3U / TXT 解析器
 * 频道 id 使用 URL 的稳定哈希，便于收藏与上次播放跨重启恢复。
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
                    currentLogo = extractAttr(trimmed, "tvg-logo")
                    currentGroup = extractAttr(trimmed, "group-title") ?: "未分类"
                    currentTvgId = extractAttr(trimmed, "tvg-id")
                    currentNumber = extractAttr(trimmed, "tvg-chno")?.toIntOrNull()
                        ?: extractAttr(trimmed, "channel-number")?.toIntOrNull()
                    val commaIndex = trimmed.lastIndexOf(',')
                    currentName = if (commaIndex >= 0 && commaIndex < trimmed.length - 1) {
                        trimmed.substring(commaIndex + 1).trim()
                    } else {
                        "未知频道"
                    }
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                    val name = currentName ?: "未知频道"
                    channels.add(
                        Channel(
                            id = stableId(trimmed),
                            name = name,
                            url = trimmed,
                            logo = currentLogo,
                            group = currentGroup,
                            tvgId = currentTvgId,
                            number = currentNumber
                        )
                    )
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

    fun stableId(url: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(url.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun extractAttr(extinf: String, key: String): String? {
        val regex = """$key="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(extinf)?.groupValues?.getOrNull(1)
    }
}
