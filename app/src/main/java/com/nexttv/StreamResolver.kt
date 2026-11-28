package com.nexttv

import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Transport capability of the current HTTP media source; parsing a URI is not playback support. */
fun supportsHttpPlayback(url:String):Boolean=url.startsWith("http://",true)||url.startsWith("https://",true)

const val HLS_MIME = "application/x-mpegURL"
data class ResolvedStream(val url: String, val mime: String?)
internal class StreamHttpStatusException(val status: Int) : IllegalArgumentException("HTTP $status")
fun streamMime(url: String, contentType: String = "", prefix: String = ""): String? {
    val path = url.substringBefore('?').lowercase()
    return if(path.endsWith(".m3u8") || contentType.substringBefore(';').trim().lowercase() in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl") || prefix.trimStart('\uFEFF',' ','\n','\r').startsWith("#EXTM3U")) HLS_MIME else null
}

private fun validateStreamUrl(url: String, httpsOnly: Boolean) {
    require(supportsHttpPlayback(url)) { "不支持的播放协议" }
    if (httpsOnly && !url.startsWith("https://", true)) throw IllegalArgumentException("仅允许 HTTPS")
}

private fun originOf(url: URL): String {
    val port = if (url.port > 0) url.port else if (url.protocol.equals("https", true)) 443 else 80
    return "${url.protocol.lowercase()}://${url.host.lowercase()}:$port"
}

internal fun sameHttpOrigin(first: String, second: String): Boolean = runCatching {
    originOf(URL(first)) == originOf(URL(second))
}.getOrDefault(false)

/**
 * Resolves a stream without letting HttpURLConnection copy custom credentials
 * across a cross-host redirect. Redirects are followed manually, at most five
 * hops; Authorization/Referer/API-key style headers are only re-sent when the
 * origin (scheme + host + port) is unchanged.
 *
 * Cancelled navigation disconnects the obsolete probe on IO, never on the UI thread.
 */
suspend fun resolveStream(url: String, headers: Map<String, String> = emptyMap(), httpsOnly: Boolean = false, probe: Boolean = false): ResolvedStream {
    validateStreamUrl(url, httpsOnly)
    val path = URL(url).path.lowercase()
    // A same-origin URL needs no discovery request; the media source can open it directly.
    if (!probe && path.endsWith(".m3u8")) return ResolvedStream(url, HLS_MIME)
    if (!probe && listOf(".mp4", ".ts", ".mkv", ".mp3", ".aac").any { path.endsWith(it) }) return ResolvedStream(url, null)

    return withContext(Dispatchers.IO) {
        var current = url
        var requestHeaders = headers.toMap()
        for (hop in 0..5) {
            ensureActive()
            validateStreamUrl(current, httpsOnly)
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                requestHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                connection.setRequestProperty("Connection", "close")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                // Follow redirects ourselves so a new host never inherits credentials.
                connection.instanceFollowRedirects = false
                val status = connection.responseCode
                if (status in 200..299) {
                    val contentType = connection.contentType.orEmpty()
                    // Confirm the payload on gateways that may return misleading MIME headers or redirect bodies.
                    val prefix = connection.inputStream.use { input ->
                        val buffer = ByteArray(512)
                        val count = input.read(buffer)
                        if (count > 0) String(buffer, 0, count, Charsets.UTF_8) else ""
                    }
                    return@withContext ResolvedStream(current, streamMime(current, contentType, prefix))
                }
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) throw StreamHttpStatusException(status)
                    val next = URI(current).resolve(location.trim()).toURL()
                    ensureActive()
                    validateStreamUrl(next.toString(), httpsOnly)
                    if (originOf(URL(current)) != originOf(next)) requestHeaders = emptyMap()
                    current = next.toString()
                    continue
                }
                throw StreamHttpStatusException(status)
            } finally {
                connection.disconnect()
            }
        }
        throw StreamHttpStatusException(310)
    }
}
