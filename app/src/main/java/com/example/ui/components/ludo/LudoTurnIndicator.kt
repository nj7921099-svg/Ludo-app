package com.example.ui.components.ludo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.TurnPhase
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Modern animated turn status banner displaying the current player, phase instruction, and extra-turn status.
 */
@Composable
fun LudoTurnIndicator(
    activePlayer: LudoPlayer?,
    turnPhase: TurnPhase,
    consecutiveSixCount: Int,
    hasPendingControllerNumber: Boolean,
    modifier: Modifier = Modifier
) {
    if (activePlayer == null) return

    val color = activePlayer.color
    val primaryColor = LudoThemeColors.getPrimaryColor(color)
    val glowColor = LudoThemeColors.getGlowColor(color)
    val darkColor = LudoThemeColors.getDarkColor(color)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ludo_turn_indicator_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0C1E)),
        border = androidx.compose.foundation.BorderStroke(
            1.8.dp,
            Brush.horizontalGradient(listOf(primaryColor, primaryColor.copy(alpha = 0.35f)))
        )
    ) {
        AnimatedContent(
            targetState = Pair(activePlayer.playerId, turnPhase),
            transitionSpec = {
                (fadeIn() + slideInVertically { height -> height / 3 }) togetherWith
                    (fadeOut() + slideOutVertically { height -> -height / 3 })
            },
            label = "turn_indicator_anim"
        ) { (playerId, phase) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player info
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color circle indicator with glowing border
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(LudoThemeColors.getLightColor(color), primaryColor),
                                    radius = 26f
                                )
                            )
                            .border(2.dp, Color.White, CircleShape)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "${color.displayName.uppercase()} (PLAYER $playerId)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = primaryColor
                            )
                        )

                        val statusText = when (phase) {
                            TurnPhase.WAITING_FOR_DICE_ROLL -> "Roll the dice to move"
                            TurnPhase.WAITING_FOR_TOKEN_SELECTION -> "Tap a highlighted token"
                            TurnPhase.TURN_RESOLVED -> "Resolving turn..."
                            TurnPhase.GAME_OVER -> "Match Finished!"
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        )
                    }
                }

                // Status badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Consecutive 6 badge
                    if (consecutiveSixCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = primaryColor.copy(alpha = 0.22f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Six #$consecutiveSixCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = primaryColor
                                    )
                                )
                            }
                        }
                    }

                    // Controller pending badge
                    if (hasPendingControllerNumber) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF261845),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC084FC))
                        ) {
                            Text(
                                text = "Controller Ready",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFE9D5FF)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

