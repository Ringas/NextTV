package com.nexttv

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.MutableState
import com.nexttv.core.App
import com.nexttv.core.data.Source
import com.nexttv.core.tv.PlaybackSession
import kotlinx.coroutines.runBlocking

class StartupBrandInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        waitForIdleSync()
        val app = App.get()
        val output = StringBuilder()
        val savedLive = app.prefs.startLive
        val savedLight = app.prefs.lightGlass
        val savedReduced = app.prefs.reduceMotion
        val savedLast = app.prefs.lastChannelId
        val savedAuto = app.prefs.autoUpdateOnStart
        val savedGuide = app.prefs.autoUpdateEpg
        var activity: Activity? = null
        var code = Activity.RESULT_OK

        @Suppress("UNCHECKED_CAST")
        val cacheFlag = App::class.java.getDeclaredField("cacheReady\$delegate").apply {
            isAccessible = true
        }.get(app) as MutableState<Boolean>

        fun await(description: String, condition: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 45_000
            while (SystemClock.uptimeMillis() < end) {
                if (condition()) return
                SystemClock.sleep(40)
            }
            error("Timed out: $description")
        }

        fun contains(node: AccessibilityNodeInfo?, value: String): Boolean {
            if (node == null) return false
            if (node.text?.toString() == value || node.contentDescription?.toString() == value) return true
            return (0 until node.childCount).any { contains(node.getChild(it), value) }
        }

        fun visible(value: String) = contains(uiAutomation.rootInActiveWindow, value)

        fun open() {
            activity = startActivitySync(
                Intent().setClassName("com.nexttv", "com.nexttv.core.tv.TvActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        fun close() {
            runOnMainSync { activity?.finish() }
            await("Activity destruction") { activity?.isDestroyed != false }
            activity = null
        }

        fun capture(name: String) {
            val bitmap = checkNotNull(uiAutomation.takeScreenshot())
            targetContext.openFileOutput("$name.png", 0).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }

        fun pass(message: String) {
            output.appendLine("PASS $message")
            sendStatus(0, Bundle().apply { putString("stream", "PASS $message\n") })
        }

        try {
            await("Application cache") { app.cacheReady }
            val source = "http://192.0.2.115:8787/live.m3u"
            if (app.channels.sources.none { it.url == source }) {
                app.channels.upsertSource(Source("startup-test", "启动测试", source))
            }
            check(runBlocking { app.channels.refreshAll() }.ok > 0)
            val channel = app.channels.channels.first { it.name.equals("LG1", ignoreCase = true) }

            runOnMainSync {
                app.prefs.startLive = false
                app.prefs.autoUpdateOnStart = false
                app.prefs.autoUpdateEpg = false
            }
            open()
            await("cached channel home") { visible("全部频道") }
            check(!visible("Next TV"))
            pass("ready channel home presents without a remaining brand layer")
            close()

            for (light in listOf(false, true)) {
                runOnMainSync {
                    app.prefs.lightGlass = light
                    app.prefs.reduceMotion = light
                    cacheFlag.value = false
                }
                open()
                await("channel startup brand, light=$light") { visible("Next TV") }
                check(!visible("全部频道") && !visible("正在读取频道…"))
                capture(if (light) "startup-home-light" else "startup-home-dark")
                runOnMainSync { cacheFlag.value = true }
                await("channel home after cache release") { visible("全部频道") && !visible("Next TV") }
                pass("slow channel launch shows only brand, light=$light reduced=$light")
                close()
            }

            runOnMainSync {
                app.prefs.startLive = true
                app.prefs.lastChannelId = channel.id
                app.prefs.reduceMotion = false
            }
            open()
            await("live brand before first frame") { visible("Next TV") }
            check(!visible("全部频道") && !visible("正在连接…"))
            capture("startup-live")
            await("real LG1 frame and immediate brand dismissal") {
                var rendered = false
                runOnMainSync {
                    rendered = PlaybackSession.active?.let { it.current.id == channel.id && it.rendered } == true
                }
                rendered && !visible("Next TV") && !visible("全部频道")
            }
            pass("direct live launch uses the real LG1 first frame without showing home")
        } catch (failure: Throwable) {
            code = Activity.RESULT_CANCELED
            output.appendLine(failure.stackTraceToString())
        } finally {
            runOnMainSync {
                activity?.finish()
                cacheFlag.value = true
                app.prefs.startLive = savedLive
                app.prefs.lightGlass = savedLight
                app.prefs.reduceMotion = savedReduced
                app.prefs.lastChannelId = savedLast
                app.prefs.autoUpdateOnStart = savedAuto
                app.prefs.autoUpdateEpg = savedGuide
            }
        }
        finish(code, Bundle().apply { putString("stream", output.toString()) })
    }
}
