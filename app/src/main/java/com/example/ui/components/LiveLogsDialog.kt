package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonDarkCard
import com.example.ui.theme.NeonDarkCardElevated
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LiveLogsDialog(
    logs: List<String>,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("live_logs_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeonDarkCard),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Network Live Logs",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )

                    Row {
                        IconButton(onClick = onClearLogs) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = TextSecondary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close dialog", tint = TextSecondary)
                        }
                    }
                }

                Text(
                    text = "Live TCP network packets between Host (App 1) & Controller (App 2):",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color(0xFF262640)
                )

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No communication events recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        reverseLayout = true
                    ) {
                        items(logs.reversed()) { logEntry ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    logEntry.contains("[RX]") -> NeonCyan.copy(alpha = 0.15f)
                                    logEntry.contains("[TX]") -> NeonPurple.copy(alpha = 0.15f)
                                    logEntry.contains("ERROR") || logEntry.contains("fail") -> NeonRed.copy(alpha = 0.15f)
                                    else -> NeonDarkCardElevated
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        logEntry.contains("[RX]") -> NeonCyan.copy(alpha = 0.4f)
                                        logEntry.contains("[TX]") -> NeonPurple.copy(alpha = 0.4f)
                                        logEntry.contains("ERROR") || logEntry.contains("fail") -> NeonRed.copy(alpha = 0.4f)
                                        else -> Color(0xFF262640)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = logEntry,
                                    modifier = Modifier.padding(8.dp),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        logEntry.contains("[RX]") -> NeonCyan
                                        logEntry.contains("[TX]") -> NeonPurple
                                        logEntry.contains("ERROR") || logEntry.contains("fail") -> NeonRed
                                        else -> TextPrimary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
