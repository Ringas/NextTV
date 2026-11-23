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

class ChannelNumberResetInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val dir = File(targetContext.cacheDir, "numbering-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = dir
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences("${dir.name}-$name", mode)
        }
        fun source(id: String, name: String): Source {
            val playlist = File(dir, "$id.m3u")
            playlist.writeText("#EXTM3U\n#EXTINF:-1,$name\nhttps://example.invalid/$id.m3u8\n")
            return Source(id, name, playlist.absolutePath, kind = Source.KIND_FILE)
        }
        val result = runCatching {
            runBlocking {
                val prefs = Prefs(context)
                val repository = ChannelRepository(context, prefs)
                fun numbers() = repository.channels.associate { it.name to it.number }
                // Simulate an empty library saved by an older build.
                prefs.channelNumbersJson = """{"old-channel":99}"""
                repository.upsertSource(source("first", "测试甲"))
                repository.upsertSource(source("second", "测试乙"))
                repository.refreshAll()
                check(numbers() == mapOf("测试甲" to 1, "测试乙" to 2)) { numbers() }
                repository.removeSource("first")
                repository.refreshAll()
                check(numbers() == mapOf("测试乙" to 2)) { numbers() }
                repository.removeSource("second")
                check(prefs.channelNumbersJson == "{}")
                repository.upsertSource(source("replacement", "测试丙"))
                repository.refreshAll()
                check(numbers() == mapOf("测试丙" to 1)) { numbers() }
                val reopened = ChannelRepository(context, prefs)
                reopened.loadCache()
                check(reopened.channels.single().number == 1)
            }
        }
        context.getSharedPreferences("nexttv", Context.MODE_PRIVATE).edit().clear().commit()
        dir.deleteRecursively()
        finish(
            if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply {
                putString("stream", result.exceptionOrNull()?.stackTraceToString()
                    ?: "PASS empty library resets stale numbering; remaining sources retain their number; new library restarts at 1 and persists\n")
            },
        )
    }
}
