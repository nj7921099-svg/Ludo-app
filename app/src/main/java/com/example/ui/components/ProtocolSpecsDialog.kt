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
import com.example.bluetooth.BluetoothConstants
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
                            text = "Controller Protocol Specs",
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
                        text = "IMPORTANT: Ludo Game (App 1) is the SOLE turn authority. Controller (App 2) can send a number for ANY box at ANY time.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonPurpleBright
                        )
                    )

                    SpecItem(
                        title = "1. Service UUIDs & Framing",
                        code = "App Custom UUID:\n${BluetoothConstants.APP_SERVICE_UUID}\n\nStandard SPP Fallback:\n${BluetoothConstants.STANDARD_SPP_UUID}\n\nDelimiter: Newline (\\n) JSON streams over RFCOMM."
                    )

                    SpecItem(
                        title = "2. Controller Command (App 2 -> App 1)",
                        code = """
                        {
                          "type": "CONTROLLER_COMMAND",
                          "commandId": "1001",
                          "boxId": 4,
                          "value": 6
                        }
                        // Note: Can be sent for ANY box at ANY time!
                        // App 1 stores it specifically for R4.
                        // It does NOT affect current turn until R4's turn arrives.
                        """.trimIndent()
                    )

                    SpecItem(
                        title = "3. Command Acknowledgement (App 1 -> App 2)",
                        code = """
                        {
                          "type": "COMMAND_ACK",
                          "commandId": "1001",
                          "boxId": 4,
                          "accepted": true,
                          "reason": "Stored specifically for R4"
                        }
                        """.trimIndent()
                    )

                    SpecItem(
                        title = "4. Number Revealed (App 1 -> App 2)",
                        code = """
                        {
                          "type": "NUMBER_RESULT",
                          "boxId": 4,
                          "turnId": 3,
                          "value": 6,
                          "source": "REMOTE",
                          "commandId": "1001"
                        }
                        // Note: Sent ONLY when R4's turn arrives and user taps R4.
                        // Once revealed, command 1001 is consumed and cleared!
                        """.trimIndent()
                    )

                    SpecItem(
                        title = "5. Game Configuration (App 1 -> App 2)",
                        code = """
                        {
                          "type": "GAME_CONFIGURATION",
                          "boxCount": 5,
                          "turnId": 1,
                          "activeBoxId": 1,
                          "isGameStarted": true
                        }
                        """.trimIndent()
                    )

                    SpecItem(
                        title = "6. Clockwise Turn Update (App 1 -> App 2)",
                        code = """
                        {
                          "type": "TURN_UPDATE",
                          "turnId": 2,
                          "activeBoxId": 2,
                          "previousBoxId": 1,
                          "previousResult": 4
                        }
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
