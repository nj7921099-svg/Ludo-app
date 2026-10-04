package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.model.BluetoothConnectionState
import com.example.bluetooth.model.BluetoothStatusType
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
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
fun ConnectionStatusCard(
    connectionState: BluetoothConnectionState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onScanClick: () -> Unit,
    onMakeDiscoverableClick: () -> Unit,
    onRestartListenerClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onOpenProtocolSpecs: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (connectionState.status) {
        BluetoothStatusType.CONNECTED -> NeonGreen
        BluetoothStatusType.LISTENING -> NeonCyan
        BluetoothStatusType.CONNECTING, BluetoothStatusType.SCANNING -> NeonAmber
        BluetoothStatusType.PERMISSIONS_REQUIRED, BluetoothStatusType.ERROR -> NeonRed
        BluetoothStatusType.BLUETOOTH_OFF, BluetoothStatusType.DISCONNECTED -> Color(0xFF6B7280)
    }

    val statusIcon = when (connectionState.status) {
        BluetoothStatusType.CONNECTED -> Icons.Default.BluetoothConnected
        BluetoothStatusType.LISTENING -> Icons.Default.Bluetooth
        BluetoothStatusType.CONNECTING, BluetoothStatusType.SCANNING -> Icons.Default.BluetoothSearching
        BluetoothStatusType.BLUETOOTH_OFF, BluetoothStatusType.PERMISSIONS_REQUIRED -> Icons.Default.BluetoothDisabled
        else -> Icons.Default.Bluetooth
    }

    val statusTitle = when (connectionState.status) {
        BluetoothStatusType.CONNECTED -> "CONNECTED TO CONTROLLER"
        BluetoothStatusType.LISTENING -> "HOST LISTENING (READY FOR APP 2)"
        BluetoothStatusType.CONNECTING -> "CONNECTING TO APP 2..."
        BluetoothStatusType.SCANNING -> "SCANNING FOR CONTROLLER..."
        BluetoothStatusType.BLUETOOTH_OFF -> "BLUETOOTH IS OFF"
        BluetoothStatusType.PERMISSIONS_REQUIRED -> "BLUETOOTH PERMISSIONS NEEDED"
        BluetoothStatusType.ERROR -> "CONNECTION FAILED"
        BluetoothStatusType.DISCONNECTED -> "DISCONNECTED"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_status_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = NeonDarkCard),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(NeonPurple, NeonPurpleBright, NeonPurpleDeep)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Always visible, clickable to toggle expand/minimize
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleExpand() }
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Icon(
                        imageVector = statusIcon,
                        contentDescription = "Bluetooth Status",
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = if (connectionState.connectedDeviceName != null)
                                "Device: ${connectionState.connectedDeviceName}"
                            else
                                connectionState.statusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.testTag("toggle_connection_card_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Minimize connection section" else "Expand connection section",
                        tint = NeonPurpleBright
                    )
                }
            }

            // Expanded detail section (Can be hidden/minimized for pure game mode)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    // Technical details panel
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = NeonDarkCardElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262640)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "System: Host Engine (App 1)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "Protocol: v${connectionState.protocolVersion}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NeonPurpleBright
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "RFCOMM Server: ${if (connectionState.isServerListening) "LISTENING ON SPP/CUSTOM UUID" else "STOPPED"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (connectionState.isServerListening) NeonGreen else TextMuted
                                )
                            )

                            if (connectionState.connectedDeviceAddress != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Connected MAC: ${connectionState.connectedDeviceAddress}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }

                            connectionState.lastError?.let { err ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Notice: $err",
                                    style = MaterialTheme.typography.bodySmall.copy(color = NeonAmber)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Development & Connection Actions
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Make Discoverable
                        OutlinedButton(
                            onClick = onMakeDiscoverableClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("make_discoverable_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Make Discoverable")
                        }

                        // Scan / Connect
                        OutlinedButton(
                            onClick = onScanClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurpleBright),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurpleBright.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("scan_devices_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nearby & Paired")
                        }

                        if (connectionState.status == BluetoothStatusType.CONNECTED) {
                            OutlinedButton(
                                onClick = onDisconnectClick,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.6f)),
                                modifier = Modifier.testTag("disconnect_button")
                            ) {
                                Text("Disconnect")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onRestartListenerClick,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383858)),
                                modifier = Modifier.testTag("restart_server_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restart Server")
                            }
                        }

                        // App 2 Developer Protocol Specs
                        AssistChip(
                            onClick = onOpenProtocolSpecs,
                            label = { Text("App 2 Protocol", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = NeonPurpleBright,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = NeonDarkCardElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurpleDeep),
                            modifier = Modifier.testTag("protocol_specs_button")
                        )

                        // Live Logs
                        AssistChip(
                            onClick = onOpenLogs,
                            label = { Text("Live Logs", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FormatListBulleted,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(containerColor = NeonDarkCardElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262640)),
                            modifier = Modifier.testTag("live_logs_button")
                        )
                    }
                }
            }
        }
    }
}
