package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonPurpleBright,
    onPrimary = OnPrimaryDark,
    primaryContainer = NeonPurpleDeep,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = NeonGreen,
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF005234),
    onSecondaryContainer = Color(0xFF67FEA8),
    tertiary = NeonCyan,
    onTertiary = Color(0xFF00363F),
    background = NeonDarkBg,
    onBackground = TextPrimary,
    surface = NeonDarkCard,
    onSurface = TextPrimary,
    surfaceVariant = NeonDarkCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = NeonPurple,
    outlineVariant = Color(0xFF2E244D)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to futuristic neon dark aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
