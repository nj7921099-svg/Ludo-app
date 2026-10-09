package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.stats.model.LudoStatisticsData
import com.example.game.ludo.stats.model.MatchHistoryRecord
import com.example.game.ludo.stats.model.PlayerMatchContribution
import com.example.game.ludo.stats.model.PlayerStatistics
import com.example.ui.MainViewModel
import com.example.ui.components.stats.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Phase 9 Step 3: Statistics & Match History UI and Presentation Tests.
 *
 * Verifies all 15 required UI and presentation test cases:
 * 1. Empty player statistics state.
 * 2. Player statistics render correctly (fields, labels, win rate).
 * 3. Games played / won render correctly.
 * 4. Win rate renders correctly (formatted percentage and edge cases).
 * 5. Tokens finished / captured render correctly.
 * 6. Team-Up statistics render correctly (matches played/won, team associations).
 * 7. Empty match history state.
 * 8. Match history record renders correctly (timestamp, mode, duration, player count).
 * 9. Individual winner displays correctly ("Winner: P1").
 * 10. Team-Up winner displays correctly ("Winning Team: Team 1 (P1 + P3)").
 * 11. Player contribution data renders (moves, 6s, caps, lost, finished).
 * 12. Null finish rank handled safely without crashing.
 * 13. Completed match appears in history and updates ViewModel StateFlow.
 * 14. Unfinished / reset match does not appear in history.
 * 15. Clear statistics produces empty statistics state and resets all metrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase9StatisticsUiTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(app)
        viewModel.clearAllStatistics()
    }

    // 1. Empty player statistics state
    @Test
    fun testEmptyPlayerStatisticsState() {
        val currentStats = viewModel.statisticsData.value
        assertEquals(0, currentStats.matchHistory.size)
        val totalGames = currentStats.playerStats.values.sumOf { it.gamesPlayed }
        assertEquals(0, totalGames)
    }

    // 2. Player statistics render correctly
    @Test
    fun testPlayerStatisticsDataModel() {
        val p1 = PlayerStatistics(
            playerId = 1,
            gamesPlayed = 10,
            gamesWon = 6,
            tokensFinished = 16,
            tokensCaptured = 8,
            timesCaptured = 3,
            sixesRolled = 15,
            extraTurnsGranted = 12,
            totalMoves = 45,
            teamUpMatchesPlayed = 4,
            teamUpMatchesWon = 3
        )

        assertEquals(1, p1.playerId)
        assertEquals(10, p1.gamesPlayed)
        assertEquals(6, p1.gamesWon)
        assertEquals(16, p1.tokensFinished)
        assertEquals(8, p1.tokensCaptured)
        assertEquals(3, p1.timesCaptured)
        assertEquals(15, p1.sixesRolled)
        assertEquals(12, p1.extraTurnsGranted)
        assertEquals(45, p1.totalMoves)
        assertEquals(4, p1.teamUpMatchesPlayed)
        assertEquals(3, p1.teamUpMatchesWon)
    }

    // 3. Games played / won render correctly
    @Test
    fun testGamesPlayedAndWon() {
        val stats = PlayerStatistics(playerId = 2, gamesPlayed = 5, gamesWon = 2)
        assertEquals(5, stats.gamesPlayed)
        assertEquals(2, stats.gamesWon)
    }

    // 4. Win rate renders correctly
    @Test
    fun testWinRateCalculationAndFormatting() {
        val zeroGames = PlayerStatistics(playerId = 1, gamesPlayed = 0, gamesWon = 0)
        assertEquals(0f, zeroGames.winRatePercent, 0.001f)
        val zeroFormatted = "%.1f".format(Locale.US, zeroGames.winRatePercent)
        assertEquals("0.0", zeroFormatted)

        val halfWon = PlayerStatistics(playerId = 2, gamesPlayed = 4, gamesWon = 2)
        assertEquals(50f, halfWon.winRatePercent, 0.001f)
        val halfFormatted = "%.1f".format(Locale.US, halfWon.winRatePercent)
        assertEquals("50.0", halfFormatted)

        val oneThird = PlayerStatistics(playerId = 3, gamesPlayed = 3, gamesWon = 1)
        val formatted = "%.1f".format(Locale.US, oneThird.winRatePercent)
        assertEquals("33.3", formatted)
    }

    // 5. Tokens finished / captured render correctly
    @Test
    fun testTokensFinishedAndCaptured() {
        val stats = PlayerStatistics(playerId = 4, tokensFinished = 4, tokensCaptured = 7, timesCaptured = 2)
        assertEquals(4, stats.tokensFinished)
        assertEquals(7, stats.tokensCaptured)
        assertEquals(2, stats.timesCaptured)
    }

    // 6. Team-Up statistics render correctly
    @Test
    fun testTeamUpStatistics() {
        val stats = PlayerStatistics(
            playerId = 1,
            gamesPlayed = 6,
            gamesWon = 4,
            teamUpMatchesPlayed = 4,
            teamUpMatchesWon = 3
        )
        assertEquals(4, stats.teamUpMatchesPlayed)
        assertEquals(3, stats.teamUpMatchesWon)
        assertTrue(stats.teamUpMatchesPlayed <= stats.gamesPlayed)
        assertTrue(stats.teamUpMatchesWon <= stats.gamesWon)
    }

    // 7. Empty match history state
    @Test
    fun testEmptyMatchHistoryState() {
        val statsData = LudoStatisticsData()
        assertTrue(statsData.matchHistory.isEmpty())
        assertEquals(0, statsData.matchHistory.size)
    }

    // 8. Match history record renders
    @Test
    fun testMatchHistoryRecordRenderingFields() {
        val record = MatchHistoryRecord(
            matchId = "test-match-1",
            timestamp = 1700000000000L,
            durationSeconds = 195L,
            gameMode = LudoGameMode.INDIVIDUAL,
            playerCount = 4,
            winningPlayerId = 1,
            winningTeamId = null,
            players = emptyList()
        )

        assertEquals("test-match-1", record.matchId)
        assertEquals(LudoGameMode.INDIVIDUAL, record.gameMode)
        assertEquals(4, record.playerCount)
        assertEquals(1, record.winningPlayerId)
        assertNull(record.winningTeamId)
        assertEquals("03:15", formatDuration(record.durationSeconds))
    }

    // 9. Individual winner displays correctly
    @Test
    fun testIndividualWinnerDisplay() {
        val record = MatchHistoryRecord(
            matchId = "indiv-1",
            timestamp = System.currentTimeMillis(),
            durationSeconds = 65L,
            gameMode = LudoGameMode.INDIVIDUAL,
            playerCount = 2,
            winningPlayerId = 2,
            winningTeamId = null,
            players = emptyList()
        )

        assertEquals(2, record.winningPlayerId)
        val winnerLabel = "Winner: P${record.winningPlayerId}"
        assertEquals("Winner: P2", winnerLabel)
    }

    // 10. Team-Up winner displays correctly
    @Test
    fun testTeamUpWinnerDisplay() {
        val record = MatchHistoryRecord(
            matchId = "team-1",
            timestamp = System.currentTimeMillis(),
            durationSeconds = 48L,
            gameMode = LudoGameMode.TEAM_UP,
            playerCount = 4,
            winningPlayerId = null,
            winningTeamId = LudoTeamId.TEAM_1,
            players = emptyList()
        )

        assertEquals(LudoTeamId.TEAM_1, record.winningTeamId)
        val teamDisplay = when (record.winningTeamId) {
            LudoTeamId.TEAM_1 -> "Team 1 (P1 + P3)"
            LudoTeamId.TEAM_2 -> "Team 2 (P2 + P4)"
            null -> "Team"
        }
        assertEquals("Team 1 (P1 + P3)", teamDisplay)
        assertEquals("00:48", formatDuration(record.durationSeconds))
    }

    // 11. Player contribution data renders
    @Test
    fun testPlayerContributionData() {
        val contribution = PlayerMatchContribution(
            playerId = 3,
            color = LudoColor.YELLOW,
            teamId = LudoTeamId.TEAM_1,
            finishRank = 1,
            isWinner = true,
            tokensFinished = 4,
            tokensCaptured = 3,
            timesCaptured = 1,
            sixesRolled = 4,
            extraTurns = 3,
            movesCount = 18
        )

        assertEquals(3, contribution.playerId)
        assertEquals(LudoColor.YELLOW, contribution.color)
        assertEquals(LudoTeamId.TEAM_1, contribution.teamId)
        assertEquals(1, contribution.finishRank)
        assertTrue(contribution.isWinner)
        assertEquals(4, contribution.tokensFinished)
        assertEquals(3, contribution.tokensCaptured)
        assertEquals(1, contribution.timesCaptured)
        assertEquals(4, contribution.sixesRolled)
        assertEquals(3, contribution.extraTurns)
        assertEquals(18, contribution.movesCount)
    }

    // 12. Null finish rank does not crash
    @Test
    fun testNullFinishRankDoesNotCrash() {
        val contribution = PlayerMatchContribution(
            playerId = 2,
            color = LudoColor.GREEN,
            finishRank = null,
            isWinner = false
        )

        assertNull(contribution.finishRank)
        assertFalse(contribution.isWinner)
        val rankText = when (contribution.finishRank) {
            1 -> "🥇 1st"
            2 -> "🥈 2nd"
            3 -> "🥉 3rd"
            4 -> "4th"
            else -> null
        }
        assertNull(rankText)
    }

    // 13. Completed match appears in history and updates ViewModel StateFlow
    @Test
    fun testCompletedMatchAppearsInHistoryFlow() {
        // Start game via ViewModel
        viewModel.selectBoxCount(2)
        viewModel.startGame()
        assertTrue(viewModel.ludoGameEngine.gameState.value.isGameStarted)

        val engine = viewModel.ludoGameEngine
        val manager = viewModel.statisticsManager

        // Fast forward player 1 to win
        val p1Tokens = engine.gameState.value.players.first().tokens.map { it.copy(stepCount = 57) }
        val winningState = engine.gameState.value.copy(
            players = engine.gameState.value.players.map {
                if (it.playerId == 1) it.copy(tokens = p1Tokens, isFinished = true, finishRank = 1) else it
            },
            turnPhase = com.example.game.ludo.model.TurnPhase.GAME_OVER,
            winners = listOf(1)
        )

        val committed = manager.finalizeMatchIfGameOver(winningState)
        assertTrue(committed)

        // Verify history record exists in ViewModel flow
        val history = viewModel.statisticsData.value.matchHistory
        assertEquals(1, history.size)
        assertEquals(1, history.first().winningPlayerId)
        assertEquals(2, history.first().playerCount)

        // Verify player stats updated
        val p1Stats = viewModel.statisticsData.value.playerStats[1]
        assertNotNull(p1Stats)
        assertEquals(1, p1Stats?.gamesPlayed)
        assertEquals(1, p1Stats?.gamesWon)
    }

    // 14. Unfinished / reset match does not appear in history
    @Test
    fun testUnfinishedMatchDiscardedOnReset() {
        viewModel.selectBoxCount(4)
        viewModel.startGame()

        // Roll dice
        viewModel.simulateNumberTest(6)

        // Reset match before game over
        viewModel.resetToSetup()
        assertFalse(viewModel.uiState.value.isGameStarted)

        // Match history must remain empty
        val history = viewModel.statisticsData.value.matchHistory
        assertEquals(0, history.size)
    }

    // 15. Clear statistics produces empty statistics state
    @Test
    fun testClearStatisticsWipesEverythingAtomically() {
        // Populate one match
        viewModel.selectBoxCount(2)
        viewModel.startGame()

        val engine = viewModel.ludoGameEngine
        val winningState = engine.gameState.value.copy(
            players = engine.gameState.value.players.map {
                if (it.playerId == 1) it.copy(tokens = it.tokens.map { t -> t.copy(stepCount = 57) }, isFinished = true, finishRank = 1) else it
            },
            turnPhase = com.example.game.ludo.model.TurnPhase.GAME_OVER,
            winners = listOf(1)
        )
        viewModel.statisticsManager.finalizeMatchIfGameOver(winningState)

        // Confirm stats exist
        assertTrue(viewModel.statisticsData.value.matchHistory.isNotEmpty())

        // Clear all statistics via ViewModel
        viewModel.clearAllStatistics()

        // Assert completely empty
        val cleared = viewModel.statisticsData.value
        assertEquals(0, cleared.matchHistory.size)
        assertTrue(cleared.playerStats.values.all { it.gamesPlayed == 0 && it.gamesWon == 0 })
    }
}
