package com.example.game.ludo.stats.model

import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoTeamId

/**
 * Breakdown of a single player's actions and contributions within a completed match.
 */
data class PlayerMatchContribution(
    val playerId: Int,
    val color: LudoColor,
    val teamId: LudoTeamId? = null,
    val finishRank: Int? = null,
    val isWinner: Boolean = false,
    val tokensFinished: Int = 0,
    val tokensCaptured: Int = 0,
    val timesCaptured: Int = 0,
    val sixesRolled: Int = 0,
    val extraTurns: Int = 0,
    val movesCount: Int = 0
)
