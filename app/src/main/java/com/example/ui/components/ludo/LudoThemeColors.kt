package com.example.ui.components.ludo

import androidx.compose.ui.graphics.Color
import com.example.game.ludo.model.LudoColor

/**
 * Visual styling and vibrant colors for modern Ludo board rendering.
 */
object LudoThemeColors {
    val RedPrimary = Color(0xFFEF4444)
    val RedDark = Color(0xFF451114)
    val RedLight = Color(0xFFFCA5A5)
    val RedGlow = Color(0xFFFF5252)

    val GreenPrimary = Color(0xFF22C55E)
    val GreenDark = Color(0xFF0F3D1F)
    val GreenLight = Color(0xFF86EFAC)
    val GreenGlow = Color(0xFF4ADE80)

    val YellowPrimary = Color(0xFFFACC15)
    val YellowDark = Color(0xFF453609)
    val YellowLight = Color(0xFFFEF08A)
    val YellowGlow = Color(0xFFFFE082)

    val BluePrimary = Color(0xFF3B82F6)
    val BlueDark = Color(0xFF11254A)
    val BlueLight = Color(0xFF93C5FD)
    val BlueGlow = Color(0xFF60A5FA)

    val TrackCellBg = Color(0xFF131022)
    val TrackCellBorder = Color(0xFF2E264E)
    val SafeStarColor = Color(0xFFFFD700)
    val SafeStarGlow = Color(0xFFFFE57F)
    val BoardBg = Color(0xFF0A0815)
    val CenterHomeBg = Color(0xFF18132C)
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

