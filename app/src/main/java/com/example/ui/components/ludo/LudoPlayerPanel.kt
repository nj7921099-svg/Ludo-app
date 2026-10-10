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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.TurnPhase
import com.example.ui.components.DiceFace
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Dedicated player panel displaying their assigned color, pawn identity,
 * individual dedicated dice, active turn indicator, and finished tokens count.
 *
 * P1 = RED
 * P2 = GREEN
 * P3 = YELLOW
 * P4 = BLUE
 */
@Composable
fun LudoPlayerPanel(
    player: LudoPlayer,
    isActiveTurn: Boolean,
    turnPhase: TurnPhase,
    activeDiceValue: Int?,
    onDiceClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMoveLocked: Boolean = false,
    isBot: Boolean = false,
    diceSize: Dp = 52.dp
) {
    val color = player.color
    val primaryColor = LudoThemeColors.getPrimaryColor(color)
    val darkColor = LudoThemeColors.getDarkColor(color)
    val isRollable = isActiveTurn && (turnPhase == TurnPhase.WAITING_FOR_DICE_ROLL) && !isMoveLocked && !isBot

    // Breathing glow animation for active player border
    val infiniteTransition = rememberInfiniteTransition(label = "player_panel_glow_${player.playerId}")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "panel_pulse_alpha"
    )

    // Dice pulse when it is this player's turn to roll
    val dicePulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "panel_dice_pulse"
    )

    // Tumble animation for this player's dice
    val rotationAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(1f) }
    var displayedValue by remember { mutableStateOf<Int?>(null) }
    var isRollingVisual by remember { mutableStateOf(false) }

    LaunchedEffect(activeDiceValue, isActiveTurn, turnPhase) {
        if (isActiveTurn && activeDiceValue != null && turnPhase == TurnPhase.WAITING_FOR_TOKEN_SELECTION) {
            try {
                isRollingVisual = true
                for (i in 1..3) {
                    displayedValue = ((i * 2 + activeDiceValue) % 6) + 1
                    rotationAnim.animateTo(
                        targetValue = if (i % 2 == 0) 14f else -14f,
                        animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                    )
                }
                rotationAnim.animateTo(0f, animationSpec = tween(durationMillis = 40))
                displayedValue = activeDiceValue
                scaleAnim.snapTo(1.15f)
                scaleAnim.animateTo(1.0f, animationSpec = spring(dampingRatio = 0.65f))
            } finally {
                displayedValue = activeDiceValue
                rotationAnim.snapTo(0f)
                scaleAnim.snapTo(1f)
                isRollingVisual = false
            }
        } else if (isActiveTurn) {
            displayedValue = activeDiceValue
            rotationAnim.snapTo(0f)
            scaleAnim.snapTo(1f)
            isRollingVisual = false
        }
    }

    val pawnRes = when (color) {
        LudoColor.RED -> R.drawable.redpiece
        LudoColor.GREEN -> R.drawable.greenpiece
        LudoColor.YELLOW -> R.drawable.yellowpiece
        LudoColor.BLUE -> R.drawable.bluepiece
    }

    val finishedCount = player.tokens.count { it.isFinished }

    Card(
        modifier = modifier
            .testTag("player_box_card_${player.playerId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveTurn) Color(0xFF140F24) else Color(0xFF0C0916)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActiveTurn) 2.dp else 1.dp,
            color = if (isActiveTurn) primaryColor.copy(alpha = pulseAlpha) else primaryColor.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Color Pawn + Player Name/Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = pawnRes),
                    contentDescription = "${color.displayName} Pawn",
                    modifier = Modifier.size(26.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "P${player.playerId}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = primaryColor
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = color.displayName.uppercase(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        )
                        if (isBot) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "BOT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 8.5.sp,
                                    color = Color(0xFFA78BFA)
                                )
                            )
                        }
                    }

                    Text(
                        text = when {
                            player.isFinished -> "🏆 Finished"
                            isActiveTurn && turnPhase == TurnPhase.WAITING_FOR_DICE_ROLL -> if (isBot) "Bot rolling..." else "Your Turn! Roll"
                            isActiveTurn && turnPhase == TurnPhase.WAITING_FOR_TOKEN_SELECTION -> if (isBot) "Bot moving..." else "Pick a Token"
                            isActiveTurn -> "Active..."
                            else -> if (finishedCount > 0) "$finishedCount/4 Home" else if (isBot) "Bot" else "Waiting"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            color = if (isActiveTurn) primaryColor else TextSecondary,
                            fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }

            // Right: Dedicated Player Dice
            Box(
                modifier = Modifier
                    .scale(if (isRollable) dicePulseScale else scaleAnim.value)
                    .rotate(rotationAnim.value)
                    .size(diceSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActiveTurn) Color(0xFF1B1630) else Color(0xFF100D1E))
                    .border(
                        width = if (isRollable || (isActiveTurn && isRollingVisual)) 2.5.dp else 1.2.dp,
                        color = if (isActiveTurn) primaryColor else primaryColor.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        enabled = isRollable && !isRollingVisual,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDiceClick
                    )
                    .testTag("player_dice_button_${player.playerId}")
                    .then(if (isActiveTurn) Modifier.testTag("ludo_dice_button") else Modifier),
                contentAlignment = Alignment.Center
            ) {
                DiceFace(
                    value = if (isActiveTurn) (displayedValue ?: activeDiceValue) else null,
                    size = diceSize - 8.dp,
                    pipColor = if (isActiveTurn) primaryColor else primaryColor.copy(alpha = 0.4f),
                    backgroundColor = if (isActiveTurn) Color(0xFF140F24) else Color(0xFF0D0A18)
                )
            }
        }
    }
}
