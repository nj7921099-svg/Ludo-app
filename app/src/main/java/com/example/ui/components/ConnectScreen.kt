package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.NetworkConstants
import com.example.network.model.HostConnectionState
import com.example.network.model.NetworkConnectionState
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonDarkCard
import com.example.ui.theme.NeonDarkCardElevated
import com.example.ui.theme.NeonGreenBright
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ConnectScreen(
    connectionState: NetworkConnectionState,
    logs: List<String>,
    onTestConnection: () -> Unit,
    onRestartServer: () -> Unit,
    onDisconnectClient: () -> Unit,
    onSimulateNumberTest: (Int) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogs by remember { mutableStateOf(false) }

    val state = connectionState.state
    val isConnected = state == HostConnectionState.CONNECTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("connect_screen_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Overview Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0A18)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "CONNECT",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Wi-Fi Status Row
                StatusItemRow(
                    label = "Wi-Fi",
                    statusText = if (connectionState.isWifiAvailable) "Connected" else "Not Connected",
                    dotColor = if (connectionState.isWifiAvailable) NeonGreenBright else NeonRed,
                    subtext = connectionState.localIp?.let { "Host IP : ${connectionState.serverPort} ($it)" } ?: "No local IP address"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Ludo Host Server Row
                StatusItemRow(
                    label = "Ludo Host",
                    statusText = if (connectionState.isServerRunning) "ONLINE" else "OFFLINE",
                    dotColor = if (connectionState.isServerRunning) NeonGreenBright else NeonRed,
                    subtext = "TCP Port: ${connectionState.serverPort} • Service: ${NetworkConstants.NSD_SERVICE_NAME} (${NetworkConstants.NSD_SERVICE_TYPE})"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Controller Status Row
                StatusItemRow(
                    label = "Controller",
                    statusText = when (state) {
                        HostConnectionState.CONNECTED -> "CONNECTED"
                        HostConnectionState.VERIFYING -> "VERIFYING..."
                        HostConnectionState.SOCKET_CONNECTED -> "SOCKET OPEN"
                        else -> "Not Connected"
                    },
                    dotColor = when (state) {
                        HostConnectionState.CONNECTED -> NeonGreenBright
                        HostConnectionState.VERIFYING, HostConnectionState.SOCKET_CONNECTED -> NeonAmber
                        else -> Color(0xFF6B7280)
                    },
                    subtext = when (state) {
                        HostConnectionState.CONNECTED -> "Device: ${connectionState.connectedClientDeviceName ?: connectionState.connectedClientIp ?: "Controller Phone"}"
                        HostConnectionState.VERIFYING -> "Verifying Handshake & Ping/Pong..."
                        else -> "[ Waiting for Controller to connect to ${connectionState.localIp ?: "Host"}:${connectionState.serverPort} ]"
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF262640))
                Spacer(modifier = Modifier.height(14.dp))

                // Prominent Host IP : 8888 & Manual IP info
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141026),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "AUTHORITATIVE HOST ENDPOINT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonPurpleBright
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${connectionState.localIp ?: "Waiting for Wi-Fi"} : ${connectionState.serverPort}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Controller manual fallback port: ${NetworkConstants.LUDO_TCP_PORT}",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                    }
                }

                if (isConnected) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Status: Connection Verified • Protocol: v1 • Ping: Successful",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NeonGreenBright,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    if (connectionState.lastPingRttMs != null) {
                        Text(
                            text = "Round-Trip Latency: ${connectionState.lastPingRttMs} ms",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onTestConnection,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                        modifier = Modifier.testTag("test_connection_button")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection (Ping/Pong)")
                    }
                }
            }
        }

        // Section 2: Connection Diagnostics
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0A18)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262640))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONNECTION DIAGNOSTICS",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonPurpleBright
                        )
                    )

                    Text(
                        text = if (isConnected) "STATUS: VERIFIED" else "STATUS: PENDING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isConnected) NeonGreenBright else NeonAmber
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                connectionState.diagnostics.forEach { diag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (diag.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (diag.isSuccess) NeonGreenBright else Color(0xFF6B7280),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = diag.title,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (diag.isSuccess) TextPrimary else TextSecondary
                                )
                            )
                            if (diag.details.isNotBlank()) {
                                Text(
                                    text = diag.details,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (diag.isSuccess) Color(0xFFA7F3D0) else Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }

                if (connectionState.lastError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✕ ${connectionState.lastError}",
                            color = NeonRed,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Section 3: Ludo Number Communication Test (Real end-to-end verification)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0A18)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262640))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "NUMBER COMMUNICATION TEST",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = NeonPurpleBright
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Real TCP verification: NUMBER_SELECTION → ACK → NUMBER_RESULT (5-second timeout protection). Select number to test:",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..6).forEach { num ->
                        Surface(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSimulateNumberTest(num)
                                }
                                .testTag("test_number_button_$num"),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF161226),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$num",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                if (connectionState.numberTestStatus != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val isPass = connectionState.numberTestStatus.startsWith("PASS")
                    val isFail = connectionState.numberTestStatus.startsWith("FAIL")
                    val borderColor = when {
                        isPass -> NeonGreenBright
                        isFail -> NeonRed
                        else -> NeonAmber
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = borderColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = connectionState.numberTestStatus,
                            color = borderColor,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        // Section 4: Server Actions & Developer Logs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRestartServer,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383858))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restart Server", fontSize = 12.sp)
            }

            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnectClient,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.6f))
                ) {
                    Text("Disconnect", fontSize = 12.sp)
                }
            }

            OutlinedButton(
                onClick = { showLogs = !showLogs },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurpleBright)
            ) {
                Text(if (showLogs) "Hide Logs" else "Logs (${logs.size})", fontSize = 12.sp)
            }
        }

        // Logs Viewer
        if (showLogs) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF06050C)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262640))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Network Packets", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall.copy(color = NeonPurpleBright),
                            modifier = Modifier.clickable { onClearLogs() }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        reverseLayout = true
                    ) {
                        items(logs.reversed()) { logEntry ->
                            Text(
                                text = logEntry,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = when {
                                    logEntry.contains("[RX]") -> NeonCyan
                                    logEntry.contains("[TX]") -> NeonPurpleBright
                                    logEntry.contains("ERROR") || logEntry.contains("Fail") -> NeonRed
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

@Composable
private fun StatusItemRow(
    label: String,
    statusText: String,
    dotColor: Color,
    subtext: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = dotColor
                    )
                )
            }
        }

        if (subtext.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
            )
        }
    }
}
