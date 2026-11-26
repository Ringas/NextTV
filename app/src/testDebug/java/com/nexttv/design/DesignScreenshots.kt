// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nexttv.core.App
import com.nexttv.core.data.Channel
import com.nexttv.core.data.Program
import com.nexttv.core.tv.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

/**
 * Renders the six acceptance screens plus the light-player contrast regression
 * at 1920×1080 without an emulator. The harness composes the real primitives
 * (header, cards, category sheet, programme rail, player badges/actions and
 * settings material) with stable fake data, so CI can compare structure, type
 * scale and spacing every push.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = App::class, sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class DesignScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/outputs/design-screens").apply { mkdirs() }

    @Test fun home() {
        shoot("01-home") { HomeScene(tab = 0) }
    }

    @Test fun favorites() {
        shoot("02-favorites") { HomeScene(tab = 1) }
    }

    @Test fun category() {
        shoot("03-category") { CategoryScene() }
    }

    @Test fun player() {
        shoot("04-player") { PlayerScene() }
    }

    @Test fun playerLight() {
        val prefs = App.get().prefs
        val previous = prefs.lightGlass
        prefs.lightGlass = true
        try {
            shoot("04-player-light") { PlayerScene() }
        } finally {
            prefs.lightGlass = previous
        }
    }

    @Test fun schedule() {
        shoot("05-schedule") { ScheduleScene() }
    }

    @Test fun settings() {
        shoot("06-settings") { SettingsScene() }
    }

    @Test fun edgeHint() {
        shoot("07-edge-hint") {
            Box(Modifier.fillMaxSize().background(NextTvStyle.Home), contentAlignment = Alignment.Center) {
                BrowserEdgeHint(30.dp, Modifier.height(420.dp))
            }
        }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(NextTvStyle.Home)) {
                AppTextScale { content() }
            }
        }
        compose.mainClock.advanceTimeBy(240)
        compose.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            if (content.width <= 0 || content.height <= 0) return@onActivity
            val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
            content.draw(Canvas(bitmap))
            FileOutputStream(File(outputDir, "$name.png")).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}

private val demoChannels = listOf(
    channel("cctv1", "CCTV-1 综合", "综合频道", 1),
    channel("cctv5", "CCTV-5 体育", "体育频道", 5).copy(
        catchup = "default", catchupSource = "?utc={utc}&lutc={lutc}", catchupDays = 7,
    ),
    channel("hunan", "湖南卫视", "芒果台", 7),
    channel("dragon", "东方卫视", "SMG", 8),
    channel("zhejiang", "浙江卫视", "中国蓝", 9),
    channel("jiangsu", "江苏卫视", "幸福蓝海", 10),
    channel("beijing", "北京卫视", "BTV", 11),
    channel("guangdong", "广东卫视", "GRT", 12),
    channel("cctv6", "CCTV-6 电影", "电影频道", 6),
    channel("cctv13", "CCTV-13 新闻", "新闻频道", 13),
    channel("cctv5plus", "CCTV-5+ 体育赛事", "体育频道", 14),
    channel("satellite", "五星体育", "体育频道", 15),
    channel("gd-sports", "广东体育", "体育频道", 16),
    channel("shenzhen", "深圳卫视", "深圳广电", 17),
    channel("tianjin", "天津卫视", "天津广电", 18),
    channel("shandong", "山东卫视", "山东广电", 19),
    channel("anhui", "安徽卫视", "安徽广电", 20),
    channel("hubei", "湖北卫视", "湖北广电", 21),
    channel("sichuan", "四川卫视", "四川广电", 22),
    channel("henan", "河南卫视", "河南广电", 23),
    channel("chongqing", "重庆卫视", "重庆广电", 24),
    channel("cartoon", "金鹰卡通", "卡通频道", 28),
    channel("cctv9", "CCTV-9 纪录", "纪录频道", 29),
    channel("cctv4", "CCTV-4 国际", "国际频道", 30),
)

private val demoCaptions = mapOf(
    "cctv1" to "19:30 新闻联播",
    "cctv5" to "19:35 天下足球",
    "hunan" to "20:10 你好，星期六",
    "dragon" to "19:30 东方新闻",
    "zhejiang" to "20:20 奔跑吧",
    "jiangsu" to "19:30 江苏新闻",
    "beijing" to "19:35 养生堂",
    "guangdong" to "20:05 广东新闻联播",
    "cctv6" to "20:15 电影预告",
    "cctv13" to "20:00 新闻直播间",
    "cctv5plus" to "19:30 赛事直播",
    "satellite" to "20:00 五星足球",
)

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

@Composable
private fun HomeScene(tab: Int) {
    val shown = if (tab == 1) demoChannels.take(12) else demoChannels
    val nav = remember { List(3) { FocusRequester() } }
    val first = remember { FocusRequester() }
    val category = remember { FocusRequester() }
    val focusers = remember { mutableStateMapOf<String, FocusRequester>() }
    Box(Modifier.fillMaxSize()) {
        HomeBackdrop(demoChannels.firstOrNull { it.id == "cctv5" })
        HomeNavigation(
            tab, true, nav,
            Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            homeCompositions = { 0 }, onSelect = {},
        )
        Column(Modifier.fillMaxSize().padding(top = 88.dp)) {
            HomeHeader(
                title = if (tab == 1) "我的收藏" else "全部频道",
                subtitle = if (tab == 1) "${shown.size} 个频道 · 长按卡片可取消收藏" else "共 ${demoChannels.size} 个频道 · 更新于 20:41",
                showCategory = tab == 0,
                first = category,
                navUp = nav[tab],
                categoryDown = if (shown.isEmpty()) category else FocusRequester.Cancel,
                horizontalPadding = 48,
                minHeight = 76,
                onFocus = {},
                onCategoryClick = {},
            )
            LazyVerticalGrid(
                GridCells.Fixed(4),
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(shown, key = { _, c -> c.id }) { index, c ->
                    HomeChannelTile(
                        channel = c,
                        index = index,
                        count = shown.size,
                        columns = 4,
                        tab = tab,
                        focusers = focusers,
                        topFocus = nav[tab],
                        caption = demoCaptions[c.id] ?: "暂无节目信息",
                        favorite = tab == 1,
                        menuOpen = false,
                        previewSlot = null,
                        previewRunning = false,
                        onFocus = {},
                        onLongClick = {},
                        onClick = {},
                        onMenuBounds = {},
                        onFavorite = {},
                    )
                }
            }
        }
    }
    LaunchedEffect(shown) {
        withFrameNanos {}
        withFrameNanos {}
        runCatching { focusers[shown.first().id]?.requestFocus() }
    }
}

@Composable
private fun CategoryScene() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer(alpha = .74f)) { HomeScene(tab = 0) }
        AppModalHost {
            Box(Modifier.fillMaxSize()) {
                StableDialogHost({}) {
                    CategoryDialog(
                        choices = listOf(
                            CategoryChoice("", "全部频道", 24),
                            CategoryChoice("央视", "央视", 12),
                            CategoryChoice("卫视", "卫视", 8),
                            CategoryChoice("体育", "体育", 6),
                            CategoryChoice("影视", "影视", 4),
                            CategoryChoice("新闻", "新闻", 3),
                            CategoryChoice("少儿", "少儿", 2),
                        ),
                        selectedId = "体育",
                        onDismiss = {},
                        onChoose = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleScene() {
    val channels = demoChannels.take(8)
    val candidate = channels.first { it.id == "cctv5" }
    val start = remember {
        Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 18, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    fun at(minutes: Int) = start + minutes.toLong() * 60_000
    val programmes = listOf(
        Triple("体育新闻", 0, 30),
        Triple("足球之夜", 30, 30),
        Triple("天下足球", 65, 85),
        Triple("体育世界", 150, 30),
        Triple("冠军欧洲", 180, 45),
        Triple("体育晨报 · 精编", 225, 30),
    ).map { (title, from, span) ->
        Program(
            channelKey = candidate.id,
            title = title,
            desc = "",
            start = at(from),
            stop = at(from + span),
        )
    }
    val requests = remember { mutableStateMapOf<String, FocusRequester>() }
    val epgRequests = remember { mutableStateMapOf<Long, FocusRequester>() }
    val days = remember { listOf(0, 1, 2) }
    val dateRequests = remember(days) { days.associateWith { FocusRequester() } }
    val channelList = remember { LazyListState(1) }
    val epgList = remember { LazyListState(0) }
    val dateList = remember { LazyListState(0) }
    Box(Modifier.fillMaxSize()) {
        StadiumBackdrop()
        ProgrammeSideSheet(
            width = 307.dp,
            candidate = candidate,
            channels = channels,
            showLogos = true,
            list = channelList,
            requests = requests,
            programs = programmes,
            epgList = epgList,
            epgRequests = epgRequests,
            days = days,
            dateList = dateList,
            dateRequests = dateRequests,
            epgDay = 0,
            now = start + 104L * 60_000,
            region = BrowserRegion.CHANNELS,
            onChannelFocus = {},
            onChannelClick = {},
            onProgramFocus = {},
            onProgramClick = { _, _ -> },
            onDate = {},
            onDateFocus = {},
        )
    }
    LaunchedEffect(candidate) {
        withFrameNanos {}
        withFrameNanos {}
        runCatching { requests[candidate.id]?.requestFocus() }
    }
}

@Composable
private fun PlayerScene() {
    val channel = demoChannels.first { it.id == "cctv5" }
    val audio = remember { FocusRequester() }
    val bitmap = remember {
        val stream = DesignScreenshots::class.java.classLoader
            ?.getResourceAsStream("stadium-1920.jpg") ?: error("stadium-1920.jpg missing")
        stream.use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    Box(Modifier.fillMaxSize()) {
        Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .56f to Color.Transparent,
                    .72f to Color.Black.copy(alpha = .38f),
                    1f to Color.Black.copy(alpha = .96f),
                )
            )
        )
        Column(Modifier.align(Alignment.BottomCenter).width(749.dp).padding(bottom = 19.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(17.dp), verticalAlignment = Alignment.Top) {
                Row(Modifier.width(260.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(68.dp).height(36.dp).clip(RoundedCornerShape(6.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF3E5E75), Color(0xFF9FB4C3))),
                                RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText("CCTV 5", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .94f), maxLines = 1)
                    }
                    Column(Modifier.weight(1f).height(56.dp).padding(start = 8.dp), verticalArrangement = Arrangement.Center) {
                        AppText(
                            "CCTV-5 体育", Modifier.fillMaxWidth().height(27.dp),
                            fontSize = InteractionStyle.subheading, fontWeight = FontWeight.SemiBold,
                            color = NextTvStyle.MediaText,
                            maxLines = 1, lineHeight = InteractionStyle.subheading * 1.2f,
                        )
                        Row(Modifier.padding(top = 3.dp).height(20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            InfoBadge("线路 1/3", onMedia = true)
                            InfoBadge("1080P", BadgeTone.Cool, onMedia = true)
                            InfoBadge("立体声", BadgeTone.Cool, onMedia = true)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    AppText(
                        "天下足球", Modifier.fillMaxWidth().height(27.dp),
                        fontSize = InteractionStyle.heading, fontWeight = FontWeight.SemiBold,
                        color = NextTvStyle.MediaText,
                        maxLines = 1, lineHeight = InteractionStyle.heading * 1.1f,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp).height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        InfoBadge("直播中", BadgeTone.Live, onMedia = true)
                        AppText(
                            "19:35–21:00 · 接下来 21:00 体育世界",
                            Modifier.weight(1f).padding(start = 7.dp),
                            fontSize = InteractionStyle.captionSmall,
                            color = NextTvStyle.MediaMuted, maxLines = 1,
                        )
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp).background(NextTvStyle.MediaTrack, RoundedCornerShape(50))) {
                        Box(Modifier.fillMaxWidth(.64f).fillMaxHeight().background(NextTvStyle.MediaFill, RoundedCornerShape(50)))
                    }
                }
                Column(Modifier.width(112.dp).height(56.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    AppText(
                        "21:14", Modifier.fillMaxWidth(), fontSize = InteractionStyle.heading,
                        fontWeight = FontWeight.SemiBold, color = NextTvStyle.MediaText, maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        lineHeight = InteractionStyle.heading,
                    )
                    AppText(
                        "周二 11月24日", Modifier.fillMaxWidth().padding(top = 2.dp),
                        fontSize = InteractionStyle.captionSmall, color = NextTvStyle.MediaMuted,
                        maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(top = 7.dp).height(1.dp).background(NextTvStyle.MediaLine, RoundedCornerShape(50)))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                QuickActionButton("线路 1/3", "切换线路", Modifier, {}, {})
                QuickActionButton("音轨", "选择音轨", Modifier.focusRequester(audio), {}, {})
                QuickActionButton("画面适配", "调整画面比例", Modifier, {}, {})
                QuickActionButton("收藏", "添加收藏", Modifier, {}, {})
                QuickActionButton("解码方式", "硬件解码", Modifier, {}, {})
                QuickActionButton("详情", "节目详情", Modifier, {}, {})
                QuickActionButton("主页", "返回主页", Modifier, {}, {})
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        withFrameNanos {}
        runCatching { audio.requestFocus() }
    }
}

@Composable
private fun SettingsScene() {
    val nav = remember { List(3) { FocusRequester() } }
    val categories = listOf("订阅与节目单", "播放", "频道与外观", "高级", "关于")
    val rows = listOf(
        SettingsRowData("频道合并", "关闭", "将不同订阅中的同一频道合成多条播放线路，并汇总节目单。", null),
        SettingsRowData("频道布局", "网格", "选择首页频道的展示方式。", {}),
        SettingsRowData("频道预览", "开启", "停稳后同时静音预览当前可见频道，操作时暂停。", null),
        SettingsRowData("界面主题", "深色", "选择深色或浅色，应用于首页、设置和播放器浮层。", {}),
        SettingsRowData("列表台标", "显示", "控制播放时频道侧栏中的台标。", null),
        SettingsRowData("动态效果", "标准", "减弱模式关闭胶囊回弹、焦点渐变、浮层过渡与换台渐变。", {}),
    )
    val activeRow = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        HomeNavigation(
            tab = 2, focused = true, requests = nav,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            homeCompositions = { 0 }, onSelect = {},
        )
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 92.dp).width(806.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(Modifier.weight(.24f)) {
                Spacer(Modifier.height(38.dp))
                categories.forEach { name ->
                    val selected = name == "频道与外观"
                    TvSurface(
                        Modifier.fillMaxWidth().height(44.dp),
                        kind = "category", selected = selected, onClick = {},
                    ) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                            AppText(
                                name,
                                color = if (selected) NextTvStyle.Ivory else NextTvStyle.Muted,
                                fontSize = InteractionStyle.body,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(.76f)) {
                AppText(
                    "频道与外观", Modifier.padding(start = 12.dp),
                    fontSize = InteractionStyle.heading, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, lineHeight = InteractionStyle.heading * 1.1f,
                )
                AppText(
                    "调整频道展示、主题与动态效果。",
                    Modifier.padding(start = 12.dp, top = 2.dp, bottom = 16.dp),
                    color = NextTvStyle.Muted, fontSize = InteractionStyle.caption, maxLines = 2,
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    rows.forEachIndexed { index, row ->
                        val active = row.title == "频道布局"
                        val modifier = Modifier.fillMaxWidth().height(44.dp)
                            .then(if (active) Modifier.focusRequester(activeRow) else Modifier)
                        if (row.action != null) {
                            TvSurface(modifier, kind = "setting", selected = false, onClick = {}) {
                                SettingsRowContent(row, "频道与外观", narrow = false, bodyHeight = 44.dp, modifier = Modifier.fillMaxWidth())
                            }
                        } else {
                            Box(modifier) {
                                SettingsRowContent(row, "频道与外观", narrow = false, bodyHeight = 44.dp, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        if (index < rows.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(.5.dp).background(NextTvStyle.Divider))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp, start = 16.dp).height(.5.dp).background(NextTvStyle.Divider))
                AppText(
                    "减弱模式关闭胶囊回弹、焦点渐变、浮层过渡与换台渐变。鼠标滚轮、\n触控滑动及其惯性仍可使用。",
                    Modifier.padding(start = 16.dp, top = 12.dp),
                    color = NextTvStyle.Muted, fontSize = InteractionStyle.caption, maxLines = 2,
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        withFrameNanos {}
        runCatching { activeRow.requestFocus() }
    }
}

@Composable
private fun StadiumBackdrop() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0xFF314552), Color(0xFF10151B), Color(0xFF080A0E)),
                center = androidx.compose.ui.geometry.Offset(480f, 240f),
                radius = 620f,
            )
        )
    )
}

