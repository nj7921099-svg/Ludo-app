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
import com.example.bluetooth.model.BluetoothConnectionState
import com.example.bluetooth.model.BluetoothStatusType
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TopControllerBar(
    connectionState: BluetoothConnectionState,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusDotColor = when (connectionState.status) {
        BluetoothStatusType.CONNECTED -> NeonGreen
        BluetoothStatusType.LISTENING -> NeonCyan
        BluetoothStatusType.CONNECTING, BluetoothStatusType.SCANNING -> NeonAmber
        BluetoothStatusType.PERMISSIONS_REQUIRED, BluetoothStatusType.ERROR -> NeonRed
        else -> Color(0xFF6B7280)
    }

    val statusSubtitle = when (connectionState.status) {
        BluetoothStatusType.CONNECTED -> "Connected"
        BluetoothStatusType.LISTENING -> "Host Listening"
        BluetoothStatusType.CONNECTING -> "Connecting..."
        BluetoothStatusType.SCANNING -> "Searching..."
        BluetoothStatusType.BLUETOOTH_OFF -> "Bluetooth Off"
        BluetoothStatusType.PERMISSIONS_REQUIRED -> "Perms Needed"
        else -> "Disconnected"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Card: CONTROLLER status
        Card(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onSettingsClick() }
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
                // Top line: CONTROLLER
                Text(
                    text = "CONTROLLER",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp,
                        color = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Bottom line: Status on left, "Bluetooth ●" on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (connectionState.connectedDeviceName != null)
                            "Connected (${connectionState.connectedDeviceName})"
                        else
                            statusSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Bluetooth",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
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

        // Right Button: Sliders / Settings Button ("setting btn jo top right corner m h")
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
