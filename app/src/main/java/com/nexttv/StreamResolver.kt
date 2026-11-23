package com.nexttv

import kotlinx.coroutines.*
import java.net.HttpURLConnection
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
/** Cancelled navigation disconnects the obsolete probe on IO, never on the UI thread. */
suspend fun resolveStream(url: String,headers:Map<String,String> = emptyMap()): ResolvedStream {
    val path=URL(url).path.lowercase()
    if(path.endsWith(".m3u8"))return ResolvedStream(url,HLS_MIME)
    if(listOf(".mp4",".ts",".mkv",".mp3",".aac").any{path.endsWith(it)})return ResolvedStream(url,null)
    return supervisorScope {
        val connection=withContext(Dispatchers.IO){URL(url).openConnection() as HttpURLConnection}
        headers.forEach {(key,value)->connection.setRequestProperty(key,value)}
        connection.setRequestProperty("Connection", "close")
        connection.connectTimeout=8000;connection.readTimeout=8000;connection.instanceFollowRedirects=true
        val request=async(Dispatchers.IO) {
          try {
            val status = connection.responseCode
            if (status !in 200..299) throw StreamHttpStatusException(status)
            val target=connection.url.toString()
            // Confirm the payload on gateways that may return misleading MIME headers or redirect bodies.
            val prefix=connection.inputStream.use {input->
                val buffer=ByteArray(512);val count=input.read(buffer);if(count>0)String(buffer,0,count,Charsets.UTF_8)else ""
            }
            ResolvedStream(target,streamMime(target,connection.contentType.orEmpty(),prefix))
          }catch(e:Exception){ensureActive();throw e}
        }
        try {
            val resolved=request.await()
            ensureActive()
            resolved
        } finally {withContext(NonCancellable+Dispatchers.IO){connection.disconnect()}}
    }
}
