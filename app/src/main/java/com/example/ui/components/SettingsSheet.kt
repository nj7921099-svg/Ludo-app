package com.example.ui.components

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bluetooth.model.BluetoothConnectionState
import com.example.bluetooth.model.BluetoothStatusType
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonDarkBg
import com.example.ui.theme.NeonDarkCard
import com.example.ui.theme.NeonDarkCardElevated
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.NeonPurpleDeep
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    connectionState: BluetoothConnectionState,
    logs: List<String>,
    onScanClick: () -> Unit,
    onMakeDiscoverableClick: () -> Unit,
    onRestartListenerClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onOpenProtocolSpecs: () -> Unit,
    onClearLogs: () -> Unit,
    onResetGame: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Bluetooth", "Protocol", "Live Logs")

    val statusColor = when (connectionState.status) {
        BluetoothStatusType.CONNECTED -> NeonGreen
        BluetoothStatusType.LISTENING -> NeonCyan
        BluetoothStatusType.CONNECTING, BluetoothStatusType.SCANNING -> NeonAmber
        BluetoothStatusType.PERMISSIONS_REQUIRED, BluetoothStatusType.ERROR -> NeonRed
        BluetoothStatusType.BLUETOOTH_OFF, BluetoothStatusType.DISCONNECTED -> Color(0xFF6B7280)
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
                            text = "SETTINGS & BLUETOOTH",
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
                        // Bluetooth Tab
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
                                            text = when (connectionState.status) {
                                                BluetoothStatusType.CONNECTED -> "CONNECTED TO CONTROLLER"
                                                BluetoothStatusType.LISTENING -> "HOST LISTENING (READY FOR APP 2)"
                                                BluetoothStatusType.CONNECTING -> "CONNECTING..."
                                                BluetoothStatusType.SCANNING -> "SCANNING..."
                                                BluetoothStatusType.BLUETOOTH_OFF -> "BLUETOOTH OFF"
                                                BluetoothStatusType.PERMISSIONS_REQUIRED -> "PERMISSIONS NEEDED"
                                                else -> "DISCONNECTED"
                                            },
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = connectionState.statusMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )

                                    if (connectionState.connectedDeviceName != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Controller Device: ${connectionState.connectedDeviceName} (${connectionState.connectedDeviceAddress ?: ""})",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "CONNECTION CONTROLS",
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
                                    onClick = onMakeDiscoverableClick,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Make Discoverable")
                                }

                                OutlinedButton(
                                    onClick = onScanClick,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurpleBright.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Nearby & Paired")
                                }

                                if (connectionState.status == BluetoothStatusType.CONNECTED) {
                                    OutlinedButton(
                                        onClick = onDisconnectClick,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.6f))
                                    ) {
                                        Text("Disconnect")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onRestartListenerClick,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383858))
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Restart Server")
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
                                text = "App 2 / Controller Protocol Specs",
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
                                Text("Open Full Specs Sheet")
                            }

                            Text(
                                text = "Key Message Formats:\n• GAME_CONFIGURATION (Host -> Controller)\n• TURN_UPDATE (Clockwise turn update)\n• PROVIDE_NUMBER (Controller -> Host)\n• NUMBER_RESULT (Host -> Controller)",
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

                            LiveLogsContent(logs = logs)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveLogsContent(logs: List<String>) {
    androidx.compose.foundation.lazy.LazyColumn(
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
