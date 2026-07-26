package com.agent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    botToken: String,
    detectedChatId: String,
    apiKey: String,
    baseUrl: String,
    modelName: String,
    availableModels: List<String>,
    testLoading: Boolean,
    testResult: String?,
    onBotTokenChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onTestApi: () -> Unit,
    onSetupAccessibility: () -> Unit
) {
    var showBotToken by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // Telegram Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Telegram Bot", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedTextField(
                    value = botToken,
                    onValueChange = onBotTokenChange,
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showBotToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showBotToken = !showBotToken }) {
                            Icon(if (showBotToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                if (detectedChatId.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Chat ID: $detectedChatId", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else {
                    Text("Start Agent and message your bot on Telegram — Chat ID auto-detects.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // NVIDIA Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("NVIDIA AI (OpenAI Compatible)", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://integrate.api.nvidia.com/v1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                Text("Select Model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableModels) { model ->
                        val isSelected = model == modelName
                        FilterChip(
                            selected = isSelected,
                            onClick = { onModelChange(model) },
                            label = { Text(model, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }

                OutlinedTextField(
                    value = modelName,
                    onValueChange = onModelChange,
                    label = { Text("Or type model name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("meta/llama-3.1-405b-instruct") }
                )

                // Test API button + result
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onTestApi,
                        enabled = apiKey.isNotBlank() && !testLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (testLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Test API")
                    }
                }

                testResult?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (it.startsWith("✅"))
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(it, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                    }
                }

                Text("Enter API Key and tap 'Test API' to verify connection.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Accessibility Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Accessibility Service", style = MaterialTheme.typography.titleMedium)
                }

                Text("Required for phone control. Enable in system settings.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Button(onClick = onSetupAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Accessibility Settings")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
