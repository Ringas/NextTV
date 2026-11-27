// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.design

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nexttv.core.App
import com.nexttv.core.data.Channel
import com.nexttv.core.data.Source
import com.nexttv.core.tv.AppTextScale
import com.nexttv.core.tv.BrowserController
import com.nexttv.core.tv.ChannelSidePanel
import com.nexttv.core.tv.NextTvStyle
import com.nexttv.core.tv.TvActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Real browser-side composition, used to lock the channel column / edge hint gap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = App::class, sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class ChannelBrowserScreenshot {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val channels = listOf(
        channel("cctv1", "CCTV-1 综合", "综合频道", 1),
        channel("cctv5", "CCTV-5 体育", "体育频道", 5),
        channel("hunan", "湖南卫视", "芒果台", 7),
        channel("dragon", "东方卫视", "SMG", 8),
        channel("js", "江苏卫视", "荔枝台", 9),
        channel("zj", "浙江卫视", "中国蓝", 10),
        channel("bj", "北京卫视", "京视", 11),
        channel("sz", "深圳卫视", "鹏城", 12),
    )

    @Test
    fun channelHint() {
        seed(App.get(), channels)
        val current = channels.first { it.id == "cctv5" }
        val browser = BrowserController()
        val fakeActivity = TvActivity()

        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalActivity provides fakeActivity) {
                AppTextScale {
                    Box(Modifier.fillMaxSize().background(NextTvStyle.Home)) {
                        ChannelSidePanel(
                            current = current,
                            group = null,
                            generation = 0,
                            onGroup = {},
                            onSelect = {},
                            onProgram = { _, _ -> },
                            controller = browser,
                            onCloseAll = {},
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(420)
        compose.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            if (content.width <= 0 || content.height <= 0) return@onActivity
            val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
            content.draw(Canvas(bitmap))
            FileOutputStream(File("build/outputs/design-screens", "08-channel-hint.png")).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-land-mdpi")
    fun groups1080Mdpi() {
        seed(App.get(), channels)
        val current = channels.first { it.id == "cctv5" }
        val browser = BrowserController()
        val fakeActivity = TvActivity()

        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalActivity provides fakeActivity) {
                AppTextScale {
                    Box(Modifier.fillMaxSize().background(NextTvStyle.Home)) {
                        ChannelSidePanel(
                            current = current,
                            group = null,
                            generation = 0,
                            onGroup = {},
                            onSelect = {},
                            onProgram = { _, _ -> },
                            controller = browser,
                            onCloseAll = {},
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(420)
        browser.navigate(-1)
        compose.mainClock.advanceTimeBy(420)
        compose.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            if (content.width <= 0 || content.height <= 0) return@onActivity
            val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
            content.draw(Canvas(bitmap))
            FileOutputStream(File("build/outputs/design-screens", "08-groups-1080-mdpi.png")).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun seed(app: App, list: List<Channel>) {
        val repo = app.channels
        fun field(name: String, value: Any?) {
            ChannelRepositoryField.set(repo, name, value)
        }
        field("channels", list)
        field("rawChannels", list)
        field("groups", list.map { it.groupName }.distinct())
        field("sources", listOf(Source("demo", "演示订阅", "https://example.invalid/list.m3u")))
        field("displayCacheSource", null)
    }

    private fun channel(id: String, name: String, group: String, number: Int) = Channel(
        id = id,
        name = name,
        tvgId = "",
        logo = "",
        group = group,
        url = "https://example.invalid/$id.m3u8",
        sourceId = "demo",
        number = number,
    )
}

private object ChannelRepositoryField {
    fun set(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}
