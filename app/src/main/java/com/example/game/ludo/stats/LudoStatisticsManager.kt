package com.example.game.ludo.stats

import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoMoveResult
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.stats.model.LudoStatisticsData
import com.example.game.ludo.stats.model.MatchHistoryRecord
import com.example.game.ludo.stats.model.PlayerMatchContribution
import com.example.game.ludo.stats.model.PlayerStatistics
import com.example.game.ludo.stats.persistence.LudoStatisticsPersistence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Thread-safe manager coordinating in-memory active match session metrics,
 * lifetime player statistics accumulation, and atomic persistence.
 */
class LudoStatisticsManager(
    private val persistence: LudoStatisticsPersistence
) {
    /**
     * Mutable in-memory accumulator for a single player within an active match.
     */
    private data class PlayerSessionMetrics(
        val playerId: Int,
        val color: LudoColor,
        val teamId: LudoTeamId? = null,
        var sixesRolled: Int = 0,
        var movesCount: Int = 0,
        var tokensCaptured: Int = 0,
        var timesCaptured: Int = 0,
        var extraTurns: Int = 0,
        var tokensFinished: Int = 0
    )

    /**
     * Active in-memory match session.
     */
    private data class ActiveMatchSession(
        val matchId: String,
        val startTimeMillis: Long,
        val gameMode: LudoGameMode,
        val playerCount: Int,
        val playerMetrics: Map<Int, PlayerSessionMetrics>,
        var isCommitted: Boolean = false
    )

    private var activeSession: ActiveMatchSession? = null

    private val _statisticsData = MutableStateFlow(persistence.loadStatistics())
    val statisticsData: StateFlow<LudoStatisticsData> = _statisticsData.asStateFlow()

    val currentMatchId: String?
        @Synchronized get() = activeSession?.matchId

    val isSessionActive: Boolean
        @Synchronized get() = activeSession != null && activeSession?.isCommitted == false

    /**
     * Starts a new in-memory statistics tracking session for an authoritative new game.
     * Only called when [initGame] succeeds.
     */
    @Synchronized
    fun startNewSession(initialState: LudoGameState): String {
        val matchId = UUID.randomUUID().toString()
        val metricsMap = initialState.players.associate { p ->
            p.playerId to PlayerSessionMetrics(
                playerId = p.playerId,
                color = p.color,
                teamId = p.teamId
            )
        }

        activeSession = ActiveMatchSession(
            matchId = matchId,
            startTimeMillis = System.currentTimeMillis(),
            gameMode = initialState.gameMode,
            playerCount = initialState.playerCount,
            playerMetrics = metricsMap,
            isCommitted = false
        )
        return matchId
    }

    /**
     * Records an accepted dice roll result.
     * Increments sixesRolled for the active player strictly when [diceValue] == 6 and accepted.
     */
    @Synchronized
    fun onDiceRolled(playerId: Int, diceValue: Int, acceptedByEngine: Boolean) {
        val session = activeSession ?: return
        if (session.isCommitted || !acceptedByEngine) return

        if (diceValue == 6) {
            session.playerMetrics[playerId]?.let {
                it.sixesRolled++
            }
        }
    }

    /**
     * Records an accepted token move result.
     *
     * Action credits:
     * - [result.playerId]: movesCount++, tokensCaptured += captured.size, extraTurns++ (if extra turn granted).
     *
     * Defending players:
     * - each captured token's owner: timesCaptured++.
     *
     * Finished token:
     * - Credited to the token owner (handles Partner Assistance where active player assists partner).
     */
    @Synchronized
    fun onTokenMoved(result: LudoMoveResult, effectiveTokenOwnerPlayerId: Int = result.playerId) {
        val session = activeSession ?: return
        if (session.isCommitted) return

        // 1. Credit active player for the move execution
        session.playerMetrics[result.playerId]?.let { activeMetrics ->
            activeMetrics.movesCount++
            activeMetrics.tokensCaptured += result.capturedTokens.size
            if (result.extraTurnGranted) {
                activeMetrics.extraTurns++
            }
        }

        // 2. Credit defending player(s) whose tokens were captured
        result.capturedTokens.forEach { capturedToken ->
            session.playerMetrics[capturedToken.playerId]?.let { victimMetrics ->
                victimMetrics.timesCaptured++
            }
        }

        // 3. Credit finished token to the token's owner
        if (result.finishedToken) {
            session.playerMetrics[effectiveTokenOwnerPlayerId]?.let { ownerMetrics ->
                ownerMetrics.tokensFinished++
            }
        }
    }

    /**
     * Commits the match if [finalState.isGameOver] is true.
     * Protects against duplicate commits using [ActiveMatchSession.isCommitted].
     * Updates lifetime player statistics and prepends match history record atomically.
     *
     * @return true if match was committed, false if not completed or already committed.
     */
    @Synchronized
    fun finalizeMatchIfGameOver(finalState: LudoGameState): Boolean {
        if (!finalState.isGameOver) return false
        val session = activeSession ?: return false
        if (session.isCommitted) return false

        session.isCommitted = true
        val endTimeMillis = System.currentTimeMillis()
        val durationSeconds = ((endTimeMillis - session.startTimeMillis) / 1000L).coerceAtLeast(0L)

        val isTeamUp = session.gameMode == LudoGameMode.TEAM_UP
        val winningTeamId = if (isTeamUp) finalState.winningTeamId else null
        val winningPlayerId = if (!isTeamUp) finalState.winnerPlayerId else null

        // Build player match contributions
        val contributions = session.playerMetrics.values.map { m ->
            val pState = finalState.players.find { it.playerId == m.playerId }
            val isWinner = if (isTeamUp) {
                m.teamId != null && m.teamId == winningTeamId
            } else {
                winningPlayerId != null && m.playerId == winningPlayerId
            }

            PlayerMatchContribution(
                playerId = m.playerId,
                color = m.color,
                teamId = m.teamId,
                finishRank = pState?.finishRank,
                isWinner = isWinner,
                tokensFinished = m.tokensFinished,
                tokensCaptured = m.tokensCaptured,
                timesCaptured = m.timesCaptured,
                sixesRolled = m.sixesRolled,
                extraTurns = m.extraTurns,
                movesCount = m.movesCount
            )
        }

        val historyRecord = MatchHistoryRecord(
            matchId = session.matchId,
            timestamp = session.startTimeMillis,
            durationSeconds = durationSeconds,
            gameMode = session.gameMode,
            playerCount = session.playerCount,
            winningPlayerId = winningPlayerId,
            winningTeamId = winningTeamId,
            players = contributions
        )

        // Update lifetime player statistics
        val currentStats = _statisticsData.value
        val updatedPlayerStats = currentStats.playerStats.toMutableMap()

        contributions.forEach { c ->
            val existing = updatedPlayerStats[c.playerId] ?: PlayerStatistics(playerId = c.playerId)
            val updated = existing.copy(
                gamesPlayed = existing.gamesPlayed + 1,
                gamesWon = existing.gamesWon + if (c.isWinner) 1 else 0,
                tokensFinished = existing.tokensFinished + c.tokensFinished,
                tokensCaptured = existing.tokensCaptured + c.tokensCaptured,
                timesCaptured = existing.timesCaptured + c.timesCaptured,
                sixesRolled = existing.sixesRolled + c.sixesRolled,
                extraTurnsGranted = existing.extraTurnsGranted + c.extraTurns,
                totalMoves = existing.totalMoves + c.movesCount,
                teamUpMatchesPlayed = existing.teamUpMatchesPlayed + if (isTeamUp) 1 else 0,
                teamUpMatchesWon = existing.teamUpMatchesWon + if (isTeamUp && c.isWinner) 1 else 0
            )
            updatedPlayerStats[c.playerId] = updated
        }

        // Newest-first history
        val updatedHistory = listOf(historyRecord) + currentStats.matchHistory

        val newStatsData = LudoStatisticsData(
            schemaVersion = LudoStatisticsPersistence.CURRENT_SCHEMA_VERSION,
            playerStats = updatedPlayerStats,
            matchHistory = updatedHistory
        )

        _statisticsData.value = newStatsData
        persistence.saveStatistics(newStatsData)
        return true
    }

    /**
     * Discards the active in-memory session if the game is reset before completion.
     * Does NOT create a match history record or increment gamesPlayed/gamesWon.
     */
    @Synchronized
    fun discardActiveSession() {
        activeSession = null
    }

    /**
     * Resets all stored lifetime statistics (useful for tests or explicit reset).
     */
    @Synchronized
    fun clearAllStatistics(): Boolean {
        activeSession = null
        val clearedData = LudoStatisticsData()
        _statisticsData.value = clearedData
        return persistence.clearStatistics()
    }
}
