package com.example.game.ludo.model

/**
 * Canonical 4 player colors for standard Ludo board.
 */
enum class LudoColor {
    RED,
    GREEN,
    YELLOW,
    BLUE;

    val displayName: String
        get() = when (this) {
            RED -> "Red"
            GREEN -> "Green"
            YELLOW -> "Yellow"
            BLUE -> "Blue"
        }
}
