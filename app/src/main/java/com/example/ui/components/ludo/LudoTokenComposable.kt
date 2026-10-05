package com.example.ui.components.ludo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.game.ludo.model.LudoColor

/**
 * Modern vector-rendered Ludo pawn token with glowing pulse when legal to move.
 */
@Composable
fun LudoTokenComposable(
    color: LudoColor,
    tokenId: Int,
    isLegalToMove: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val primaryColor = LudoThemeColors.getPrimaryColor(color)
    val lightColor = LudoThemeColors.getLightColor(color)
    val glowColor = LudoThemeColors.getGlowColor(color)

    // Pulsing halo animation when selectable
    val infiniteTransition = rememberInfiniteTransition(label = "token_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isLegalToMove) 1.25f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isLegalToMove) 0.85f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    Box(
        modifier = modifier
            .size(size * 1.5f)
            .clickable(
                enabled = isLegalToMove,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = "${color.displayName} token $tokenId${if (isLegalToMove) ", selectable" else ""}"
            }
            .testTag("ludo_token_${color.name.lowercase()}_$tokenId"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * pulseScale)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.width * 0.42f

            // Outer glowing ring for legal moves
            if (isLegalToMove) {
                drawCircle(
                    color = glowColor.copy(alpha = haloAlpha),
                    radius = baseRadius * 1.35f
                )
                drawCircle(
                    color = Color.White,
                    radius = baseRadius * 1.15f,
                    style = Stroke(width = 2.5f)
                )
            }

            // Drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                radius = baseRadius,
                center = center + Offset(1.5f, 2.5f)
            )

            // Main token pawn body with radial gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lightColor, primaryColor),
                    center = center - Offset(baseRadius * 0.25f, baseRadius * 0.25f),
                    radius = baseRadius * 1.2f
                ),
                radius = baseRadius,
                center = center
            )

            // Inner dark rim
            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 2.0f)
            )

            // Highlight shine
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = baseRadius * 0.28f,
                center = center - Offset(baseRadius * 0.28f, baseRadius * 0.28f)
            )

            // Center crown pip
            drawCircle(
                color = Color.White,
                radius = baseRadius * 0.22f,
                center = center
            )
        }
    }
}
