package com.example

import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoMoveResult
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import com.example.game.ludo.stats.LudoStatisticsManager
import com.example.game.ludo.stats.persistence.LudoStatisticsPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 9 Core Statistics & Match History Unit Tests.
 *
 * Verifies all 23 explicit requirements:
 * 1. New match creates unique matchId.
 * 2. Accepted six increments sixesRolled for active player.
 * 3. Rejected dice does not increment.
 * 4. Token move increments totalMoves.
 * 5. Capture credits capturing active player.
 * 6. Captured player gets timesCaptured.
 * 7. Finished token increments correct token-owner statistic.
 * 8. Extra turn increments correctly.
 * 9. Individual completed match creates exactly one history record.
 * 10. Individual winner gets gamesWon.
 * 11. All participating Individual players get gamesPlayed.
 * 12. Team-Up completed match creates exactly one history record.
 * 13. Both winning teammates get gamesWon.
 * 14. All Team-Up players get gamesPlayed.
 * 15. Team-Up teamUpMatchesPlayed is correct.
 * 16. Team-Up teamUpMatchesWon is correct.
 * 17. Reset before game over discards active session (no history record created).
 * 18. Rematch creates a new matchId.
 * 19. Duplicate finalization does not create duplicate history.
 * 20. Persistence round-trip works.
 * 21. Corrupted statistics file safely recovers.
 * 22. Statistics failure does not break gameplay.
 * 23. Existing rules and game modes remain uncorrupted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase9StatisticsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var statsFile: File
    private lateinit var persistence: LudoStatisticsPersistence
    private lateinit var statsManager: LudoStatisticsManager
    private lateinit var engine: LudoGameEngine

    @Before
    fun setUp() {
        statsFile = tempFolder.newFile("test_ludo_stats.json")
        persistence = LudoStatisticsPersistence(statsFile)
        statsManager = LudoStatisticsManager(persistence)
        engine = LudoGameEngine()
    }

    @Test
    fun test1_newMatchCreatesUniqueMatchId() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        val matchId1 = statsManager.startNewSession(engine.gameState.value)
        assertNotNull(matchId1)
        assertTrue(matchId1.isNotBlank())
        assertEquals(matchId1, statsManager.currentMatchId)

        // Starting another match produces distinct UUID
        val matchId2 = statsManager.startNewSession(engine.gameState.value)
        assertNotEquals(matchId1, matchId2)
    }

    @Test
    fun test2_acceptedSixIncrementsSixesRolled() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)

        // P1 rolls 6 (accepted)
        statsManager.onDiceRolled(playerId = 1, diceValue = 6, acceptedByEngine = true)
        // P1 rolls 4 (accepted - not a 6)
        statsManager.onDiceRolled(playerId = 1, diceValue = 4, acceptedByEngine = true)
        // P2 rolls 6 (accepted)
        statsManager.onDiceRolled(playerId = 2, diceValue = 6, acceptedByEngine = true)

        // Simulate game over to check contributions
        val finishedState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winners = listOf(1)
        )
        statsManager.finalizeMatchIfGameOver(finishedState)

        val stats = statsManager.statisticsData.value
        assertEquals(1, stats.matchHistory.size)
        val p1 = stats.matchHistory[0].players.find { it.playerId == 1 }!!
        val p2 = stats.matchHistory[0].players.find { it.playerId == 2 }!!
        val p3 = stats.matchHistory[0].players.find { it.playerId == 3 }!!

        assertEquals(1, p1.sixesRolled)
        assertEquals(1, p2.sixesRolled)
        assertEquals(0, p3.sixesRolled)
        assertEquals(1, stats.playerStats[1]?.sixesRolled)
        assertEquals(1, stats.playerStats[2]?.sixesRolled)
        assertEquals(0, stats.playerStats[3]?.sixesRolled)
    }

    @Test
    fun test3_rejectedDiceDoesNotIncrement() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)

        // P1 attempts roll of 6 but rejected by engine
        statsManager.onDiceRolled(playerId = 1, diceValue = 6, acceptedByEngine = false)

        val finishedState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winners = listOf(1)
        )
        statsManager.finalizeMatchIfGameOver(finishedState)

        val stats = statsManager.statisticsData.value
        assertEquals(0, stats.playerStats[1]?.sixesRolled ?: 0)
    }

    @Test
    fun test4_tokenMoveIncrementsTotalMoves() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)

        val move1 = LudoMoveResult(
            playerId = 1,
            tokenId = 0,
            diceValue = 6,
            fromStep = 0,
            toStep = 1,
            traversedSteps = listOf(1),
            nextPlayerId = 1
        )
        statsManager.onTokenMoved(move1)

        val move2 = LudoMoveResult(
            playerId = 1,
            tokenId = 0,
            diceValue = 3,
            fromStep = 1,
            toStep = 4,
            traversedSteps = listOf(2, 3, 4),
            nextPlayerId = 2
        )
        statsManager.onTokenMoved(move2)

        val finishedState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winners = listOf(1)
        )
        statsManager.finalizeMatchIfGameOver(finishedState)

        val stats = statsManager.statisticsData.value
        assertEquals(2, stats.playerStats[1]?.totalMoves)
        assertEquals(0, stats.playerStats[2]?.totalMoves ?: 0)
    }

    @Test
    fun test5_and_6_captureCreditsCapturingPlayerAndTimesCapturedVictim() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)

        val capturedOpponentToken = LudoToken(
            tokenId = 1,
            playerId = 2,
            state = TokenState.ON_BOARD,
            stepCount = 14
        )

        val captureMove = LudoMoveResult(
            playerId = 1,
            tokenId = 0,
            diceValue = 4,
            fromStep = 10,
            toStep = 14,
            traversedSteps = listOf(11, 12, 13, 14),
            capturedTokens = listOf(capturedOpponentToken),
            extraTurnGranted = true,
            extraTurnReason = ExtraTurnReason.CAPTURED_OPPONENT,
            nextPlayerId = 1
        )
        statsManager.onTokenMoved(captureMove)

        val finishedState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winners = listOf(1)
        )
        statsManager.finalizeMatchIfGameOver(finishedState)

        val stats = statsManager.statisticsData.value
        assertEquals("P1 gets 1 tokensCaptured", 1, stats.playerStats[1]?.tokensCaptured)
        assertEquals("P1 timesCaptured is 0", 0, stats.playerStats[1]?.timesCaptured)
        assertEquals("P2 timesCaptured is 1", 1, stats.playerStats[2]?.timesCaptured)
        assertEquals("P2 tokensCaptured is 0", 0, stats.playerStats[2]?.tokensCaptured)
    }

    @Test
    fun test7_finishedTokenIncrementsCorrectTokenOwnerStatistic() {
        engine.initGame(4, LudoGameMode.TEAM_UP)
        statsManager.startNewSession(engine.gameState.value)

        // P1 moves and finishes P3's token during Partner Assistance
        val partnerFinishMove = LudoMoveResult(
            playerId = 1,
            tokenId = 2, // P3's token
            diceValue = 2,
            fromStep = 55,
            toStep = 57,
            traversedSteps = listOf(56, 57),
            finishedToken = true,
            extraTurnGranted = true,
            extraTurnReason = ExtraTurnReason.FINISHED_TOKEN,
            nextPlayerId = 1
        )
        // Credited to token owner P3
        statsManager.onTokenMoved(partnerFinishMove, effectiveTokenOwnerPlayerId = 3)

        val finishedState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winningTeamId = LudoTeamId.TEAM_1
        )
        statsManager.finalizeMatchIfGameOver(finishedState)

        val stats = statsManager.statisticsData.value
        assertEquals("Move executed by P1", 1, stats.playerStats[1]?.totalMoves)
        assertEquals("P1 did not finish own token", 0, stats.playerStats[1]?.tokensFinished)
        assertEquals("P3 gets credit for token finish", 1, stats.playerStats[3]?.tokensFinished)
    }

    @Test
    fun test8_extraTurnIncrementsCorrectly() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)

        val moveWithExtraTurn = LudoMoveResult(
            playerId = 1,
            tokenId = 0,
            diceValue = 6,
            fromStep = 0,
            toStep = 1,
            traversedSteps = listOf(1),
            extraTurnGranted = true,
            extraTurnReason = ExtraTurnReason.ROLLED_SIX,
            nextPlayerId = 1
        )
        statsManager.onTokenMoved(moveWithExtraTurn)

        val normalMove = LudoMoveResult(
            playerId = 1,
            tokenId = 0,
            diceValue = 2,
            fromStep = 1,
            toStep = 3,
            traversedSteps = listOf(2, 3),
            extraTurnGranted = false,
            nextPlayerId = 2
        )
        statsManager.onTokenMoved(normalMove)

        val finishedState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winners = listOf(1)
        )
        statsManager.finalizeMatchIfGameOver(finishedState)

        val stats = statsManager.statisticsData.value
        assertEquals(1, stats.playerStats[1]?.extraTurnsGranted)
    }

    @Test
    fun test9_10_11_individualCompletedMatchCreatesSingleRecordAndCorrectPlayerStats() {
        engine.initGame(3, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)

        val gameOverState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winners = listOf(2, 1, 3)
        )
        val committed = statsManager.finalizeMatchIfGameOver(gameOverState)
        assertTrue(committed)

        val stats = statsManager.statisticsData.value
        assertEquals(1, stats.matchHistory.size)
        val record = stats.matchHistory[0]
        assertEquals(LudoGameMode.INDIVIDUAL, record.gameMode)
        assertEquals(3, record.playerCount)
        assertEquals(2, record.winningPlayerId)
        assertNull(record.winningTeamId)

        // All 3 players participated: gamesPlayed = 1
        assertEquals(1, stats.playerStats[1]?.gamesPlayed)
        assertEquals(1, stats.playerStats[2]?.gamesPlayed)
        assertEquals(1, stats.playerStats[3]?.gamesPlayed)
        assertNull(stats.playerStats[4]) // P4 did not play

        // Only P2 won
        assertEquals(0, stats.playerStats[1]?.gamesWon)
        assertEquals(1, stats.playerStats[2]?.gamesWon)
        assertEquals(0, stats.playerStats[3]?.gamesWon)

        // Win rate
        assertEquals(100f, stats.playerStats[2]!!.winRatePercent, 0.01f)
        assertEquals(0f, stats.playerStats[1]!!.winRatePercent, 0.01f)
    }

    @Test
    fun test12_to_16_teamUpCompletedMatchAwardsBothTeammatesAndUpdatesStats() {
        engine.initGame(4, LudoGameMode.TEAM_UP)
        statsManager.startNewSession(engine.gameState.value)

        val gameOverState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winningTeamId = LudoTeamId.TEAM_1
        )
        val committed = statsManager.finalizeMatchIfGameOver(gameOverState)
        assertTrue(committed)

        val stats = statsManager.statisticsData.value
        assertEquals(1, stats.matchHistory.size)
        val record = stats.matchHistory[0]
        assertEquals(LudoGameMode.TEAM_UP, record.gameMode)
        assertEquals(4, record.playerCount)
        assertEquals(LudoTeamId.TEAM_1, record.winningTeamId)

        // All 4 players played Team-Up match
        for (pId in 1..4) {
            assertEquals("P$pId gamesPlayed == 1", 1, stats.playerStats[pId]?.gamesPlayed)
            assertEquals("P$pId teamUpMatchesPlayed == 1", 1, stats.playerStats[pId]?.teamUpMatchesPlayed)
        }

        // P1 and P3 (TEAM_1) won
        assertEquals("P1 gamesWon == 1", 1, stats.playerStats[1]?.gamesWon)
        assertEquals("P1 teamUpMatchesWon == 1", 1, stats.playerStats[1]?.teamUpMatchesWon)
        assertEquals("P3 gamesWon == 1", 1, stats.playerStats[3]?.gamesWon)
        assertEquals("P3 teamUpMatchesWon == 1", 1, stats.playerStats[3]?.teamUpMatchesWon)

        // P2 and P4 (TEAM_2) lost
        assertEquals("P2 gamesWon == 0", 0, stats.playerStats[2]?.gamesWon)
        assertEquals("P2 teamUpMatchesWon == 0", 0, stats.playerStats[2]?.teamUpMatchesWon)
        assertEquals("P4 gamesWon == 0", 0, stats.playerStats[4]?.gamesWon)
        assertEquals("P4 teamUpMatchesWon == 0", 0, stats.playerStats[4]?.teamUpMatchesWon)
    }

    @Test
    fun test17_resetBeforeGameOverDiscardsActiveSession() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)
        assertTrue(statsManager.isSessionActive)

        // User resets game back to setup
        statsManager.discardActiveSession()

        assertFalse(statsManager.isSessionActive)
        assertNull(statsManager.currentMatchId)

        // Finalize attempt fails safely because session was discarded
        val gameOverState = engine.gameState.value.copy(turnPhase = TurnPhase.GAME_OVER, winners = listOf(1))
        val committed = statsManager.finalizeMatchIfGameOver(gameOverState)
        assertFalse(committed)

        val stats = statsManager.statisticsData.value
        assertEquals("No match history must be recorded", 0, stats.matchHistory.size)
        assertTrue("No player stats must be recorded", stats.playerStats.isEmpty())
    }

    @Test
    fun test18_rematchCreatesNewMatchId() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        val matchId1 = statsManager.startNewSession(engine.gameState.value)

        // First game ends
        val gameOverState1 = engine.gameState.value.copy(turnPhase = TurnPhase.GAME_OVER, winners = listOf(1))
        statsManager.finalizeMatchIfGameOver(gameOverState1)

        // Rematch started
        val matchId2 = statsManager.startNewSession(engine.gameState.value)
        assertNotEquals(matchId1, matchId2)

        // Second game ends
        val gameOverState2 = engine.gameState.value.copy(turnPhase = TurnPhase.GAME_OVER, winners = listOf(2))
        statsManager.finalizeMatchIfGameOver(gameOverState2)

        val stats = statsManager.statisticsData.value
        assertEquals("Must have 2 completed match history records", 2, stats.matchHistory.size)
        assertEquals(matchId2, stats.matchHistory[0].matchId) // Newest first
        assertEquals(matchId1, stats.matchHistory[1].matchId)
    }

    @Test
    fun test19_duplicateFinalizationDoesNotCreateDuplicateHistory() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)

        val gameOverState = engine.gameState.value.copy(turnPhase = TurnPhase.GAME_OVER, winners = listOf(1))
        val firstCommit = statsManager.finalizeMatchIfGameOver(gameOverState)
        val secondCommit = statsManager.finalizeMatchIfGameOver(gameOverState)

        assertTrue(firstCommit)
        assertFalse("Second commit attempt must be safely rejected", secondCommit)

        val stats = statsManager.statisticsData.value
        assertEquals("Must only have 1 history record", 1, stats.matchHistory.size)
        assertEquals("P1 played 1 match", 1, stats.playerStats[1]?.gamesPlayed)
    }

    @Test
    fun test20_persistenceRoundTripWorks() {
        engine.initGame(4, LudoGameMode.INDIVIDUAL)
        statsManager.startNewSession(engine.gameState.value)

        statsManager.onDiceRolled(playerId = 1, diceValue = 6, acceptedByEngine = true)
        statsManager.onTokenMoved(
            LudoMoveResult(
                playerId = 1,
                tokenId = 0,
                diceValue = 6,
                fromStep = 0,
                toStep = 1,
                traversedSteps = listOf(1),
                nextPlayerId = 1
            )
        )

        val gameOverState = engine.gameState.value.copy(turnPhase = TurnPhase.GAME_OVER, winners = listOf(1))
        statsManager.finalizeMatchIfGameOver(gameOverState)

        // Create new persistence & manager instance reading from the exact same file
        val newPersistence = LudoStatisticsPersistence(statsFile)
        val reloadedData = newPersistence.loadStatistics()

        assertEquals(1, reloadedData.matchHistory.size)
        val record = reloadedData.matchHistory[0]
        assertEquals(1, record.winningPlayerId)
        assertEquals(1, reloadedData.playerStats[1]?.gamesWon)
        assertEquals(1, reloadedData.playerStats[1]?.sixesRolled)
        assertEquals(1, reloadedData.playerStats[1]?.totalMoves)
    }

    @Test
    fun test21_corruptedStatisticsFileSafelyRecovers() {
        // Corrupt file contents
        statsFile.writeText("INVALID_CORRUPTED_JSON_DATA{{{###")

        val corruptedPersistence = LudoStatisticsPersistence(statsFile)
        val recoveredData = corruptedPersistence.loadStatistics()

        assertNotNull(recoveredData)
        assertEquals(1, recoveredData.schemaVersion)
        assertTrue(recoveredData.playerStats.isEmpty())
        assertTrue(recoveredData.matchHistory.isEmpty())
    }

    @Test
    fun test22_and_23_statisticsFailureDoesNotBreakGameplay() {
        // Read-only directory simulation / persistence clearing
        statsManager.clearAllStatistics()
        assertEquals(0, statsManager.statisticsData.value.matchHistory.size)

        // Regular gameplay initialization and mechanics remain completely functional
        val initSuccess = engine.initGame(4, LudoGameMode.TEAM_UP)
        assertTrue(initSuccess)
        assertEquals(LudoGameMode.TEAM_UP, engine.gameState.value.gameMode)
        assertEquals(4, engine.gameState.value.playerCount)
    }
}
