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
    private const val MAX_RECORDS = 64
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

    private val hashKey = Regex("[0-9a-f]{64}")

    fun record(
        key: String,
        channelName: String,
        routeLabel: String,
        code: String,
        message: String,
        at: Long = System.currentTimeMillis(),
    ) {
        if (!hashKey.matches(key)) return
        records[key] = RouteFailureRecord(
            channelName = channelName.take(160),
            routeLabel = routeLabel.take(40),
            code = code.take(160),
            message = redactSensitive(message.take(300)),
            at = at,
        )
        if (records.size > MAX_RECORDS) {
            records.entries.sortedBy { it.value.at }.take(records.size - MAX_RECORDS)
                .forEach { records.remove(it.key, it.value) }
        }
        persist()
    }

    fun get(key: String): RouteFailureRecord? = records[key]

    fun recent(limit: Int = 1): List<RouteFailureRecord> =
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
            var dropped = false
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val key = item.optString("key")
                // v1 stored raw source/url/header keys. Never keep those at rest.
                if (!hashKey.matches(key)) { dropped = true; continue }
                records[key] = RouteFailureRecord(
                    channelName = item.optString("channel"),
                    routeLabel = item.optString("route"),
                    code = item.optString("code"),
                    message = redactSensitive(item.optString("message")),
                    at = item.optLong("at"),
                )
            }
            if (records.size > MAX_RECORDS) {
                records.entries.sortedBy { it.value.at }.take(records.size - MAX_RECORDS)
                    .forEach { records.remove(it.key, it.value) }
                dropped = true
            }
            if (dropped) persist()
        }
    }

    private fun persist() {
        val file = journalFile ?: return
        // Serializing even 64 records on the caller thread can block playback;
        // snapshot the immutable records here and build JSON inside the writer.
        val snapshot = records.entries.map { it.key to it.value }
        writer.execute {
            runCatching {
                val array = JSONArray()
                snapshot.forEach { (key, record) ->
                    array.put(JSONObject()
                        .put("key", key)
                        .put("channel", record.channelName)
                        .put("route", record.routeLabel)
                        .put("code", record.code)
                        .put("message", record.message)
                        .put("at", record.at))
                }
                val payload = JSONObject().put("version", 1).put("records", array).toString()
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
