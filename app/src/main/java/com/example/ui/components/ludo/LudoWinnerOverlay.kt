package com.example.ui.components.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.game.ludo.model.LudoPlayer
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.random.Random

/**
 * Victory dialog displayed when a match concludes, featuring royal crown graphics and particle celebration.
 */
@Composable
fun LudoWinnerOverlay(
    winners: List<Int>,
    players: List<LudoPlayer>,
    onNewGame: () -> Unit,
    onBackToSetup: () -> Unit
) {
    val scaleAnim = remember { Animatable(0.75f) }
    val particleAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(1.0f, animationSpec = spring(dampingRatio = 0.65f))
    }

    LaunchedEffect(Unit) {
        particleAnim.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    Dialog(onDismissRequest = { /* Modal dialog */ }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scaleAnim.value)
        ) {
            // Background celebratory fireworks/confetti particles
            CelebrationParticles(progress = particleAnim.value)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("ludo_winner_dialog"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF100C22)),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    Brush.linearGradient(listOf(Color(0xFFFFD700), NeonPurpleBright, Color(0xFFFFD700)))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Royal Golden Crown Hero Art (winnercrown.png style vector)
                    Canvas(modifier = Modifier.size(72.dp)) {
                        drawRoyalCrown(center = Offset(size.width / 2f, size.height / 2f), size = size.width * 0.88f)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "VICTORY!",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            letterSpacing = 2.5.sp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFFFFE57F), Color(0xFFFFD700), Color(0xFFFFA000))
                            )
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val firstWinner = players.find { it.playerId == winners.firstOrNull() }
                    val winnerName = firstWinner?.name ?: "Player 1"

                    Text(
                        text = "$winnerName has conquered the board!",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            color = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Match Leaderboard Podium
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF090714))
                            .border(1.dp, Color(0xFF261E40), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        players.sortedBy { it.finishRank ?: 99 }.forEachIndexed { idx, player ->
                            val rankLabel = when (player.finishRank) {
                                1 -> "🥇 1st Place"
                                2 -> "🥈 2nd Place"
                                3 -> "🥉 3rd Place"
                                else -> "${idx + 1}th Place"
                            }
                            val pColor = LudoThemeColors.getPrimaryColor(player.color)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(pColor)
                                            .border(1.5.dp, Color.White, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = player.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = rankLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = if (player.finishRank == 1) Color(0xFFFFD700) else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackToSetup,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("winner_btn_setup"),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A3C6B))
                        ) {
                            Text(
                                text = "Setup",
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = onNewGame,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("winner_btn_rematch"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Text(
                                text = "Play Again",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Royal golden crown vector rendering inspired by winnercrown.png
 */
private fun DrawScope.drawRoyalCrown(center: Offset, size: Float) {
    val w = size
    val h = size * 0.72f
    val left = center.x - w / 2f
    val top = center.y - h / 2f
    val right = center.x + w / 2f
    val bottom = center.y + h / 2f

    val crownPath = Path().apply {
        moveTo(left, bottom - h * 0.15f)
        lineTo(left + w * 0.08f, top + h * 0.25f)
        lineTo(left + w * 0.28f, top + h * 0.55f)
        lineTo(center.x, top)
        lineTo(right - w * 0.28f, top + h * 0.55f)
        lineTo(right - w * 0.08f, top + h * 0.25f)
        lineTo(right, bottom - h * 0.15f)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }

    // Gold gradient fill
    drawPath(
        path = crownPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFF3B0), Color(0xFFFFD700), Color(0xFFC69200)),
            startY = top,
            endY = bottom
        ),
        style = Fill
    )

    // Dark outline
    drawPath(
        path = crownPath,
        color = Color(0xFF7A5500),
        style = Stroke(width = 2.5f)
    )

    // Crown base rim
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFF59D), Color(0xFFFFD700))),
        topLeft = Offset(left, bottom - h * 0.20f),
        size = androidx.compose.ui.geometry.Size(w, h * 0.20f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
    )

    // Jewel gems (Sapphires & Rubies)
    val gemRadius = w * 0.048f
    drawCircle(Color(0xFF2563EB), gemRadius, Offset(center.x, bottom - h * 0.10f))
    drawCircle(Color(0xFFEF4444), gemRadius * 0.85f, Offset(left + w * 0.25f, bottom - h * 0.10f))
    drawCircle(Color(0xFFEF4444), gemRadius * 0.85f, Offset(right - w * 0.25f, bottom - h * 0.10f))
}

/**
 * Lightweight mathematical particle celebration overlay.
 */
@Composable
private fun CelebrationParticles(progress: Float) {
    val particleColors = listOf(
        Color(0xFFFFD700),
        Color(0xFFEF4444),
        Color(0xFF22C55E),
        Color(0xFF3B82F6),
        Color(0xFFC084FC),
        Color(0xFFFFFFFF)
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val count = 28
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * 2 * Math.PI + (i * 0.4f)
            val speed = 90f + (i % 6) * 35f
            val dist = speed * progress
            val cx = size.width / 2f + Math.cos(angle).toFloat() * dist
            val cy = size.height / 2f + Math.sin(angle).toFloat() * dist + (progress * progress * 80f) // gravity

            val alpha = (1f - progress).coerceIn(0f, 1f)
            val pColor = particleColors[i % particleColors.size].copy(alpha = alpha)

            if (i % 3 == 0) {
                drawCircle(color = pColor, radius = 4f, center = Offset(cx, cy))
            } else {
                drawRect(
                    color = pColor,
                    topLeft = Offset(cx - 3f, cy - 3f),
                    size = androidx.compose.ui.geometry.Size(6f, 6f)
                )
            }
        }
    }
}

