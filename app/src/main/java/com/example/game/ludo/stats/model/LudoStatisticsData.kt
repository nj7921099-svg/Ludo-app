package com.example.game.ludo.stats.model

/**
 * Root data container for all persisted statistics and match history.
 *
 * @param schemaVersion Schema version (current = 1).
 * @param playerStats Map of playerId (1..4) -> lifetime [PlayerStatistics].
 * @param matchHistory Ordered list of completed matches, sorted newest-first.
 */
data class LudoStatisticsData(
    val schemaVersion: Int = 1,
    val playerStats: Map<Int, PlayerStatistics> = emptyMap(),
    val matchHistory: List<MatchHistoryRecord> = emptyList()
)
