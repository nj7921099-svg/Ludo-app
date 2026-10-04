package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.model.HostConnectionState
import com.example.network.model.NetworkConnectionState
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonGreenBright
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary

/**
 * Global Connection Indicator positioned at the top-left corner of all screens.
 *
 * Rules:
 * - Shared across all tabs from the central ConnectionState source.
 * - Displays:
 *     🟢 Connected
 *     🔴 Not Connected (or Disconnected / Error)
 *     🟡 Connecting (or Verifying / Starting)
 * - Tapping it opens the Connect tab!
 */
@Composable
fun GlobalConnectionIndicator(
    connectionState: NetworkConnectionState,
    onIndicatorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = connectionState.state

    val isConnected = state == HostConnectionState.CONNECTED
    val isConnecting = state in listOf(
        HostConnectionState.SOCKET_CONNECTED,
        HostConnectionState.VERIFYING,
        HostConnectionState.CONNECTING,
        HostConnectionState.STARTING_HOST,
        HostConnectionState.HOST_READY
    )

    val (dotColor, labelText) = when {
        isConnected -> Pair(NeonGreenBright, "Connected")
        state == HostConnectionState.HOST_READY -> Pair(Color(0xFF38BDF8), "Host Ready")
        state == HostConnectionState.VERIFYING || state == HostConnectionState.SOCKET_CONNECTED -> Pair(NeonAmber, "Verifying...")
        isConnecting -> Pair(NeonAmber, "Connecting...")
        else -> Pair(NeonRed, "Not Connected")
    }

    // Breathing pulse animation for connecting / active state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = if (isConnecting) 0.4f else 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnecting) 600 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onIndicatorClick() }
            .testTag("global_connection_indicator"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC0D0A1A),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            dotColor.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = dotAlpha))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TextPrimary
                )
            )
        }
    }
}
