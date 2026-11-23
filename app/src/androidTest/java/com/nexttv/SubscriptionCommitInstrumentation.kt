// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import com.nexttv.core.data.ChannelRepository
import com.nexttv.core.data.Prefs
import com.nexttv.core.data.Source
import java.io.File
import kotlinx.coroutines.runBlocking

class SubscriptionCommitInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val directory = File(targetContext.cacheDir, "refresh-commit-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getCacheDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences("${directory.name}-$name", mode)
        }
        val store = context.getSharedPreferences("nexttv", Context.MODE_PRIVATE)
        val result = runCatching {
            val guide = "https://example.invalid/guide.xml"
            store.edit().putString("epg_url", guide).putBoolean("preload_next", false)
                .putBoolean("auto_update", false).commit()
            val prefs = Prefs(context)
            check(prefs.preloadMode == 0 && !prefs.autoUpdateOnStart)
            prefs.migrateLegacyEpg(setOf(guide))
            check(prefs.epgUrl.isEmpty() && store.getString("legacy_epg_url_backup", "") == guide)
            prefs.epgUrl = "https://example.invalid/manual.xml"
            prefs.migrateLegacyEpg(setOf(guide))
            check(prefs.epgUrl.endsWith("/manual.xml"))

            fun source(id: String): Source {
                val file = File(directory, "$id.m3u")
                file.writeText("#EXTM3U\n#EXTINF:-1,$id\nhttps://example.invalid/$id.m3u8\n")
                return Source(id, id, file.absolutePath, kind = Source.KIND_FILE,
                    epgUrl = guide, headers = "X-Test: metadata", autoUpdate = false)
            }
            val old = source("old")
            val replacement = source("replacement")
            val repo = ChannelRepository(context, prefs)
            repo.upsertSource(old)
            runBlocking {
                check(repo.refreshAll().ok == 1)
                val superseded = repo.refreshAll { progress ->
                    if (progress.startsWith("正在更新") && repo.sources.single().id == old.id) {
                        repo.removeSource(old.id)
                        repo.upsertSource(replacement)
                    }
                }
                check(superseded.ok == 0 && superseded.failed == 0)
                check(repo.channels.isEmpty())
                check(!File(directory, "pl_old.m3u").exists())
                check(repo.refreshAll().ok == 1)
            }
            val cache = File(directory, "channels.json")
            val committed = cache.readBytes()
            val blocker = File(directory, "channels.json.new")
            check(blocker.mkdir())
            File(replacement.url).writeText("#EXTM3U\n#EXTINF:-1,updated\nhttps://example.invalid/updated.m3u8\n")
            runBlocking {
                val failed = repo.refreshAll()
                check(failed.ok == 0 && failed.failed == 1 && failed.storageWarning.contains("无法写入"))
                check(repo.channels.single().name == replacement.name)
                check(cache.readBytes().contentEquals(committed))
                check(blocker.delete())
                val recovered = repo.refreshAll()
                check(recovered.ok == 1 && recovered.storageWarning.isEmpty())
                check(repo.channels.single().name == "updated")
            }
            val reopened = ChannelRepository(context, Prefs(context))
            reopened.loadCache()
            val persisted = reopened.sources.single()
            check(persisted.id == replacement.id && persisted.epgUrl == guide &&
                persisted.headers == replacement.headers && !persisted.autoUpdate)
            check(reopened.channels.single().name == "updated")
        }
        store.edit().clear().commit()
        directory.deleteRecursively()
        finish(
            if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply {
                putString("stream", result.exceptionOrNull()?.stackTraceToString()
                    ?: "PASS legacy keys and XMLTV migration preserved; superseded refresh cannot revive deleted source cache; write failure preserves previous cache and retry survives reopen\n")
            },
        )
    }
}
