package com.example.ui.components.ludo

import androidx.compose.ui.graphics.Color
import com.example.game.ludo.model.LudoColor

/**
 * Visual styling and vibrant colors for classic reference-accurate Ludo board rendering.
 * Matches classic 4-color board: Green (top-left), Yellow (top-right), Red (bottom-left), Blue (bottom-right).
 */
object LudoThemeColors {
    // Reference image vibrant colors
    val RedPrimary = Color(0xFFE2262B)
    val RedDark = Color(0xFFB81B1F)
    val RedLight = Color(0xFFFF8B8E)
    val RedGlow = Color(0xFFFF4D4D)

    val GreenPrimary = Color(0xFF009B48)
    val GreenDark = Color(0xFF007536)
    val GreenLight = Color(0xFF55C77A)
    val GreenGlow = Color(0xFF2FD66F)

    val YellowPrimary = Color(0xFFFFDF00)
    val YellowDark = Color(0xFFCCA700)
    val YellowLight = Color(0xFFFFF275)
    val YellowGlow = Color(0xFFFFEB3B)

    val BluePrimary = Color(0xFF139FE0)
    val BlueDark = Color(0xFF0A7BAF)
    val BlueLight = Color(0xFF6ED3FF)
    val BlueGlow = Color(0xFF38B6FF)

    // Classic white board styling
    val TrackCellBg = Color(0xFFFFFFFF)
    val TrackCellBorder = Color(0xFF222222)
    val SafeStarColor = Color(0xFF333333)
    val SafeStarGlow = Color(0xFF666666)
    val BoardBg = Color(0xFFFFFFFF)
    val CenterHomeBg = Color(0xFFFFFFFF)
    val GoldCrown = Color(0xFFFFD700)
    val GoldCrownLight = Color(0xFFFFF3B0)

    fun getPrimaryColor(color: LudoColor): Color = when (color) {
        LudoColor.RED -> RedPrimary
        LudoColor.GREEN -> GreenPrimary
        LudoColor.YELLOW -> YellowPrimary
        LudoColor.BLUE -> BluePrimary
    }

    fun getDarkColor(color: LudoColor): Color = when (color) {
        LudoColor.RED -> RedDark
        LudoColor.GREEN -> GreenDark
        LudoColor.YELLOW -> YellowDark
        LudoColor.BLUE -> BlueDark
    }

    fun getLightColor(color: LudoColor): Color = when (color) {
        LudoColor.RED -> RedLight
        LudoColor.GREEN -> GreenLight
        LudoColor.YELLOW -> YellowLight
        LudoColor.BLUE -> BlueLight
    }

    fun getGlowColor(color: LudoColor): Color = when (color) {
        LudoColor.RED -> RedGlow
        LudoColor.GREEN -> GreenGlow
        LudoColor.YELLOW -> YellowGlow
        LudoColor.BLUE -> BlueGlow
    }
}

