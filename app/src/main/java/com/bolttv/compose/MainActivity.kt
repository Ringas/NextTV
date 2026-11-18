package com.bolttv.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoltComposeTheme {
                BoltApp()
            }
        }
    }
}

private enum class Screen {
    Home, Player, Settings, Epg
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BoltApp() {
    val context = LocalContext.current
    val repository = remember { ChannelRepository() }
    val epgRepository = remember { EpgRepository() }
    val dualPlayer = remember { DualPlayerManager(context) }
    val previewManager = remember { PreviewPlayerManager(context) }
    val webServer = remember { SourceWebServer() }

    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    val channels by repository.channels.collectAsState()
    val dualState by dualPlayer.state.collectAsState()

    DisposableEffect(Unit) {
        dualPlayer.initialize()
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

    Surface(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
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
                        currentScreen = Screen.Player
                    },
                    onBack = { currentScreen = Screen.Home }
                )
            }
        }
    }
}
