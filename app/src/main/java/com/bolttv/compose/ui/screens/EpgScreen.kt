package com.bolttv.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bolttv.compose.ui.theme.NextTvColors
import com.bolttv.compose.core.data.ChannelRepository
import com.bolttv.compose.core.data.EpgRepository
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.model.EpgProgram
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * APTV 风格 EPG 时间轴：
 * - 顶部：日期切换 + 小时刻度
 * - 左侧：频道名
 * - 右侧：节目块（宽度 ∝ 时长），红线标记当前时间
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgScreen(
    repository: ChannelRepository,
    epgRepository: EpgRepository,
    onChannelClick: (Channel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channels by repository.channels.collectAsState()
    val epgLoaded by epgRepository.loaded.collectAsState()
    val programCount by epgRepository.programCount.collectAsState()

    // 以「今天 0 点」为基准，dayOffset 切换日期
    var dayOffset by remember { mutableStateOf(0) }
    val now = System.currentTimeMillis()

    val dayStart = remember(dayOffset) {
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    // 展示 6 小时窗口，可用左右扩展；默认从「当前整点往前 1 小时」或当天 0 点
    val windowHours = 6
    val pxPerMinute = 4.dp // 1 分钟 = 4dp → 1 小时 = 240dp
    val windowStart = remember(dayOffset, now) {
        if (dayOffset == 0) {
            // 今天：从当前时间往前对齐到整点再减 30 分钟，保证「现在」在视野中
            val cal = Calendar.getInstance()
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.MINUTE, -30)
            max(dayStart, cal.timeInMillis)
        } else {
            dayStart + 8L * 3600_000 // 非今天默认从 8:00 看
        }
    }
    val windowEnd = windowStart + windowHours * 3600_000L
    val totalWidth = pxPerMinute * (windowHours * 60)

    val hourFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("MM/dd E", Locale.getDefault()) }
    val hScroll = rememberScrollState()

    // 当前时间线位置
    val nowOffset: Dp = if (now in windowStart until windowEnd) {
        val minutes = ((now - windowStart) / 60_000f)
        pxPerMinute * minutes
    } else {
        (-1).dp
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NextTvColors.Bg)
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "节目表",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (epgLoaded) "$programCount 条节目" else "未加载 EPG",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { dayOffset = (dayOffset - 1).coerceAtLeast(-1) }) { Text("前一天") }
                Text(
                    text = dayFmt.format(Date(dayStart)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = { dayOffset = (dayOffset + 1).coerceAtMost(2) }) { Text("后一天") }
                Button(onClick = onBack) { Text("返回") }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!epgLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "请先在设置中加载 EPG（XMLTV）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        // 时间刻度行
        Row(Modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(160.dp)) // 对齐频道列
            Box(
                modifier = Modifier
                    .horizontalScroll(hScroll)
                    .height(32.dp)
            ) {
                Box(Modifier = Modifier.width(totalWidth).fillMaxHeight()) {
                    for (h in 0 until windowHours) {
                        val labelTime = windowStart + h * 3600_000L
                        Text(
                            text = hourFmt.format(Date(labelTime)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .offset(x = pxPerMinute * (h * 60))
                                .padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 频道 × 节目网格
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(channels, key = { it.id }) { channel ->
                val programs = epgRepository.getProgramsInRange(
                    channel.id,
                    channel.tvgId,
                    windowStart,
                    windowEnd
                )
                EpgChannelRow(
                    channel = channel,
                    programs = programs,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    pxPerMinute = pxPerMinute,
                    totalWidth = totalWidth,
                    nowOffset = nowOffset,
                    scrollState = hScroll,
                    onPlay = { onChannelClick(channel) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpgChannelRow(
    channel: Channel,
    programs: List<EpgProgram>,
    windowStart: Long,
    windowEnd: Long,
    pxPerMinute: Dp,
    totalWidth: Dp,
    nowOffset: Dp,
    scrollState: androidx.compose.foundation.ScrollState,
    onPlay: () -> Unit
) {
    val hourFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧频道名
        Surface(
            onClick = onPlay,
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight(),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF161B22),
                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 节目条带
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0D1117))
        ) {
            Box(modifier = Modifier.width(totalWidth).fillMaxHeight()) {
                // 小时竖线
                val hours = ((windowEnd - windowStart) / 3600_000L).toInt()
                for (h in 1 until hours) {
                    Box(
                        modifier = Modifier
                            .offset(x = pxPerMinute * (h * 60))
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                }

                // 节目块
                programs.forEach { prog ->
                    val startClamped = max(prog.start, windowStart)
                    val endClamped = minOf(prog.end, windowEnd)
                    val startMin = ((startClamped - windowStart) / 60_000f).coerceAtLeast(0f)
                    val durMin = ((endClamped - startClamped) / 60_000f).coerceAtLeast(8f) // 最少可视宽度
                    val isNow = System.currentTimeMillis() in prog.start until prog.end

                    Surface(
                        onClick = onPlay,
                        modifier = Modifier
                            .offset(x = pxPerMinute * startMin)
                            .width(pxPerMinute * durMin - 3.dp)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isNow) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else Color(0xFF21262D),
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = prog.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${hourFmt.format(Date(prog.start))}-${hourFmt.format(Date(prog.end))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                    }
                }

                // 当前时间红线
                if (nowOffset >= 0.dp) {
                    Box(
                        modifier = Modifier
                            .offset(x = nowOffset)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFFF5252))
                    )
                }
            }
        }
    }
}
