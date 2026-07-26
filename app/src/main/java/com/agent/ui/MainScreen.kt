package com.agent.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    actionLog: List<String>,
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
    apiConnected: Boolean,
    accessibilityEnabled: Boolean,
    availableModels: List<String>,
    modelsLoading: Boolean,
    modelsError: String,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBotTokenChange: (String) -> Unit,
    onTestApi: () -> Unit,
    onFetchModels: () -> Unit,
    onStartBot: () -> Unit,
    onStopBot: () -> Unit,
    onEnableAccessibility: () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab == 0, onClick = { onTabChange(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") }, label = { Text("Home") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { onTabChange(1) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") }, label = { Text("Chat") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { onTabChange(2) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(apiConnected, accessibilityEnabled, botStatus)
                1 -> ChatScreen(messages, chatInput, chatLoading, actionLog, onChatInputChange, onSendMessage, onClearChat)
                2 -> SettingsScreen(
                    apiKey, baseUrl, model, botToken, detectedChatId,
                    testResult, testLoading, botStatus,
                    apiConnected, accessibilityEnabled,
                    availableModels, modelsLoading, modelsError,
                    onApiKeyChange, onBaseUrlChange, onModelChange, onBotTokenChange,
                    onTestApi, onFetchModels, onStartBot, onStopBot, onEnableAccessibility
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(apiConnected: Boolean, accessibilityEnabled: Boolean, botStatus: String) {
    val ctx = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("AGENT", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("AI Phone Assistant", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            StatusRow("API Connection", apiConnected)
            StatusRow("Phone Control", accessibilityEnabled)
            StatusRow("Telegram Bot", botStatus == "Running")
            Spacer(Modifier.height(32.dp))
            if (!apiConnected || !accessibilityEnabled) {
                Text("Setup needed:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (!apiConnected) Text("1. Add NVIDIA API key in Settings → Test API", style = MaterialTheme.typography.bodyMedium)
                if (!accessibilityEnabled) Text("2. Enable Accessibility in Settings", style = MaterialTheme.typography.bodyMedium)
                Text("3. Chat or use Telegram to control phone", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }) { Text("Open Accessibility Settings") }
            } else {
                Text("✓ Ready to go!\nChat with AGENT or send Telegram commands", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(10.dp)
        ) {}
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(if (ok) "Active" else "Inactive", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
    }
}
