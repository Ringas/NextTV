package com.bolttv.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.bolttv.compose.core.data.ChannelRepository
import com.bolttv.compose.core.data.EpgRepository
import com.bolttv.compose.core.data.SourceWebServer
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

    val serverRunning by webServer.running.collectAsState()
    val accessUrl by webServer.accessUrl.collectAsState()

    DisposableEffect(Unit) {
        webServer.startServer(context)
        webServer.setOnSourceSubmitted { submitted ->
            scope.launch {
                status = "收到扫码源，加载中…"
                val result = repository.loadFromUrl(submitted.url, submitted.name)
                status = result.fold(
                    onSuccess = { "扫码成功，加载 $it 个频道" },
                    onFailure = { "加载失败: ${it.message}" }
                )
            }
        }
        onDispose { }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "播放源",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    scope.launch {
                        status = "加载中…"
                        val testUrl = "https://iptv-org.github.io/iptv/countries/cn.m3u"
                        val result = repository.loadFromUrl(testUrl, "IPTV-org CN")
                        status = result.fold(
                            onSuccess = { "成功加载 $it 个频道" },
                            onFailure = { "失败: ${it.message}" }
                        )
                    }
                }
            ) {
                Text("加载示例源 (iptv-org)")
            }

            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "电子节目单 (EPG)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    scope.launch {
                        epgStatus = "加载 EPG 中…"
                        val epgUrl = "https://iptv-org.github.io/epg/guides/cn/tv.cctv.com.epg.xml"
                        val result = epgRepository.loadFromUrl(epgUrl)
                        epgStatus = result.fold(
                            onSuccess = { "EPG 加载成功：$it 条节目" },
                            onFailure = { "EPG 失败: ${it.message}" }
                        )
                    }
                }
            ) {
                Text("加载示例 EPG")
            }

            if (epgStatus.isNotEmpty()) {
                Text(
                    text = epgStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onBack) {
                Text("返回")
            }
        }

        Column(
            modifier = Modifier.width(280.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "扫码添加播放源",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            QrCodeImage(
                content = accessUrl,
                size = 220.dp
            )

            Text(
                text = if (serverRunning && accessUrl != null) {
                    "手机连接同一 Wi-Fi 后扫码\n$accessUrl"
                } else {
                    "服务未启动"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
