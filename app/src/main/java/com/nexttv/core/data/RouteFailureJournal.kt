// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** One persisted failure entry for a single playback route. */
data class RouteFailureRecord(
    val channelName: String,
    val routeLabel: String,
    val code: String,
    val message: String,
    val at: Long,
)

/**
 * Route-keyed failure history. Unlike the old per-session single string, a
 * route keeps its own last failure and the history is restored on next launch.
 * It is only removed by the explicit cache-clear action.
 */
object RouteFailureJournal {
    private const val FILE_NAME = "route_failures.json"
    private val records = ConcurrentHashMap<String, RouteFailureRecord>()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nexttv-route-failures").apply { isDaemon = true }
    }
    @Volatile private var journalFile: File? = null

    @Synchronized
    fun attach(context: Context) {
        if (journalFile != null) return
        val file = File(context.filesDir, FILE_NAME)
        journalFile = file
        load(file)
    }

    fun record(
        key: String,
        channelName: String,
        routeLabel: String,
        code: String,
        message: String,
        at: Long = System.currentTimeMillis(),
    ) {
        if (key.isBlank()) return
        records[key] = RouteFailureRecord(
            channelName = channelName.take(160),
            routeLabel = routeLabel.take(40),
            code = code.take(160),
            message = message.take(300),
            at = at,
        )
        persist()
    }

    fun get(key: String): RouteFailureRecord? = records[key]

    fun recent(limit: Int = 4): List<RouteFailureRecord> =
        records.values.sortedByDescending { it.at }.take(limit.coerceAtLeast(1))

    @Synchronized
    fun clear(context: Context) {
        records.clear()
        val file = journalFile ?: File(context.filesDir, FILE_NAME).also { journalFile = it }
        writer.execute { runCatching { file.delete() } }
    }

    private fun load(file: File) {
        if (!file.isFile) return
        runCatching {
            val array = JSONObject(file.readText()).optJSONArray("records") ?: return@runCatching
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val key = item.optString("key")
                if (key.isBlank()) continue
                records[key] = RouteFailureRecord(
                    channelName = item.optString("channel"),
                    routeLabel = item.optString("route"),
                    code = item.optString("code"),
                    message = item.optString("message"),
                    at = item.optLong("at"),
                )
            }
        }
    }

    private fun persist() {
        val file = journalFile ?: return
        val snapshot = JSONArray()
        records.forEach { (key, record) ->
            snapshot.put(JSONObject()
                .put("key", key)
                .put("channel", record.channelName)
                .put("route", record.routeLabel)
                .put("code", record.code)
                .put("message", record.message)
                .put("at", record.at))
        }
        val payload = JSONObject().put("version", 1).put("records", snapshot).toString()
        writer.execute {
            runCatching {
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(payload)
                if (!temp.renameTo(file)) {
                    file.writeText(payload)
                    temp.delete()
                }
            }
        }
    }
}
