package com.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    messages: List<AgentViewModel.ChatMessageUi>,
    chatLoading: Boolean,
    onSendMessage: () -> Unit,
    onClearChat: () -> Unit,
    apiKey: String,
    baseUrl: String,
    model: String,
    botToken: String,
    detectedChatId: Long?,
    testResult: String,
    testLoading: Boolean,
    botStatus: String,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBotTokenChange: (String) -> Unit,
    onTestApi: () -> Unit,
    onStartBot: () -> Unit,
    onStopBot: () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { onTabChange(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onTabChange(1) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onTabChange(2) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> ChatScreen(
                    messages = messages,
                    chatInput = chatInput,
                    chatLoading = chatLoading,
                    onInputChange = onChatInputChange,
                    onSend = onSendMessage,
                    onClear = onClearChat
                )
                2 -> SettingsScreen(
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    botToken = botToken,
                    detectedChatId = detectedChatId,
                    testResult = testResult,
                    testLoading = testLoading,
                    botStatus = botStatus,
                    onApiKeyChange = onApiKeyChange,
                    onBaseUrlChange = onBaseUrlChange,
                    onModelChange = onModelChange,
                    onBotTokenChange = onBotTokenChange,
                    onTestApi = onTestApi,
                    onStartBot = onStartBot,
                    onStopBot = onStopBot
                )
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AGENT",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "AI Phone Assistant",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "• Control your phone with AI\n• Chat with NVIDIA AI models\n• Remote control via Telegram",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Start by configuring your API key in Settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
