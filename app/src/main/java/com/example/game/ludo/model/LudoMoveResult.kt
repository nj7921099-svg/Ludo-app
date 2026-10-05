package com.example.game.ludo.model

/**
 * Deterministic outcome of executing a token move.
 * Contains all necessary metadata for the engine and future UI animation step-by-step playback.
 */
data class LudoMoveResult(
    val playerId: Int,
    val tokenId: Int,
    val diceValue: Int,
    val fromStep: Int,
    val toStep: Int,
    val traversedSteps: List<Int>,
    val capturedTokens: List<LudoToken> = emptyList(),
    val enteredHome: Boolean = false,
    val finishedToken: Boolean = false,
    val extraTurnGranted: Boolean = false,
    val extraTurnReason: ExtraTurnReason? = null,
    val isGameOver: Boolean = false,
    val nextPlayerId: Int
)
