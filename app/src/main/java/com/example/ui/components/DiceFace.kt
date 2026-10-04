package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual dice face displaying authentic pips (1 to 6) or placeholder for unrolled state.
 */
@Composable
fun DiceFace(
    value: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    pipColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (value != null) 1.0f else 0.95f,
        animationSpec = tween(durationMillis = 200),
        label = "dice_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scaleAnim)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = if (value != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (value == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
        } else {
            Canvas(modifier = Modifier.size(size)) {
                val w = this.size.width
                val h = this.size.height
                val pipRadius = w * 0.08f

                val left = w * 0.26f
                val center = w * 0.50f
                val right = w * 0.74f

                val top = h * 0.26f
                val middle = h * 0.50f
                val bottom = h * 0.74f

                when (value) {
                    1 -> {
                        drawCircle(pipColor, pipRadius, Offset(center, middle))
                    }
                    2 -> {
                        drawCircle(pipColor, pipRadius, Offset(left, top))
                        drawCircle(pipColor, pipRadius, Offset(right, bottom))
                    }
                    3 -> {
                        drawCircle(pipColor, pipRadius, Offset(left, top))
                        drawCircle(pipColor, pipRadius, Offset(center, middle))
                        drawCircle(pipColor, pipRadius, Offset(right, bottom))
                    }
                    4 -> {
                        drawCircle(pipColor, pipRadius, Offset(left, top))
                        drawCircle(pipColor, pipRadius, Offset(right, top))
                        drawCircle(pipColor, pipRadius, Offset(left, bottom))
                        drawCircle(pipColor, pipRadius, Offset(right, bottom))
                    }
                    5 -> {
                        drawCircle(pipColor, pipRadius, Offset(left, top))
                        drawCircle(pipColor, pipRadius, Offset(right, top))
                        drawCircle(pipColor, pipRadius, Offset(center, middle))
                        drawCircle(pipColor, pipRadius, Offset(left, bottom))
                        drawCircle(pipColor, pipRadius, Offset(right, bottom))
                    }
                    6 -> {
                        drawCircle(pipColor, pipRadius, Offset(left, top))
                        drawCircle(pipColor, pipRadius, Offset(right, top))
                        drawCircle(pipColor, pipRadius, Offset(left, middle))
                        drawCircle(pipColor, pipRadius, Offset(right, middle))
                        drawCircle(pipColor, pipRadius, Offset(left, bottom))
                        drawCircle(pipColor, pipRadius, Offset(right, bottom))
                    }
                }
            }
        }
    }
}
