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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.game.ludo.model.LudoColor

/**
 * Modern vector 3D Ludo token with bevels, specular reflections, and animated legal selection glow.
 */
@Composable
fun LudoTokenComposable(
    color: LudoColor,
    tokenId: Int,
    isLegalToMove: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val primaryColor = LudoThemeColors.getPrimaryColor(color)
    val lightColor = LudoThemeColors.getLightColor(color)
    val darkColor = LudoThemeColors.getDarkColor(color)
    val glowColor = LudoThemeColors.getGlowColor(color)

    // Pulsing halo animation when selectable
    val infiniteTransition = rememberInfiniteTransition(label = "token_pulse_$tokenId")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isLegalToMove) 1.20f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isLegalToMove) 0.90f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    Box(
        modifier = modifier
            .size(size * 1.45f)
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
        Canvas(modifier = Modifier.size(size * (if (isLegalToMove) pulseScale else 1.0f))) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.width * 0.40f

            // 1. Outer glowing beacon ring for legal moves
            if (isLegalToMove) {
                drawCircle(
                    color = glowColor.copy(alpha = haloAlpha * 0.45f),
                    radius = baseRadius * 1.55f,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = haloAlpha),
                    radius = baseRadius * 1.28f,
                    center = center,
                    style = Stroke(width = 2.4f)
                )
            }

            // 2. Realistic drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.50f),
                radius = baseRadius * 1.02f,
                center = center + Offset(1.5f, 2.8f)
            )

            // 3. Dark beveled outer base ring
            drawCircle(
                color = darkColor,
                radius = baseRadius,
                center = center
            )

            // 4. Main 3D convex dome with rich multi-stop radial gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        lightColor,
                        primaryColor,
                        darkColor
                    ),
                    center = center - Offset(baseRadius * 0.32f, baseRadius * 0.32f),
                    radius = baseRadius * 1.15f
                ),
                radius = baseRadius * 0.92f,
                center = center
            )

            // 5. Metallic inner ring
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = baseRadius * 0.56f,
                center = center,
                style = Stroke(width = 1.2f)
            )

            // 6. Center glossy star/pip core
            drawTokenStar(
                center = center,
                radius = baseRadius * 0.32f,
                color = Color.White.copy(alpha = 0.95f)
            )

            // 7. Top-left specular gloss sheen
            drawCircle(
                color = Color.White.copy(alpha = 0.70f),
                radius = baseRadius * 0.18f,
                center = center - Offset(baseRadius * 0.35f, baseRadius * 0.35f)
            )
        }
    }
}

private fun DrawScope.drawTokenStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val spikes = 5
    val step = Math.PI / spikes
    var rot = Math.PI / 2 * 3

    path.moveTo(center.x, (center.y - radius))
    for (i in 0 until spikes) {
        val x1 = center.x + Math.cos(rot).toFloat() * radius
        val y1 = center.y + Math.sin(rot).toFloat() * radius
        path.lineTo(x1, y1)
        rot += step

        val x2 = center.x + Math.cos(rot).toFloat() * (radius * 0.42f)
        val y2 = center.y + Math.sin(rot).toFloat() * (radius * 0.42f)
        path.lineTo(x2, y2)
        rot += step
    }
    path.close()
    drawPath(path, color, style = Fill)
}

