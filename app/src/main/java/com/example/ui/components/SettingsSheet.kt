package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.network.model.HostConnectionState
import com.example.network.model.NetworkConnectionState
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonDarkBg
import com.example.ui.theme.NeonDarkCard
import com.example.ui.theme.NeonDarkCardElevated
import com.example.ui.theme.NeonGreenBright
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    connectionState: NetworkConnectionState,
    logs: List<String>,
    onRestartServerClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onOpenProtocolSpecs: () -> Unit,
    onOpenConnectTab: () -> Unit,
    onClearLogs: () -> Unit,
    onResetGame: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Host & Wi-Fi", "Protocol", "Live Logs")

    val state = connectionState.state
    val statusColor = when (state) {
        HostConnectionState.CONNECTED -> NeonGreenBright
        HostConnectionState.HOST_READY -> Color(0xFF38BDF8)
        HostConnectionState.VERIFYING, HostConnectionState.SOCKET_CONNECTED -> NeonAmber
        HostConnectionState.NETWORK_UNAVAILABLE, HostConnectionState.ERROR -> NeonRed
        else -> Color(0xFF6B7280)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(640.dp)
                .testTag("settings_dialog"),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = NeonPurpleBright,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SETTINGS & NETWORK",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_settings_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = NeonDarkCardElevated,
                    contentColor = NeonPurpleBright,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonPurpleBright
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) TextPrimary else TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Contents
                when (selectedTab) {
                    0 -> {
                        // Host & Wi-Fi Tab
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Current Status Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = NeonDarkCardElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282845)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(statusColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = connectionState.statusTitle,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = connectionState.displayStatusMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )

                                    if (connectionState.localIp != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Local Host: ${connectionState.localIp}:${connectionState.serverPort} (NSD: LudoHost)",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        )
                                    }
                                }
                            }

                            // Sound & Audio FX Toggle using soundon / soundoff assets
                            var isSoundEnabled by remember { mutableStateOf(true) }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = NeonDarkCardElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282845)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSoundEnabled = !isSoundEnabled }
                                    .testTag("sound_toggle_row")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            painter = painterResource(id = if (isSoundEnabled) R.drawable.soundon else R.drawable.soundoff),
                                            contentDescription = "Sound Effect",
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Sound & Audio Effects",
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (isSoundEnabled) "Sound FX Enabled" else "Sound FX Muted",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isSoundEnabled) "ON" else "OFF",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSoundEnabled) NeonGreenBright else TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Text(
                                text = "HOST SERVER CONTROLS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NeonPurpleBright
                                )
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onOpenConnectTab()
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Connect Tab")
                                }

                                OutlinedButton(
                                    onClick = onRestartServerClick,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383858))
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restart Host Server")
                                }

                                if (state == HostConnectionState.CONNECTED) {
                                    OutlinedButton(
                                        onClick = onDisconnectClick,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.6f))
                                    ) {
                                        Text("Disconnect Controller")
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF262640), modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "GAME RESET",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NeonAmber
                                )
                            )

                            OutlinedButton(
                                onClick = {
                                    onResetGame()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(alpha = 0.6f))
                            ) {
                                Text("Reset Game to Setup")
                            }
                        }
                    }

                    1 -> {
                        // Protocol Tab
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Wi-Fi TCP & NSD Protocol Specs",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )

                            OutlinedButton(
                                onClick = onOpenProtocolSpecs,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurpleBright)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Full Protocol Specs")
                            }

                            Text(
                                text = "Key Message Formats:\n• HANDSHAKE / HANDSHAKE_ACK (role, v1)\n• PING / PONG (RTT calculation)\n• NUMBER_SELECTION (Controller -> Host)\n• ACK (Host -> Controller)\n• NUMBER_RESULT (Host -> Controller)\n• CONFIG / STATE_SYNC",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    2 -> {
                        // Live Logs Tab
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Packets (${logs.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                )

                                OutlinedButton(
                                    onClick = onClearLogs,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Clear", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                reverseLayout = true
                            ) {
                                items(logs.reversed()) { logEntry ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            logEntry.contains("[RX]") -> NeonCyan.copy(alpha = 0.15f)
                                            logEntry.contains("[TX]") -> NeonPurple.copy(alpha = 0.15f)
                                            logEntry.contains("ERROR") || logEntry.contains("fail") -> NeonRed.copy(alpha = 0.15f)
                                            else -> NeonDarkCardElevated
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = logEntry,
                                            modifier = Modifier.padding(6.dp),
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = when {
                                                logEntry.contains("[RX]") -> NeonCyan
                                                logEntry.contains("[TX]") -> NeonPurpleBright
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
    }
}
