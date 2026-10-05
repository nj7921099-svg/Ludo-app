package com.example.game.ludo.model

/**
 * Immutable, complete snapshot of the Ludo game state.
 */
data class LudoGameState(
    val isGameStarted: Boolean = false,
    val playerCount: Int = 4,
    val players: List<LudoPlayer> = emptyList(),
    val currentPlayerId: Int = 1,
    val diceValue: Int? = null,
    val turnPhase: TurnPhase = TurnPhase.WAITING_FOR_DICE_ROLL,
    val legalTokenIds: Set<Int> = emptySet(),
    val consecutiveSixCount: Int = 0,
    val winners: List<Int> = emptyList(),
    val lastMoveResult: LudoMoveResult? = null
) {
    val activePlayer: LudoPlayer?
        get() = players.find { it.playerId == currentPlayerId }

    val isGameOver: Boolean
        get() = turnPhase == TurnPhase.GAME_OVER

    val hasLegalMove: Boolean
        get() = legalTokenIds.isNotEmpty()

    fun getPlayer(playerId: Int): LudoPlayer? = players.find { it.playerId == playerId }
}
