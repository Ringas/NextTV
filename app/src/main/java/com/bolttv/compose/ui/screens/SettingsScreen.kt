package com.bolttv.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bolttv.compose.ui.theme.NextTvColors
import com.bolttv.compose.core.data.ChannelRepository
import com.bolttv.compose.core.data.EpgRepository
import com.bolttv.compose.core.data.SourceWebServer
import com.bolttv.compose.core.model.PlaylistSource
import com.bolttv.compose.ui.components.QrCodeImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: ChannelRepository,
    epgRepository: EpgRepository,
    webServer: SourceWebServer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var epgStatus by remember { mutableStateOf("") }

    val sources by repository.sources.collectAsState()
    val activeId by repository.activeSourceId.collectAsState()
    val serverRunning by webServer.running.collectAsState()
    val accessUrl by webServer.accessUrl.collectAsState()

    DisposableEffect(Unit) {
        webServer.startServer(context)
        webServer.setOnSourceSubmitted { submitted ->
            scope.launch {
                status = "收到扫码源，加载中…"
                val result = repository.addOrUpdateSource(submitted.name, submitted.url)
                status = result.fold(
                    onSuccess = { "已添加并加载 $it 个频道" },
                    onFailure = { "加载失败: ${it.message}" }
                )
            }
        }
        onDispose { }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(NextTvColors.Bg).padding(40.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            modifier = Modifier.weight(1.2f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                color = NextTvColors.TextPrimary
            )

            Text(
                text = "播放源管理",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            status = "加载示例源…"
                            val result = repository.addOrUpdateSource(
                                "IPTV-org CN",
                                "https://iptv-org.github.io/iptv/countries/cn.m3u"
                            )
                            status = result.fold(
                                onSuccess = { "成功 $it 个频道" },
                                onFailure = { "失败: ${it.message}" }
                            )
                        }
                    }
                ) { Text("添加示例源") }

                Button(
                    onClick = {
                        scope.launch {
                            status = "刷新中…"
                            val result = repository.refreshActiveSource()
                            status = result.fold(
                                onSuccess = { "已刷新 $it 个频道" },
                                onFailure = { "刷新失败: ${it.message}" }
                            )
                        }
                    }
                ) { Text("刷新当前源") }
            }

            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 源列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sources.isEmpty()) {
                    item {
                        Text(
                            text = "暂无播放源，请添加或扫码",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(sources, key = { it.id }) { source ->
                    SourceRow(
                        source = source,
                        active = source.id == activeId,
                        onActivate = {
                            scope.launch {
                                status = "切换源…"
                                val r = repository.activateSource(source.id)
                                status = r.fold(
                                    onSuccess = { "已切换，共 $it 频道" },
                                    onFailure = { "失败: ${it.message}" }
                                )
                            }
                        },
                        onDelete = {
                            scope.launch {
                                repository.removeSource(source.id)
                                status = "已删除 ${source.name}"
                            }
                        }
                    )
                }
            }

            Text(
                text = "电子节目单 (EPG)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    scope.launch {
                        epgStatus = "加载 EPG…"
                        val epgUrl =
                            "https://iptv-org.github.io/epg/guides/cn/tv.cctv.com.epg.xml"
                        val result = epgRepository.loadFromUrl(epgUrl)
                        epgStatus = result.fold(
                            onSuccess = { "EPG：$it 条节目" },
                            onFailure = { "EPG 失败: ${it.message}" }
                        )
                    }
                }
            ) { Text("加载示例 EPG") }

            if (epgStatus.isNotEmpty()) {
                Text(
                    text = epgStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onBack) { Text("返回") }
        }

        Column(
            modifier = Modifier.width(260.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "扫码添加播放源",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            QrCodeImage(content = accessUrl, size = 200.dp)
            Text(
                text = if (serverRunning && accessUrl != null) {
                    "同一 Wi-Fi 扫码\n$accessUrl"
                } else {
                    "服务未启动"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceRow(
    source: PlaylistSource,
    active: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = buildString {
                if (active) append("● ")
                append(source.name)
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = source.url ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!active) {
                Button(onClick = onActivate) { Text("使用") }
            }
            Button(onClick = onDelete) { Text("删除") }
        }
    }
}
