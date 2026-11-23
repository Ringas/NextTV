// SPDX-License-Identifier: GPL-3.0-only
// Next TV — HTTP defaults and request headers for subscription transport.
package com.nexttv.core.data

import android.content.Context
import android.net.Uri
import com.nexttv.core.io.*
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class SubscriptionTransport(private val context: Context, private val prefs: Prefs) {
    fun stage(src: Source, destination: File, budget: ImportBudget) {
        fun write(input: java.io.InputStream) {
            budget.input(decodedInput(budget.input(input, ImportPolicy.PLAYLIST_BYTES)), ImportPolicy.PLAYLIST_BYTES).use { decoded ->
                destination.outputStream().use { decoded.copyTo(it) }
            }
        }
        val raw = src.url.trim()
        if (raw.startsWith("content://")) {
            write(context.contentResolver.openInputStream(Uri.parse(raw)) ?: throw IOException("无法读取本地文件"))
            return
        }
        if (raw.startsWith("/") || raw.startsWith("file://")) {
            val path = raw.removePrefix("file://")
            write(File(path).inputStream())
            return
        }
        openHttp(src, budget, ::write)
    }

    private fun openHttp(src: Source, budget: ImportBudget, read: (java.io.InputStream) -> Unit) {
        val conn = (URL(src.url.trim()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 25000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", src.userAgent.ifBlank { prefs.defaultUserAgent })
            val effectiveReferer=src.referer.ifBlank{prefs.defaultReferer}
            if (effectiveReferer.isNotBlank()) setRequestProperty("Referer", effectiveReferer)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            ChannelRepository.parseHeaders(src.headers).forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            budget.watch{conn.disconnect()}
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            if((conn.getHeaderField("Content-Length")?.toLongOrNull()?:0)>ImportPolicy.PLAYLIST_BYTES)throw ImportLimitException("播放源内容超过大小上限")
            conn.inputStream.use(read)
        } finally {
            conn.disconnect()
        }
    }

}
