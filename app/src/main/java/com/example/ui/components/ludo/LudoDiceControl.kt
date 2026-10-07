package com.example.ui.components.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.TurnPhase
import com.example.ui.components.DiceFace
import kotlinx.coroutines.delay

/**
 * Modern interactive dice component for rolling in Ludo with tumbling animation.
 * Fully cancellation-safe and optimized for real-device frame rates.
 */
@Composable
fun LudoDiceControl(
    diceValue: Int?,
    turnPhase: TurnPhase,
    activeColor: LudoColor,
    onDiceClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMoveLocked: Boolean = false,
    size: Dp = 70.dp
) {
    val isRollable = (turnPhase == TurnPhase.WAITING_FOR_DICE_ROLL) && !isMoveLocked
    val primaryColor = LudoThemeColors.getPrimaryColor(activeColor)

    // Pulsing scale ONLY when ready to roll (saves GPU/CPU when waiting for moves)
    val pulseScale: Float
    if (isRollable) {
        val infiniteTransition = rememberInfiniteTransition(label = "dice_pulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dice_scale"
        )
        pulseScale = animatedScale
    } else {
        pulseScale = 1.0f
    }

    // Dynamic tumble animation states
    val rotationAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(1f) }
    var displayedValue by remember { mutableStateOf(diceValue) }
    var isRollingVisual by remember { mutableStateOf(false) }

    // Synchronize and animate whenever authoritative diceValue changes or rolls
    LaunchedEffect(diceValue, turnPhase) {
        if (diceValue != null && turnPhase == TurnPhase.WAITING_FOR_TOKEN_SELECTION) {
            try {
                isRollingVisual = true
                // Snappy 3-stage visual tumble (180ms total)
                for (i in 1..3) {
                    displayedValue = ((i * 2 + diceValue) % 6) + 1
                    rotationAnim.animateTo(
                        targetValue = if (i % 2 == 0) 12f else -12f,
                        animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                    )
                }
                rotationAnim.animateTo(0f, animationSpec = tween(durationMillis = 40))
                displayedValue = diceValue // Authoritative final value
                scaleAnim.snapTo(1.12f)
                scaleAnim.animateTo(1.0f, animationSpec = spring(dampingRatio = 0.65f))
            } finally {
                // Guarantee authoritative settlement even if cancelled
                displayedValue = diceValue
                rotationAnim.snapTo(0f)
                scaleAnim.snapTo(1f)
                isRollingVisual = false
            }
        } else {
            displayedValue = diceValue
            rotationAnim.snapTo(0f)
            scaleAnim.snapTo(1f)
            isRollingVisual = false
        }
    }

    Column(
        modifier = modifier.testTag("ludo_dice_control"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .scale(if (isRollable) pulseScale else scaleAnim.value)
                .rotate(rotationAnim.value)
                .size(size)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141026))
                .border(
                    width = if (isRollable || isRollingVisual) 3.dp else 1.8.dp,
                    color = if (isRollable || isRollingVisual) primaryColor else Color(0xFF322A54),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable(
                    enabled = isRollable && !isRollingVisual,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDiceClick
                )
                .testTag("ludo_dice_button"),
            contentAlignment = Alignment.Center
        ) {
            DiceFace(
                value = displayedValue,
                size = size - 10.dp,
                pipColor = primaryColor,
                backgroundColor = Color(0xFF1B1632)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action prompt
        val promptText = when {
            isRollingVisual -> "ROLLING..."
            turnPhase == TurnPhase.WAITING_FOR_DICE_ROLL -> "TAP TO ROLL"
            turnPhase == TurnPhase.WAITING_FOR_TOKEN_SELECTION -> "SELECT TOKEN"
            turnPhase == TurnPhase.TURN_RESOLVED -> "..."
            turnPhase == TurnPhase.GAME_OVER -> "FINISHED"
            else -> "..."
        }

        Text(
            text = promptText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = if (isRollable) primaryColor else Color(0xFF8B7FA8)
            )
        )
    }
}
