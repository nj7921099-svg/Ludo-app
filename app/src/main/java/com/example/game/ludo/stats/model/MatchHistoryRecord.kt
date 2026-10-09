package com.example.game.ludo.stats.model

import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoTeamId

/**
 * Historical record of a fully completed Ludo match.
 */
data class MatchHistoryRecord(
    val matchId: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val gameMode: LudoGameMode,
    val playerCount: Int,
    val winningPlayerId: Int?,
    val winningTeamId: LudoTeamId?,
    val players: List<PlayerMatchContribution>
)
