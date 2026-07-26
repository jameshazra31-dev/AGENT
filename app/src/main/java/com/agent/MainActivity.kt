package com.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agent.ui.AgentViewModel
import com.agent.ui.screens.MainScreen
import com.agent.ui.screens.SettingsScreen
import com.agent.ui.theme.AgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentTheme {
                AgentMain()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentMain() {
    val viewModel: AgentViewModel = viewModel()
    var selectedTab by remember { mutableIntStateOf(0) }

    val botToken by viewModel.botToken.collectAsState()
    val chatId by viewModel.chatId.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val modelName by viewModel.modelName.collectAsState()
    val serviceStatus by viewModel.serviceStatus.collectAsState()
    val agentRunning by viewModel.isRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> MainScreen(
                    serviceActive = serviceStatus,
                    agentRunning = agentRunning,
                    logs = logs,
                    onStartAgent = { viewModel.startAgent() },
                    onStopAgent = { viewModel.stopAgent() },
                    onOpenSettings = { selectedTab = 1 },
                    onRefreshStatus = { viewModel.checkServiceStatus() }
                )
                1 -> SettingsScreen(
                    botToken = botToken,
                    chatId = chatId,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    modelName = modelName,
                    onBotTokenChange = viewModel::updateBotToken,
                    onChatIdChange = viewModel::updateChatId,
                    onApiKeyChange = viewModel::updateApiKey,
                    onBaseUrlChange = viewModel::updateBaseUrl,
                    onModelChange = viewModel::updateModel,
                    onSetupAccessibility = viewModel::setupAccessibilityService
                )
            }
        }
    }
}
