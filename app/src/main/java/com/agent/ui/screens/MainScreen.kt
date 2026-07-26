package com.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(
    serviceActive: Boolean,
    agentRunning: Boolean,
    logs: List<String>,
    onStartAgent: () -> Unit,
    onStopAgent: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AGENT", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("AI Phone Controller", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefreshStatus) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        // Status Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "Service",
                active = serviceActive,
                icon = Icons.Default.TouchApp,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Agent",
                active = agentRunning,
                icon = Icons.Default.SmartToy,
                modifier = Modifier.weight(1f)
            )
        }

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (agentRunning) {
                Button(
                    onClick = onStopAgent,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Agent")
                }
            } else {
                Button(
                    onClick = onStartAgent,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Agent")
                }
            }

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Settings")
            }
        }

        // Logs Section
        Text("Activity Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No activity yet.\nConfigure settings and start the agent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs.reversed()) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(
                if (active) "Active" else "Inactive",
                style = MaterialTheme.typography.titleSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
