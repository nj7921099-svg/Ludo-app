package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IntegrationInstructions
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.network.NetworkConstants
import com.example.ui.theme.NeonDarkCard
import com.example.ui.theme.NeonDarkCardElevated
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ProtocolSpecsDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("protocol_specs_dialog"),
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
                            imageVector = Icons.Default.IntegrationInstructions,
                            contentDescription = null,
                            tint = NeonPurpleBright,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wi-Fi Protocol Specs",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close dialog", tint = TextSecondary)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color(0xFF262640)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Ludo-app = Wi-Fi HOST / SERVER (Port ${NetworkConstants.LUDO_TCP_PORT})\nController-BT = Wi-Fi CONTROLLER / CLIENT\nFraming: Newline-delimited JSON (\\n)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonPurpleBright
                        )
                    )

                    SpecItem(
                        title = "1. Unified TCP Port & NSD Discovery",
                        code = "Authoritative TCP Port: ${NetworkConstants.LUDO_TCP_PORT}\nManual Fallback Endpoint: <Host_IP>:${NetworkConstants.LUDO_TCP_PORT}\nNSD Service Name: ${NetworkConstants.NSD_SERVICE_NAME}\nNSD Service Type: ${NetworkConstants.NSD_SERVICE_TYPE}\nController auto-discovers Ludo Host via mDNS or connects to Host IP:${NetworkConstants.LUDO_TCP_PORT}."
                    )

                    SpecItem(
                        title = "2. Handshake (Controller -> Host)",
                        code = """
                        {
                          "type": "HANDSHAKE",
                          "protocolVersion": 1,
                          "role": "CONTROLLER",
                          "deviceName": "Controller Phone",
                          "requestId": "uuid-1",
                          "timestamp": 123456789
                        }
                        """.trimIndent()
                    )

                    SpecItem(
                        title = "3. Handshake ACK (Host -> Controller)",
                        code = """
                        {
                          "type": "HANDSHAKE_ACK",
                          "protocolVersion": 1,
                          "role": "LUDO_HOST",
                          "status": "OK",
                          "deviceName": "Ludo Phone",
                          "requestId": "uuid-1",
                          "timestamp": 123456790
                        }
                        """.trimIndent()
                    )

                    SpecItem(
                        title = "4. Number Selection (Controller -> Host)",
                        code = """
                        {
                          "type": "NUMBER_SELECTION",
                          "value": 5,
                          "boxId": 4,
                          "requestId": "req-999",
                          "timestamp": 123456800
                        }
                        // Controller can send for any box at any time!
                        """.trimIndent()
                    )

                    SpecItem(
                        title = "5. ACK (Host -> Controller)",
                        code = """
                        {
                          "type": "ACK",
                          "requestId": "req-999",
                          "status": "OK",
                          "reason": "Stored pending for R4",
                          "timestamp": 123456801
                        }
                        """.trimIndent()
                    )

                    SpecItem(
                        title = "6. Number Result (Host -> Controller)",
                        code = """
                        {
                          "type": "NUMBER_RESULT",
                          "boxId": 4,
                          "turnId": 3,
                          "value": 5,
                          "source": "REMOTE",
                          "requestId": "req-999",
                          "timestamp": 123456810
                        }
                        // Sent with matching requestId to verify end-to-end delivery!
                        """.trimIndent()
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecItem(
    title: String,
    code: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = NeonPurpleBright
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = NeonDarkCardElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262640)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = code,
                modifier = Modifier.padding(10.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
