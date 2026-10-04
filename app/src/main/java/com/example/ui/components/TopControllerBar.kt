package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.model.HostConnectionState
import com.example.network.model.NetworkConnectionState
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreenBright
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TopControllerBar(
    connectionState: NetworkConnectionState,
    onStatusCardClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = connectionState.state
    val isConnected = state == HostConnectionState.CONNECTED

    val statusDotColor = when (state) {
        HostConnectionState.CONNECTED -> NeonGreenBright
        HostConnectionState.HOST_READY -> Color(0xFF38BDF8)
        HostConnectionState.VERIFYING, HostConnectionState.SOCKET_CONNECTED -> NeonAmber
        HostConnectionState.NETWORK_UNAVAILABLE, HostConnectionState.ERROR -> NeonRed
        else -> Color(0xFF6B7280)
    }

    val statusSubtitle = when (state) {
        HostConnectionState.CONNECTED -> "Controller: ${connectionState.connectedClientDeviceName ?: connectionState.connectedClientIp ?: "Connected"}"
        HostConnectionState.VERIFYING -> "Verifying Handshake..."
        HostConnectionState.SOCKET_CONNECTED -> "Socket Open"
        HostConnectionState.HOST_READY -> "Host Ready (${connectionState.localIp ?: "Listening"})"
        HostConnectionState.NETWORK_UNAVAILABLE -> "Wi-Fi Hotspot Needed"
        HostConnectionState.ERROR -> "Network Error"
        else -> "Awaiting Controller"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Card: Wi-Fi Host & Controller status (clickable to open Connect tab)
        Card(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onStatusCardClick() }
                .testTag("controller_status_header_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0A18)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Top line: LUDO HOST & CONTROLLER
                Text(
                    text = if (isConnected) "CONTROLLER CONNECTED" else "LUDO WI-FI HOST",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp,
                        color = if (isConnected) NeonGreenBright else TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Bottom line: Status on left, "Wi-Fi ●" on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Wi-Fi",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                    }
                }
            }
        }

        // Right Button: Settings Button
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF141026))
                .border(
                    width = 1.5.dp,
                    color = NeonPurple,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable {
                    onSettingsClick()
                }
                .testTag("top_right_settings_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Settings",
                tint = Color(0xFFD8B4FE),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
