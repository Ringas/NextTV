// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.data

import android.content.Context
import com.nexttv.core.io.ImportPolicy
import java.io.File
import java.util.Locale

/**
 * Disk cache accounting and user-initiated cleanup. Subscriptions, favourites,
 * credentials and settings are deliberately excluded.
 */
object CacheStore {
    fun usage(context: Context): Long {
        val filesDir = context.filesDir
        val logoDir = File(context.cacheDir, "logos")
        val epgDir = File(filesDir, "epg_feeds")
        var total = File(filesDir, "channels.json").length()
        filesDir.listFiles { file -> file.isFile && file.name.startsWith("pl_") }?.forEach { total += it.length() }
        total += directorySize(epgDir)
        total += directorySize(logoDir)
        return total
    }

    fun clear(context: Context): Long {
        val before = usage(context)
        val filesDir = context.filesDir
        File(filesDir, "channels.json").delete()
        File(filesDir, "channels.json.bak").delete()
        filesDir.listFiles { file -> file.isFile && file.name.startsWith("pl_") }?.forEach { it.delete() }
        deleteChildren(File(filesDir, "epg_feeds"))
        deleteChildren(File(context.cacheDir, "logos"))
        return before
    }

    fun format(bytes: Long): String = when {
        bytes < 0 -> "计算中…"
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    internal fun trimSourceCaches(context: Context, keepId: String) {
        val keep = "pl_$keepId.m3u"
        val files = context.filesDir.listFiles { file ->
            file.isFile && file.name.startsWith("pl_") && file.name.substringAfter("pl_").substringBefore('.').isNotBlank()
        } ?: return
        var total = files.sumOf { it.length() }
        if (total <= ImportPolicy.PLAYLIST_CACHE_TOTAL_BYTES) return
        files.filterNot { it.name == keep || it.name == "$keep.bak" }
            .sortedBy { it.lastModified() }
            .forEach { file ->
                if (total <= ImportPolicy.PLAYLIST_CACHE_TOTAL_BYTES) return@forEach
                total -= file.length()
                file.delete()
            }
    }

    private fun directorySize(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun deleteChildren(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        }
    }
}
