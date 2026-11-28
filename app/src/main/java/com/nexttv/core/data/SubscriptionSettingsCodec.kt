// SPDX-License-Identifier: GPL-3.0-only
// Next TV — stored subscription format for the channel repository.
package com.nexttv.core.data

import org.json.JSONArray
import org.json.JSONObject

internal object SubscriptionSettingsCodec {
    fun encode(sources: List<Source>): String {
        val entries = sources.map { source ->
            JSONObject(linkedMapOf(
                "id" to source.id, "name" to source.name, "url" to source.url,
                "enabled" to source.enabled, "autoUpdate" to source.autoUpdate,
                "kind" to source.kind, "epgUrl" to source.epgUrl,
                "userAgent" to source.userAgent, "referer" to source.referer, "headers" to source.headers,
                "channelCount" to source.channelCount, "unsupportedCount" to source.unsupportedCount, "lastUpdate" to source.lastUpdate,
                "lastError" to source.lastError
            ))
        }
        return JSONArray(entries).toString()
    }

    fun decode(serialized: String): List<Source> {
        val entries = JSONArray(serialized)
        val decoded = ArrayList<Source>(entries.length())
        var firstError: Exception? = null
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            runCatching {
                val address = entry.getString("url")
                val source = Source(
                    entry.getString("id"), entry.optString("name", "播放源"), address,
                    kind = entry.optString("kind", Source.KIND_URL),
                    enabled = entry.optBoolean("enabled", true),
                    autoUpdate = entry.optBoolean("autoUpdate", true)
                )
                source.copy(
                    epgUrl = entry.optString("epgUrl"), headers = entry.optString("headers"),
                    referer = entry.optString("referer"), userAgent = entry.optString("userAgent"),
                    lastError = entry.optString("lastError"), lastUpdate = entry.optLong("lastUpdate"),
                    channelCount = entry.optInt("channelCount"), unsupportedCount = entry.optInt("unsupportedCount")
                )
            }.onSuccess { decoded += it }.onFailure { firstError = firstError ?: (it as? Exception) }
        }
        // Keep the previous source config instead of treating one corrupt record as "no sources".
        if (entries.length() > 0 && decoded.isEmpty()) {
            throw IllegalArgumentException("订阅配置全部无法解析", firstError)
        }
        return decoded
    }
}
