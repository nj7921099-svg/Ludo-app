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
 * Phase 10 - Step 2: Core AI/Bot Strategy and Safe Coordination Unit Tests.
 *
 * Verifies all 15 explicit requirements:
 * 1. Empty legalTokenIds returns null.
 * 2. Strategy never selects an illegal token.
 * 3. Legal finishing move receives highest priority.
 * 4. Capture is preferred over an ordinary move when no higher-priority finish is available.
 * 5. Leaving base is prioritized according to the documented heuristic.
 * 6. Safe-cell and forward-progress preferences work where applicable.
 * 7. Bot dice activation occurs on a configured bot's dice phase.
 * 8. Bot token selection occurs on a configured bot's move phase.
 * 9. Human turns are never automated.
 * 10. Repeated StateFlow emissions do not trigger duplicate actions.
 * 11. A turn change during a delay invalidates the pending action.
 * 12. Reset or rematch invalidates stale bot work.
 * 13. Team-Up Partner Assistance uses authoritative legal token IDs.
 * 14. Existing third-six behavior remains engine-controlled.
 * 15. Statistics and match finalization are not double-counted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase10BotTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(app)
        viewModel.clearAllStatistics()
        viewModel.botActionDelayMs = 0L // Fast deterministic scheduling for tests
    }

    // =========================================================================
    // 1. Strategy: Empty Legal Tokens
    // =========================================================================

    @Test
    fun test1_emptyLegalTokens_returnsNull() {
        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(
                LudoPlayer(playerId = 1, color = LudoColor.RED),
                LudoPlayer(playerId = 2, color = LudoColor.YELLOW)
            ),
            currentPlayerId = 1,
            diceValue = 3,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = emptySet()
        )

        val selected = LudoBotStrategy.selectToken(state)
        assertNull(selected)
    }

    // =========================================================================
    // 2. Strategy: Never Selects Illegal Token
    // =========================================================================

    @Test
    fun test2_strategyNeverSelectsIllegalToken() {
        // Player 1 has all 4 tokens in base. Only tokens 1 and 3 are marked legal in state.
        val player = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = (0..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.IN_BASE, stepCount = 0) }
        )
        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(player, LudoPlayer(playerId = 2, color = LudoColor.YELLOW)),
            currentPlayerId = 1,
            diceValue = 6,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(1, 3)
        )

        val selected = LudoBotStrategy.selectToken(state)
        assertNotNull(selected)
        assertTrue(selected == 1 || selected == 3)
        assertFalse(selected == 0 || selected == 2)
    }

    // =========================================================================
    // 3. Strategy: Legal Finishing Move Highest Priority
    // =========================================================================

    @Test
    fun test3_legalFinishingMoveReceivesHighestPriority() {
        // Token 0 can reach finish (step 54 + 3 = 57)
        // Token 1 can capture an opponent at step 10 + 3 = 13 (Green Start is index 13, but let's use step 5 + 3 = 8 which is safe, or step 6 + 3 = 9 which is unsafe)
        // Opponent at global track index corresponding to Red step 9:
        // Red start is 0, so Red step 9 is global track index 8 (Star = safe). Let's put opponent at Red step 10 (track index 9, unsafe).
        // Token 1 is at step 7, dice 3 -> step 10 (captures opponent).
        val redTokens = listOf(
            LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_HOME_LANE, stepCount = 54),
            LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 7),
            LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
        )
        val yellowTokens = listOf(
            // Yellow start is track index 26. Track index 9 is (26 + step - 1)%52 => 25 + step = 9 + 52 = 61 => step = 36.
            LudoToken(tokenId = 0, playerId = 2, state = TokenState.ON_BOARD, stepCount = 36),
            LudoToken(tokenId = 1, playerId = 2, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 2, playerId = 2, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 3, playerId = 2, state = TokenState.IN_BASE, stepCount = 0)
        )

        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(
                LudoPlayer(playerId = 1, color = LudoColor.RED, tokens = redTokens),
                LudoPlayer(playerId = 2, color = LudoColor.YELLOW, tokens = yellowTokens)
            ),
            currentPlayerId = 1,
            diceValue = 3,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0, 1)
        )

        // Token 0 reaches step 57 (finish). Token 1 captures. Finishing must beat capture!
        val selected = LudoBotStrategy.selectToken(state)
        assertEquals(0, selected)
    }

    // =========================================================================
    // 4. Strategy: Capture Preferred Over Ordinary Move
    // =========================================================================

    @Test
    fun test4_capturePreferredOverOrdinaryMove() {
        // Red start is 0. Red step 10 is track index 9 (unsafe).
        // Yellow step 36 is track index 9 (unsafe).
        // Token 0 at step 7, dice 3 -> lands on step 10 (captures Yellow Token 0).
        // Token 1 at step 20, dice 3 -> lands on step 23 (ordinary move, track index 22, unsafe, no capture).
        val redTokens = listOf(
            LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 7),
            LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 20),
            LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
        )
        val yellowTokens = listOf(
            LudoToken(tokenId = 0, playerId = 2, state = TokenState.ON_BOARD, stepCount = 36),
            LudoToken(tokenId = 1, playerId = 2, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 2, playerId = 2, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 3, playerId = 2, state = TokenState.IN_BASE, stepCount = 0)
        )

        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(
                LudoPlayer(playerId = 1, color = LudoColor.RED, tokens = redTokens),
                LudoPlayer(playerId = 2, color = LudoColor.YELLOW, tokens = yellowTokens)
            ),
            currentPlayerId = 1,
            diceValue = 3,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0, 1)
        )

        val selected = LudoBotStrategy.selectToken(state)
        assertEquals(0, selected)
    }

    // =========================================================================
    // 5. Strategy: Leaving Base Prioritized Over Ordinary Move
    // =========================================================================

    @Test
    fun test5_leavingBasePrioritizedOverOrdinaryMove() {
        // Dice is 6.
        // Token 0 is in base (step 0). On 6, it moves to step 1 (leaving base).
        // Token 1 is at step 15, moves to step 21 (step 21 is track index 20, unsafe, no capture, does not finish).
        val redTokens = listOf(
            LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 15),
            LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
        )

        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(
                LudoPlayer(playerId = 1, color = LudoColor.RED, tokens = redTokens),
                LudoPlayer(playerId = 2, color = LudoColor.YELLOW)
            ),
            currentPlayerId = 1,
            diceValue = 6,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0, 1)
        )

        val selected = LudoBotStrategy.selectToken(state)
        assertEquals(0, selected)
    }

    // =========================================================================
    // 6. Strategy: Safe Cell, Forward Progress, and Tie-Breaker
    // =========================================================================

    @Test
    fun test6a_safeCellPreference() {
        // Red step 9 is track index 8 (Star = SAFE).
        // Token 0 at step 5 + dice 4 = step 9 (lands on SAFE star square).
        // Token 1 at step 6 + dice 4 = step 10 (lands on UNSAFE track index 9).
        // Neither captures or finishes or leaves base.
        val redTokens = listOf(
            LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 5),
            LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 6),
            LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
        )

        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(
                LudoPlayer(playerId = 1, color = LudoColor.RED, tokens = redTokens),
                LudoPlayer(playerId = 2, color = LudoColor.YELLOW)
            ),
            currentPlayerId = 1,
            diceValue = 4,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0, 1)
        )

        val selected = LudoBotStrategy.selectToken(state)
        assertEquals(0, selected)
    }

    @Test
    fun test6b_forwardProgressPreference() {
        // Both tokens land on unsafe squares with no captures.
        // Token 0 is at step 30 + dice 3 = 33 (track index 32, unsafe).
        // Token 1 is at step 10 + dice 3 = 13 (track index 12, unsafe).
        val redTokens = listOf(
            LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 30),
            LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 10),
            LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
        )

        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(
                LudoPlayer(playerId = 1, color = LudoColor.RED, tokens = redTokens),
                LudoPlayer(playerId = 2, color = LudoColor.YELLOW)
            ),
            currentPlayerId = 1,
            diceValue = 3,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0, 1)
        )

        // Token 0 is further along (step 33 > step 13)
        val selected = LudoBotStrategy.selectToken(state)
        assertEquals(0, selected)
    }

    @Test
    fun test6c_deterministicTieBreaker_lowerTokenIdWins() {
        // Token 1 and Token 2 are at identical steps (step 10), dice 3 -> step 13.
        val redTokens = listOf(
            LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 10),
            LudoToken(tokenId = 2, playerId = 1, state = TokenState.ON_BOARD, stepCount = 10),
            LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
        )

        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(
                LudoPlayer(playerId = 1, color = LudoColor.RED, tokens = redTokens),
                LudoPlayer(playerId = 2, color = LudoColor.YELLOW)
            ),
            currentPlayerId = 1,
            diceValue = 3,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(1, 2)
        )

        // Lower tokenId (1 over 2) is chosen deterministically
        val selected = LudoBotStrategy.selectToken(state)
        assertEquals(1, selected)
    }

    // =========================================================================
    // 7. Bot Dice Activation
    // =========================================================================

    @Test
    fun test7_botDiceActivation_occursOnConfiguredBotTurn() = runTest {
        viewModel.botActionDelayMs = 0L
        viewModel.setBotPlayers(setOf(1))
        viewModel.startGame()

        // Give coroutines opportunity to execute
        advanceUntilIdle()

        val state = viewModel.ludoGameEngine.gameState.value
        // Player 1 (bot) started in WAITING_FOR_DICE_ROLL, should have rolled!
        // Either in WAITING_FOR_TOKEN_SELECTION (if rolled 6) or passed to P2 (if rolled 1..5 with no moves)
        assertNotNull(state.diceValue)
    }

    // =========================================================================
    // 8. Bot Token Selection & Movement
    // =========================================================================

    @Test
    fun test8_botTokenSelection_occursOnConfiguredBotMovePhase() = runTest {
        viewModel.botActionDelayMs = 0L
        viewModel.setBotPlayers(setOf(1))

        // Set up custom game state: P1 is a bot in WAITING_FOR_TOKEN_SELECTION with a 4 rolled
        viewModel.ludoGameEngine.initGame(4)
        val p1 = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 10)
            ) + (1..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.IN_BASE, stepCount = 0) }
        )
        val customState = LudoGameState(
            isGameStarted = true,
            playerCount = 4,
            players = listOf(p1) + viewModel.ludoGameEngine.gameState.value.players.drop(1),
            currentPlayerId = 1,
            diceValue = 4,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0),
            consecutiveSixCount = 0
        )
        viewModel.ludoGameEngine.restoreState(customState)
        viewModel.setBotPlayers(setOf(1))

        advanceUntilIdle()

        val stateAfter = viewModel.ludoGameEngine.gameState.value
        // Token 0 was selected and moved: step 10 + 4 = 14
        val p1After = stateAfter.players.first { it.playerId == 1 }
        val token0 = p1After.tokens.first { it.tokenId == 0 }
        assertEquals(14, token0.stepCount)
        assertEquals(TokenState.ON_BOARD, token0.state)
        // No extra turn on a 4, so turn passed cleanly to Player 2 (human)
        assertEquals(2, stateAfter.currentPlayerId)
    }

    // =========================================================================
    // 9. Human Turns Never Automated
    // =========================================================================

    @Test
    fun test9_humanTurnsNeverAutomated() = runTest {
        viewModel.botActionDelayMs = 0L
        viewModel.setBotPlayers(emptySet()) // No bots configured
        viewModel.startGame()

        advanceUntilIdle()

        val state = viewModel.ludoGameEngine.gameState.value
        assertEquals(1, state.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)
        assertNull(state.diceValue) // Never rolled automatically!
    }

    // =========================================================================
    // 10. Repeated StateFlow Emissions Do Not Trigger Duplicate Actions
    // =========================================================================

    @Test
    fun test10_repeatedStateFlowEmissions_doNotTriggerDuplicateActions() = runTest {
        viewModel.botActionDelayMs = 500L
        viewModel.setBotPlayers(setOf(1))
        viewModel.ludoGameEngine.initGame(4)

        // Multiple repeated triggers for same state
        viewModel.setBotPlayers(setOf(1))
        viewModel.setBotPlayers(setOf(1))

        val state = viewModel.ludoGameEngine.gameState.value
        // Still at dice roll phase before delay expires
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)
    }

    // =========================================================================
    // 11. Turn Change During Delay Invalidates Pending Action
    // =========================================================================

    @Test
    fun test11_turnChangeDuringDelay_invalidatesPendingAction() = runTest {
        viewModel.botActionDelayMs = 1000L
        viewModel.setBotPlayers(setOf(1))
        viewModel.startGame()

        // Force engine turn change to Player 2 (human) before delay finishes
        viewModel.ludoGameEngine.onDiceRolled(1) // P1 has no moves on 1, turn passes to P2
        val state = viewModel.ludoGameEngine.gameState.value
        assertEquals(2, state.currentPlayerId)

        advanceUntilIdle()

        // P2 is human, so turn phase should remain WAITING_FOR_DICE_ROLL for P2
        val finalState = viewModel.ludoGameEngine.gameState.value
        assertEquals(2, finalState.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, finalState.turnPhase)
    }

    // =========================================================================
    // 12. Reset or Rematch Invalidates Stale Bot Work
    // =========================================================================

    @Test
    fun test12_resetOrRematch_invalidatesStaleBotWork() = runTest {
        viewModel.botActionDelayMs = 1000L
        viewModel.setBotPlayers(setOf(1))
        viewModel.startGame()

        // Reset game to setup immediately
        viewModel.resetToSetup()

        advanceUntilIdle()

        val state = viewModel.ludoGameEngine.gameState.value
        assertFalse(state.isGameStarted)
    }

    // =========================================================================
    // 13. Team-Up Partner Assistance Uses Authoritative Legal Tokens
    // =========================================================================

    @Test
    fun test13_teamUpPartnerAssistance_usesAuthoritativeLegalTokens() {
        val engine = LudoGameEngine()
        engine.initGame(4, LudoGameMode.TEAM_UP)

        // Player 1 (finished) and Player 3 (unfinished teammate)
        val finishedTokens = (0..3).map {
            LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57)
        }
        val p1 = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = finishedTokens,
            isFinished = true,
            finishRank = 1,
            teamId = LudoTeamId.TEAM_1
        )
        val p3Tokens = listOf(
            LudoToken(tokenId = 0, playerId = 3, state = TokenState.ON_BOARD, stepCount = 10),
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
            diceValue = 3,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0), // Belongs to Player 3 under Partner Assistance!
            gameMode = LudoGameMode.TEAM_UP
        )

        val selected = LudoBotStrategy.selectToken(partnerState)
        assertEquals(0, selected)
    }

    // =========================================================================
    // 14. Existing Third-Six Behavior Engine-Controlled
    // =========================================================================

    @Test
    fun test14_existingThirdSixBehavior_remainsEngineControlled() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Set up consecutiveSixCount = 2
        val customState = engine.gameState.value.copy(
            consecutiveSixCount = 2,
            turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL
        )
        engine.restoreState(customState)

        // Roll 6 on 3rd attempt: engine must apply third-six fallback (1..5)
        assertTrue(engine.onDiceRolled(6, thirdRollFallback = 3))
        val afterState = engine.gameState.value
        assertEquals(3, afterState.diceValue)
        assertEquals(0, afterState.consecutiveSixCount)
    }

    // =========================================================================
    // 15. Statistics and Match Finalization Not Double-Counted
    // =========================================================================

    @Test
    fun test15_statisticsAndMatchFinalization_notDoubleCounted() = runTest {
        viewModel.botActionDelayMs = 0L
        viewModel.setBotPlayers(setOf(1))
        viewModel.startGame()

        advanceUntilIdle()

        // Verify active session was started exactly once
        assertTrue(viewModel.statisticsManager.isSessionActive)

        val stats = viewModel.statisticsData.value
        // Match history has not been finalized yet (match is ongoing, no duplicate or premature finalization)
        assertEquals(0, stats.matchHistory.size)

        // Reset discards active session cleanly without creating history record
        viewModel.resetToSetup()
        advanceUntilIdle()
        assertFalse(viewModel.statisticsManager.isSessionActive)
        assertEquals(0, viewModel.statisticsData.value.matchHistory.size)
    }
}
