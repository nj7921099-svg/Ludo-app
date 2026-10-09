package com.example.game.ludo.stats.model

/**
 * Lifetime statistics for a Player/Box slot identity (P1..P4).
 * Represents gameplay metrics accumulated across completed matches on this device.
 */
data class PlayerStatistics(
    val playerId: Int,
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val tokensFinished: Int = 0,
    val tokensCaptured: Int = 0,
    val timesCaptured: Int = 0,
    val sixesRolled: Int = 0,
    val extraTurnsGranted: Int = 0,
    val totalMoves: Int = 0,
    val teamUpMatchesPlayed: Int = 0,
    val teamUpMatchesWon: Int = 0
) {
    val winRatePercent: Float
        get() = if (gamesPlayed > 0) (gamesWon.toFloat() / gamesPlayed * 100f) else 0f
}
