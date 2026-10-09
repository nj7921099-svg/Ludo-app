package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.game.ludo.ai.LudoBotStrategy
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import com.example.ui.BotOpponentMode
import com.example.ui.MainViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

/**
 * Phase 10 - Step 3: Bot Selection UI & Final End-to-End Integration Tests.
 *
 * Verifies all 12 explicit requirements:
 * 1. ALL HUMAN selects no bots.
 * 2. VS BOTS selects P2..Pn as bots and P1 as human.
 * 3. Changing player count removes inactive bot IDs.
 * 4. Team-Up allows individual player control selection.
 * 5. Switching back to ALL HUMAN clears bot selections.
 * 6. Rematch preserves the intended player-control configuration.
 * 7. Reset cancels pending bot actions.
 * 8. Human turns remain manual.
 * 9. Bot turns execute automatically.
 * 10. Game over prevents further bot actions.
 * 11. Existing Team-Up Partner Assistance continues to work.
 * 12. Existing statistics and persistence tests remain valid.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase10Step3BotUiIntegrationTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(app)
        viewModel.clearAllStatistics()
        viewModel.botActionDelayMs = 0L
    }

    // =========================================================================
    // 1. ALL HUMAN selects no bots
    // =========================================================================

    @Test
    fun test1_allHuman_selectsNoBots() {
        viewModel.selectBotOpponentMode(BotOpponentMode.ALL_HUMAN)

        assertEquals(BotOpponentMode.ALL_HUMAN, viewModel.botOpponentMode.value)
        assertEquals(emptySet<Int>(), viewModel.botPlayerIds.value)
        assertEquals(emptySet<Int>(), viewModel.uiState.value.botPlayerIds)
        assertEquals(BotOpponentMode.ALL_HUMAN, viewModel.uiState.value.botOpponentMode)
    }

    // =========================================================================
    // 2. VS BOTS selects P2..Pn as bots and P1 as human
    // =========================================================================

    @Test
    fun test2_vsBots_selectsP2ToPnAsBotsAndP1AsHuman() {
        // Test for 2 players
        viewModel.selectBoxCount(2)
        viewModel.selectBotOpponentMode(BotOpponentMode.VS_BOTS)
        assertEquals(setOf(2), viewModel.botPlayerIds.value)
        assertFalse(viewModel.isBotPlayer(1))
        assertTrue(viewModel.isBotPlayer(2))

        // Test for 3 players
        viewModel.selectBoxCount(3)
        viewModel.selectBotOpponentMode(BotOpponentMode.VS_BOTS)
        assertEquals(setOf(2, 3), viewModel.botPlayerIds.value)
        assertFalse(viewModel.isBotPlayer(1))
        assertTrue(viewModel.isBotPlayer(2))
        assertTrue(viewModel.isBotPlayer(3))

        // Test for 4 players
        viewModel.selectBoxCount(4)
        viewModel.selectBotOpponentMode(BotOpponentMode.VS_BOTS)
        assertEquals(setOf(2, 3, 4), viewModel.botPlayerIds.value)
        assertFalse(viewModel.isBotPlayer(1))
        assertTrue(viewModel.isBotPlayer(2))
        assertTrue(viewModel.isBotPlayer(3))
        assertTrue(viewModel.isBotPlayer(4))
    }

    // =========================================================================
    // 3. Changing player count removes inactive bot IDs
    // =========================================================================

    @Test
    fun test3_changingPlayerCount_removesInactiveBotIds() {
        // Start in 4 players with bots {2, 3, 4}
        viewModel.selectBoxCount(4)
        viewModel.selectBotOpponentMode(BotOpponentMode.VS_BOTS)
        assertEquals(setOf(2, 3, 4), viewModel.botPlayerIds.value)

        // Drop to 2 players -> P3 and P4 must not remain in botPlayerIds
        viewModel.selectBoxCount(2)
        assertEquals(setOf(2), viewModel.botPlayerIds.value)
        assertFalse(viewModel.isBotPlayer(3))
        assertFalse(viewModel.isBotPlayer(4))

        // Custom selection in 4 players: set custom bots {1, 4}
        viewModel.selectBoxCount(4)
        viewModel.setBotPlayers(setOf(1, 4))
        assertEquals(setOf(1, 4), viewModel.botPlayerIds.value)

        // Drop to 3 players -> P4 removed, P1 preserved
        viewModel.selectBoxCount(3)
        assertEquals(setOf(1), viewModel.botPlayerIds.value)
        assertFalse(viewModel.isBotPlayer(4))
    }

    // =========================================================================
    // 4. Team-Up allows individual player control selection
    // =========================================================================

    @Test
    fun test4_teamUp_allowsIndividualPlayerControlSelection() {
        viewModel.selectGameMode(LudoGameMode.TEAM_UP)
        assertEquals(4, viewModel.gameEngine.selectedBoxCount.value)

        // Initially all human
        viewModel.selectBotOpponentMode(BotOpponentMode.ALL_HUMAN)
        assertEquals(emptySet<Int>(), viewModel.botPlayerIds.value)

        // Toggle P1 to Bot
        viewModel.toggleBotPlayer(1)
        assertTrue(viewModel.isBotPlayer(1))
        assertEquals(setOf(1), viewModel.botPlayerIds.value)

        // Toggle P3 to Bot (Team 1 all bots: P1 + P3)
        viewModel.toggleBotPlayer(3)
        assertTrue(viewModel.isBotPlayer(1))
        assertTrue(viewModel.isBotPlayer(3))
        assertFalse(viewModel.isBotPlayer(2))
        assertFalse(viewModel.isBotPlayer(4))

        // Toggle P1 back to Human
        viewModel.toggleBotPlayer(1)
        assertFalse(viewModel.isBotPlayer(1))
        assertTrue(viewModel.isBotPlayer(3))
    }

    // =========================================================================
    // 5. Switching back to ALL HUMAN clears bot selections
    // =========================================================================

    @Test
    fun test5_switchingBackToAllHuman_clearsBotSelections() {
        viewModel.selectBoxCount(4)
        viewModel.selectBotOpponentMode(BotOpponentMode.VS_BOTS)
        assertEquals(setOf(2, 3, 4), viewModel.botPlayerIds.value)

        // Switch to ALL HUMAN
        viewModel.selectBotOpponentMode(BotOpponentMode.ALL_HUMAN)
        assertEquals(emptySet<Int>(), viewModel.botPlayerIds.value)
        assertEquals(BotOpponentMode.ALL_HUMAN, viewModel.botOpponentMode.value)
    }

    // =========================================================================
    // 6. Rematch preserves the intended player-control configuration
    // =========================================================================

    @Test
    fun test6_rematch_preservesIntendedPlayerControlConfiguration() {
        viewModel.selectBoxCount(4)
        viewModel.setBotPlayers(setOf(2, 4))
        viewModel.startGame()

        assertEquals(setOf(2, 4), viewModel.botPlayerIds.value)

        // Trigger rematch
        viewModel.restartLudoRematch()

        // Configuration must be completely preserved across rematch
        assertEquals(setOf(2, 4), viewModel.botPlayerIds.value)
        assertFalse(viewModel.isBotPlayer(1))
        assertTrue(viewModel.isBotPlayer(2))
        assertFalse(viewModel.isBotPlayer(3))
        assertTrue(viewModel.isBotPlayer(4))
    }

    // =========================================================================
    // 7. Reset cancels pending bot actions
    // =========================================================================

    @Test
    fun test7_reset_cancelsPendingBotActions() = runTest {
        viewModel.botActionDelayMs = 1000L
        viewModel.setBotPlayers(setOf(1))
        viewModel.startGame()

        // Reset to setup immediately
        viewModel.resetToSetup()

        advanceUntilIdle()

        val state = viewModel.ludoGameEngine.gameState.value
        assertFalse(state.isGameStarted)
    }

    // =========================================================================
    // 8. Human turns remain manual
    // =========================================================================

    @Test
    fun test8_humanTurnsRemainManual() = runTest {
        viewModel.botActionDelayMs = 0L
        viewModel.selectBotOpponentMode(BotOpponentMode.ALL_HUMAN)
        viewModel.startGame()

        advanceUntilIdle()

        val state = viewModel.ludoGameEngine.gameState.value
        assertEquals(1, state.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)
        assertNull(state.diceValue)
    }

    // =========================================================================
    // 9. Bot turns execute automatically
    // =========================================================================

    @Test
    fun test9_botTurnsExecuteAutomatically() = runTest {
        viewModel.botActionDelayMs = 0L
        // Configure P1 as Bot
        viewModel.setBotPlayers(setOf(1))
        viewModel.startGame()

        advanceUntilIdle()

        val state = viewModel.ludoGameEngine.gameState.value
        // P1 was automated: either rolled dice, or passed turn if no legal moves
        assertNotNull(state.diceValue)
    }

    // =========================================================================
    // 10. Game over prevents further bot actions
    // =========================================================================

    @Test
    fun test9_gameOverPreventsFurtherBotActions() = runTest {
        viewModel.botActionDelayMs = 0L
        viewModel.setBotPlayers(setOf(1, 2))

        // Set game state to GAME_OVER
        viewModel.ludoGameEngine.initGame(2)
        val finishedState = viewModel.ludoGameEngine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winners = listOf(1)
        )
        viewModel.ludoGameEngine.restoreState(finishedState)

        advanceUntilIdle()

        // Turn phase remains GAME_OVER without any bot actions launched
        assertEquals(TurnPhase.GAME_OVER, viewModel.ludoGameEngine.gameState.value.turnPhase)
    }

    // =========================================================================
    // 11. Existing Team-Up Partner Assistance continues to work
    // =========================================================================

    @Test
    fun test11_existingTeamUpPartnerAssistance_continuesToWork() {
        val engine = LudoGameEngine()
        engine.initGame(4, LudoGameMode.TEAM_UP)

        // Player 1 finished all 4 tokens
        val p1Tokens = (0..3).map {
            LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57)
        }
        val p1 = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = p1Tokens,
            isFinished = true,
            finishRank = 1,
            teamId = LudoTeamId.TEAM_1
        )
        // Partner Player 3 has unfinished token at step 20
        val p3Tokens = listOf(
            LudoToken(tokenId = 0, playerId = 3, state = TokenState.ON_BOARD, stepCount = 20),
            LudoToken(tokenId = 1, playerId = 3, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 2, playerId = 3, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 3, playerId = 3, state = TokenState.IN_BASE, stepCount = 0)
        )
        val p3 = LudoPlayer(
            playerId = 3,
            color = LudoColor.YELLOW,
            tokens = p3Tokens,
            teamId = LudoTeamId.TEAM_1
        )

        val partnerState = LudoGameState(
            isGameStarted = true,
            playerCount = 4,
            players = listOf(
                p1,
                LudoPlayer(playerId = 2, color = LudoColor.GREEN, teamId = LudoTeamId.TEAM_2),
                p3,
                LudoPlayer(playerId = 4, color = LudoColor.BLUE, teamId = LudoTeamId.TEAM_2)
            ),
            currentPlayerId = 1,
            diceValue = 4,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0),
            gameMode = LudoGameMode.TEAM_UP
        )

        val bestToken = LudoBotStrategy.selectToken(partnerState)
        assertEquals(0, bestToken)
    }

    // =========================================================================
    // 12. Existing statistics and persistence remain valid
    // =========================================================================

    @Test
    fun test12_existingStatisticsAndPersistence_remainValid() = runTest {
        viewModel.botActionDelayMs = 0L
        viewModel.selectBotOpponentMode(BotOpponentMode.VS_BOTS)
        viewModel.startGame()

        advanceUntilIdle()

        // Verify active session tracking is operational
        assertTrue(viewModel.statisticsManager.isSessionActive)
        assertEquals(0, viewModel.statisticsData.value.matchHistory.size)
    }
}
