package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.BoxState
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenBright
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BoxCard(
    box: BoxState,
    onBoxTap: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 145.dp,
    cardHeight: Dp = 145.dp
) {
    val isActive = box.isActiveTurn

    // Breathing glow animation for the active box's GREEN border
    val infiniteTransition = rememberInfiniteTransition(label = "active_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1.0f,
        animationSpec = tween(180),
        label = "card_scale"
    )

    val borderColor = if (isActive) {
        NeonGreenBright.copy(alpha = pulseAlpha)
    } else {
        NeonPurple.copy(alpha = 0.85f)
    }

    val borderWidth = if (isActive) 2.5.dp else 1.5.dp

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .scale(scaleAnim)
            .testTag("box_card_${box.boxId}"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Main rounded card
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable(enabled = isActive) {
                    onBoxTap()
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF0F1918) else Color(0xFF121020)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // R1 / R2 / R3 label
                Text(
                    text = box.label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.testTag("box_label_${box.boxId}")
                )

                Spacer(modifier = Modifier.height(3.dp))

                // [ ACTIVE ] or [ WAITING ] or [ REVEALED ]
                Text(
                    text = when {
                        isActive -> "[ ACTIVE ]"
                        box.isRolled -> "[ REVEALED ]"
                        else -> "[ WAITING ]"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isActive) NeonGreenBright else Color(0xFFA89BC7),
                        letterSpacing = 0.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                // [ ? ] or [ 6 ]
                Text(
                    text = if (box.currentValue != null) "[ ${box.currentValue} ]" else "[ ? ]",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        color = if (box.currentValue != null) {
                            if (isActive) NeonGreenBright else NeonPurpleBright
                        } else Color(0xFF887EA6)
                    ),
                    modifier = Modifier.testTag("box_value_${box.boxId}")
                )

                if (isActive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (box.hasPendingApp2Value) "Tap to reveal" else "Tap to reveal",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = NeonGreenBright.copy(alpha = 0.85f)
                        )
                    )
                }
            }
        }

        // Attached "YOUR TURN" badge at top edge of active box
        if (isActive) {
            Surface(
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                color = NeonGreenBright,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 0.dp)
                    .testTag("your_turn_badge_${box.boxId}")
            ) {
                Text(
                    text = "YOUR TURN",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        color = Color(0xFF031E12),
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}
