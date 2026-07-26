package com.agent.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
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
    var showKey by remember { mutableStateOf(false) }
    var showBotToken by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // Status card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip("API", apiConnected)
                StatusChip("Accessibility", accessibilityEnabled)
                StatusChip("Telegram", botStatus == "Running")
            }
        }

        // NVIDIA API card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("NVIDIA API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) PasswordVisualTransformation() else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                    },
                    placeholder = { Text("nvapi-...") }
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://integrate.api.nvidia.com/v1") }
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTestApi, enabled = !testLoading && apiKey.isNotBlank()) {
                        if (testLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Test API")
                    }
                    OutlinedButton(onClick = onFetchModels, enabled = !modelsLoading && apiKey.isNotBlank()) {
                        if (modelsLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Fetch Models")
                    }
                }

                if (testResult.isNotBlank()) {
                    val isError = testResult.startsWith("❌")
                    Text(
                        text = testResult,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (modelsError.isNotBlank()) {
                    Text(modelsError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Model selection
                Text("Model:", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChange,
                    label = { Text("Model name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (availableModels.isNotEmpty()) {
                    Text("Tap to select:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableModels, key = { it }) { m ->
                            AssistChip(
                                onClick = { onModelChange(m) },
                                label = { Text(m.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall) },
                                colors = if (m == model)
                                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary, labelColor = MaterialTheme.colorScheme.onPrimary)
                                else AssistChipDefaults.assistChipColors()
                            )
                        }
                    }
                }
            }
        }

        // Accessibility card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Accessibility Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (accessibilityEnabled) "Enabled — phone control active" else "Disabled — needed to control phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (accessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                if (!accessibilityEnabled) {
                    Button(onClick = onEnableAccessibility) { Text("Enable") }
                }
            }
        }

        // Telegram card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Telegram Bot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = botToken,
                    onValueChange = onBotTokenChange,
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("123456:ABC-DEF...") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartBot, enabled = botToken.isNotBlank() && botStatus != "Running") { Text("Start") }
                    OutlinedButton(onClick = onStopBot, enabled = botStatus == "Running") { Text("Stop") }
                }

                Text("Status: $botStatus", style = MaterialTheme.typography.bodySmall)
                if (detectedChatId != null) {
                    Text("Chat ID: $detectedChatId", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatusChip(label: String, connected: Boolean) {
    val color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = color, modifier = Modifier.size(8.dp)) {}
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
