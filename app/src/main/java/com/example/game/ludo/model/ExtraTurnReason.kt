package com.example.game.ludo.model

/**
 * Reason why an extra turn (consecutive roll) was awarded to the active player.
 */
enum class ExtraTurnReason {
    /** Player rolled a six (first or second consecutive) */
    ROLLED_SIX,

    /** Player captured an opponent's token on a non-safe tile */
    CAPTURED_OPPONENT,

    /** Player's token successfully reached Home (finished) */
    FINISHED_TOKEN
}
