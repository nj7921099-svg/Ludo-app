package com.example.ui.components.ludo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
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
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.TurnPhase
import com.example.ui.components.DiceFace

/**
 * Modern interactive dice component for rolling in Ludo.
 *
 * @param diceValue Current rolled value (1..6) or null.
 * @param turnPhase Current phase of the active turn.
 * @param activeColor Color of the active player.
 * @param onDiceClick Invoked when player taps the dice.
 */
@Composable
fun LudoDiceControl(
    diceValue: Int?,
    turnPhase: TurnPhase,
    activeColor: LudoColor,
    onDiceClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMoveLocked: Boolean = false,
    size: Dp = 68.dp
) {
    val isRollable = (turnPhase == TurnPhase.WAITING_FOR_DICE_ROLL) && !isMoveLocked
    val primaryColor = LudoThemeColors.getPrimaryColor(activeColor)
    val glowColor = LudoThemeColors.getGlowColor(activeColor)

    val infiniteTransition = rememberInfiniteTransition(label = "dice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRollable) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dice_scale"
    )

    Column(
        modifier = modifier.testTag("ludo_dice_control"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .scale(pulseScale)
                .size(size)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF141026))
                .border(
                    width = if (isRollable) 3.dp else 1.5.dp,
                    color = if (isRollable) primaryColor else Color(0xFF322A54),
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable(
                    enabled = isRollable,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDiceClick
                )
                .testTag("ludo_dice_button"),
            contentAlignment = Alignment.Center
        ) {
            DiceFace(
                value = diceValue,
                size = size - 10.dp,
                pipColor = primaryColor,
                backgroundColor = Color(0xFF1F1A38)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action prompt
        val promptText = when (turnPhase) {
            TurnPhase.WAITING_FOR_DICE_ROLL -> "TAP TO ROLL"
            TurnPhase.WAITING_FOR_TOKEN_SELECTION -> "SELECT TOKEN"
            TurnPhase.TURN_RESOLVED -> "..."
            TurnPhase.GAME_OVER -> "FINISHED"
        }

        Text(
            text = promptText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = if (isRollable) glowColor else Color(0xFF8E84A8)
            )
        )
    }
}
