package com.example.ui.components.ludo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.ludo.model.LudoColor

/**
 * Modern 3D Ludo token rendering using actual uploaded pawn assets (e.g. yellowpiece.png).
 * Highly optimized: infinite pulse transition ONLY runs when token is legal to move.
 */
@Composable
fun LudoTokenComposable(
    color: LudoColor,
    tokenId: Int,
    isLegalToMove: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    val glowColor = LudoThemeColors.getGlowColor(color)

    // Dedicated drawable asset for each player color
    val drawableRes = when (color) {
        LudoColor.RED -> R.drawable.redpiece
        LudoColor.GREEN -> R.drawable.greenpiece
        LudoColor.YELLOW -> R.drawable.yellowpiece
        LudoColor.BLUE -> R.drawable.bluepiece
    }

    // High performance optimization: ONLY run transition when selectable!
    val pulseScale: Float
    if (isLegalToMove) {
        val infiniteTransition = rememberInfiniteTransition(label = "legal_pulse_$tokenId")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
        pulseScale = animatedScale
    } else {
        pulseScale = 1.0f
    }

    Box(
        modifier = modifier
            .size(size * 1.35f)
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
        // Legal move aura ring
        if (isLegalToMove) {
            Box(
                modifier = Modifier
                    .size(size * 1.30f * pulseScale)
                    .clip(CircleShape)
                    .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = glowColor, spotColor = Color.White)
            )
        }

        // Actual 3D pawn piece PNG image asset
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            modifier = Modifier
                .size(size * if (isLegalToMove) pulseScale else 1.0f)
        )
    }
}
