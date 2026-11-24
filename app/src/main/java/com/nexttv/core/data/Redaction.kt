// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

private val urlPattern = Regex("""https?://[^\s"'<>]+""")
private val secretPattern = Regex("""(?i)\b(token|password|passwd|secret|api[_-]?key|authorization)=([^\r\n&]+)""")

/**
 * Crash reports are stored on disk and may be copied into an issue. Strip URL
 * credentials, query strings and common secret-looking values before writing.
 */
internal fun redactSensitive(text: String): String {
    val withoutUrls = urlPattern.replace(text) { match ->
        val raw = match.value
        val schemeEnd = raw.indexOf("://") + 3
        val authorityEnd = raw.indexOfAny(charArrayOf('/', '?', '#'), schemeEnd).let { if (it < 0) raw.length else it }
        val authority = raw.substring(schemeEnd, authorityEnd).substringAfterLast('@')
        val path = raw.substring(authorityEnd).substringBefore('?').substringBefore('#')
        val hasQuery = '?' in raw.substring(authorityEnd)
        raw.substring(0, schemeEnd) + authority + path + if (hasQuery) "?<redacted>" else ""
    }
    return secretPattern.replace(withoutUrls) { match -> "${match.groupValues[1]}=<redacted>" }
}
