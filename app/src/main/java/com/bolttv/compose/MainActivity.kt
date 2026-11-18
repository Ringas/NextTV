package com.bolttv.compose

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bolttv.compose.core.data.AppPreferences
import com.bolttv.compose.core.data.ChannelRepository
import com.bolttv.compose.core.data.EpgRepository
import com.bolttv.compose.core.data.SourceWebServer
import com.bolttv.compose.core.model.Channel
import com.bolttv.compose.core.player.DualPlayerManager
import com.bolttv.compose.core.player.PreviewPlayerManager
import com.bolttv.compose.ui.screens.EpgScreen
import com.bolttv.compose.ui.screens.HomeScreen
import com.bolttv.compose.ui.screens.PlayerScreen
import com.bolttv.compose.ui.screens.SettingsScreen
import com.bolttv.compose.ui.theme.BoltComposeTheme
import com.bolttv.compose.ui.theme.NextTvColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContent {
                BoltComposeTheme {
                    NextTvApp()
                }
            }
        } catch (t: Throwable) {
            Log.e("NextTV", "setContent failed", t)
            throw t
        }
    }
}

private enum class Screen {
    Home, Player, Settings, Epg
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NextTvApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val prefs = remember { AppPreferences(appContext) }
    val repository = remember { ChannelRepository(prefs) }
    val epgRepository = remember { EpgRepository() }
    val dualPlayer = remember { DualPlayerManager(appContext) }
    val previewManager = remember { PreviewPlayerManager(appContext) }
    val webServer = remember { SourceWebServer() }
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var bootstrapped by remember { mutableStateOf(false) }
    var bootMessage by remember { mutableStateOf("正在启动…") }
    var fatalError by remember { mutableStateOf<String?>(null) }

    val channels by repository.channels.collectAsState()
    val dualState by dualPlayer.state.collectAsState()

    LaunchedEffect(Unit) {
        try {
            dualPlayer.initialize()
            bootMessage = "正在恢复播放源…"
            val result = repository.bootstrap()
            bootMessage = result.fold(
                onSuccess = { if (it > 0) "已恢复 $it 个频道" else "请添加播放源" },
                onFailure = { "恢复失败: ${it.message}" }
            )
            repository.findLastChannel()?.let { selectedChannel = it }
            prefs.getLastEpgUrl()?.let { url ->
                runCatching { epgRepository.loadFromUrl(url) }
            }
        } catch (t: Throwable) {
            Log.e("NextTV", "bootstrap failed", t)
            fatalError = t.message ?: t.javaClass.simpleName
        } finally {
            bootstrapped = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            dualPlayer.release()
            previewManager.release()
            webServer.stopServer()
        }
    }

    DisposableEffect(currentScreen) {
        if (currentScreen == Screen.Player) {
            previewManager.clearPreview()
        }
        onDispose { }
    }

    // 不用 TV Surface 作根布局，避免部分机型主题/焦点问题
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NextTvColors.Bg)
    ) {
        when {
            fatalError != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "启动异常: $fatalError",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NextTvColors.Danger
                    )
                }
            }
            !bootstrapped -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = bootMessage,
                        style = MaterialTheme.typography.headlineSmall,
                        color = NextTvColors.TextPrimary
                    )
                }
            }
            else -> when (currentScreen) {
                Screen.Home -> {
                    HomeScreen(
                        repository = repository,
                        epgRepository = epgRepository,
                        currentChannel = dualState.currentChannel,
                        previewManager = previewManager,
                        onChannelClick = { channel -> selectedChannel = channel },
                        onOpenPlayer = { currentScreen = Screen.Player },
                        onOpenSettings = { currentScreen = Screen.Settings },
                        onOpenEpg = { currentScreen = Screen.Epg }
                    )
                }
                Screen.Player -> {
                    PlayerScreen(
                        dualPlayer = dualPlayer,
                        channels = channels,
                        initialChannel = selectedChannel,
                        repository = repository,
                        onChannelChanged = { ch ->
                            selectedChannel = ch
                            scope.launch { repository.setLastChannel(ch) }
                        },
                        onBack = { currentScreen = Screen.Home }
                    )
                }
                Screen.Settings -> {
                    SettingsScreen(
                        repository = repository,
                        epgRepository = epgRepository,
                        webServer = webServer,
                        onBack = { currentScreen = Screen.Home }
                    )
                }
                Screen.Epg -> {
                    EpgScreen(
                        repository = repository,
                        epgRepository = epgRepository,
                        onChannelClick = { channel ->
                            selectedChannel = channel
                            scope.launch { repository.setLastChannel(channel) }
                            currentScreen = Screen.Player
                        },
                        onBack = { currentScreen = Screen.Home }
                    )
                }
            }
        }
    }
}
