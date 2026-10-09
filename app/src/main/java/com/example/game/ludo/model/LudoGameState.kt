package com.example.game.ludo.model

/**
 * Immutable snapshot of the authoritative Ludo game state.
 *
 * @param isGameStarted True if a game session has been initialized.
 * @param playerCount Number of active players (2..4).
 * @param players Immutable list of players in the game.
 * @param currentPlayerId ID of the player whose turn it currently is (1..4).
 * @param diceValue Current active dice value (1..6) after rolling, or null if awaiting roll.
 * @param turnPhase Current phase within the turn state machine.
 * @param legalTokenIds Token IDs (0..3) belonging to the active player that can legally be moved.
 * @param consecutiveSixCount Count of consecutive 6 rolls by the current player (0..3).
 * @param winners Ordered list of finished player IDs (1st place, 2nd place, etc.).
 * @param lastMoveResult Outcome of the most recently executed token move, if any.
 * @param gameMode Selected game mode (INDIVIDUAL or TEAM_UP).
 * @param winningTeamId Team that won the match in TEAM_UP mode, or null if game is ongoing / INDIVIDUAL mode.
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
    val lastMoveResult: LudoMoveResult? = null,
    val gameMode: LudoGameMode = LudoGameMode.INDIVIDUAL,
    val winningTeamId: LudoTeamId? = null
) {
    val activePlayer: LudoPlayer?
        get() = players.find { it.playerId == currentPlayerId }

    val isGameOver: Boolean
        get() = turnPhase == TurnPhase.GAME_OVER

    val winnerPlayerId: Int?
        get() = winners.firstOrNull()

    /**
     * In TEAM_UP mode, returns true if both players on the given team have all 8 tokens finished.
     */
    fun isTeamFinished(teamId: LudoTeamId): Boolean {
        val teamPlayers = players.filter { it.teamId == teamId }
        return teamPlayers.isNotEmpty() && teamPlayers.all { it.isFinished }
    }

    /**
     * In TEAM_UP mode, returns total finished tokens (0..8) for the given team.
     */
    fun getTeamFinishedTokenCount(teamId: LudoTeamId): Int {
        return players.filter { it.teamId == teamId }.sumOf { it.finishedTokenCount }
    }
}
