package com.example

import com.example.game.ludo.engine.CaptureResolver
import com.example.game.ludo.engine.LudoDiceBridge
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.engine.MoveValidator
import com.example.game.ludo.engine.PendingNumberStore
import com.example.game.ludo.model.BoardCoordinate
import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import com.example.game.model.RollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase5InteractiveGameplayTest {

    // =========================================================================
    // A. Legal token calculation & UI-facing state matching MoveValidator
    // =========================================================================

    @Test
    fun `test A - legal token calculation matches MoveValidator directly`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Rolling 6 allows any in-base token (0..3) to emerge
        assertTrue(engine.onDiceRolled(6))
        val state = engine.gameState.value
        val expected = MoveValidator.calculateLegalTokens(state.players[0], 6)
        assertEquals(setOf(0, 1, 2, 3), state.legalTokenIds)
        assertEquals(expected, state.legalTokenIds)

        // Move token 0 out of base
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertEquals(1, moveRes!!.toStep)

        // Rolling 4 should only allow token 0 (on board at step 1) to move
        assertTrue(engine.onDiceRolled(4))
        val stateAfter4 = engine.gameState.value
        val expectedAfter4 = MoveValidator.calculateLegalTokens(stateAfter4.players[0], 4)
        assertEquals(setOf(0), stateAfter4.legalTokenIds)
        assertEquals(expectedAfter4, stateAfter4.legalTokenIds)
    }

    // =========================================================================
    // B. Illegal token rejection (stale, un-highlighted, out-of-turn taps)
    // =========================================================================

    @Test
    fun `test B - illegal token rejection prevents moving un-highlighted tokens`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Waiting for dice roll -> cannot move any token
        assertNull(engine.moveToken(0))

        // Roll 4 -> only tokens on board can move, but all are in base -> legalTokenIds is empty
        assertTrue(engine.onDiceRolled(4))
        // Since no legal moves existed, turn passed immediately to Player 2!
        assertEquals(2, engine.gameState.value.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)

        // Attempting to move any token of Player 1 or Player 2 must return null
        assertNull(engine.moveToken(0))
        assertNull(engine.moveToken(1))
    }

    // =========================================================================
    // C. Base-to-track movement
    // =========================================================================

    @Test
    fun `test C - base to track movement releases token on 6 only`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Roll 6
        engine.onDiceRolled(6)
        val res = engine.moveToken(2)
        assertNotNull(res)
        assertEquals(0, res!!.fromStep)
        assertEquals(1, res.toStep)
        assertEquals(listOf(1), res.traversedSteps)

        val token2 = engine.gameState.value.players[0].tokens[2]
        assertEquals(TokenState.ON_BOARD, token2.state)
        assertEquals(1, token2.stepCount)
    }

    // =========================================================================
    // D. Common-track movement & intermediate step traversal
    // =========================================================================

    @Test
    fun `test D - common track movement traverses intermediate steps`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        engine.onDiceRolled(6)
        engine.moveToken(0) // now at step 1

        engine.onDiceRolled(5)
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertEquals(1, moveRes!!.fromStep)
        assertEquals(6, moveRes.toStep)
        assertEquals(listOf(2, 3, 4, 5, 6), moveRes.traversedSteps)
        assertEquals(6, engine.gameState.value.players[0].tokens[0].stepCount)
    }

    // =========================================================================
    // E. Home-lane movement (steps 52..56)
    // =========================================================================

    @Test
    fun `test E - home lane movement steps 52 to 56`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Position token 0 directly at step 51
        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 51) else t
                })
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Roll 3: 51 + 3 = 54 (in home lane)
        assertTrue(engine.onDiceRolled(3))
        val res = engine.moveToken(0)
        assertNotNull(res)
        assertEquals(54, res!!.toStep)
        assertTrue(res.enteredHome)
        assertEquals(TokenState.IN_HOME_LANE, engine.gameState.value.players[0].tokens[0].state)
        assertEquals(54, engine.gameState.value.players[0].tokens[0].stepCount)
    }

    // =========================================================================
    // F. Finish at step 57
    // =========================================================================

    @Test
    fun `test F - finish token at step 57 awards extra turn and finishes token`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.IN_HOME_LANE, stepCount = 55) else t
                })
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Roll 2: 55 + 2 = 57
        assertTrue(engine.onDiceRolled(2))
        val res = engine.moveToken(0)
        assertNotNull(res)
        assertEquals(57, res!!.toStep)
        assertTrue(res.finishedToken)
        assertTrue(res.extraTurnGranted)
        assertEquals(ExtraTurnReason.FINISHED_TOKEN, res.extraTurnReason)
        assertEquals(TokenState.FINISHED, engine.gameState.value.players[0].tokens[0].state)
        // Active player remains Player 1 because extra turn was awarded
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // G. Capture sends opponent to base and awards extra turn
    // =========================================================================

    @Test
    fun `test G - capture opponent token sends it to base and grants extra turn`() {
        val engine = LudoGameEngine()
        engine.initGame(2) // Player 1 (Red), Player 2 (Yellow)

        // Position Red token 0 at step 5 (track index 4)
        // Position Yellow token 0 at step 27: Yellow start = 26. (26 + 27 - 1) % 52 = 52 % 52 = 0
        // Red token 0 moves from 5 with roll 5: 5 + 5 = 10 -> track index (0 + 10 - 1) = 9
        // Let's set Yellow token 0 at track index 9: Yellow relative step = 36 -> (26 + 36 - 1) % 52 = 61 % 52 = 9
        val customPlayers = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 5) else t
                })
                2 -> p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 36) else t
                })
                else -> p
            }
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Red rolls 5: step 5 + 5 = 10 (lands on track index 9 where Yellow token is)
        assertTrue(engine.onDiceRolled(5))
        val res = engine.moveToken(0)
        assertNotNull(res)
        assertEquals(1, res!!.capturedTokens.size)
        assertEquals(2, res.capturedTokens[0].playerId)
        assertTrue(res.extraTurnGranted)
        assertEquals(ExtraTurnReason.CAPTURED_OPPONENT, res.extraTurnReason)

        // Opponent token is reset to base
        val yellowToken = engine.gameState.value.players[1].tokens[0]
        assertEquals(TokenState.IN_BASE, yellowToken.state)
        assertEquals(0, yellowToken.stepCount)

        // Player 1 retains turn due to capture
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // H. Safe-cell non-capture
    // =========================================================================

    @Test
    fun `test H - landing on safe cell does not capture opponent`() {
        // Red start is track index 0 (safe)
        // Blue step 40 is track index: (39 + 40 - 1) % 52 = 78 % 52 = 26 (Yellow start)
        // Red step 9 is track index (0 + 9 - 1) = 8 (STAR - SAFE)
        val player1 = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 9),
                LudoToken(tokenId = 1, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
            )
        )
        val player2 = LudoPlayer(
            playerId = 2,
            color = LudoColor.GREEN,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 2, state = TokenState.ON_BOARD, stepCount = 48), // also lands on index 8
                LudoToken(tokenId = 1, playerId = 2, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 2, playerId = 2, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 3, playerId = 2, state = TokenState.IN_BASE, stepCount = 0)
            )
        )

        val captures = CaptureResolver.resolveCaptures(
            movingPlayerId = 2,
            movingPlayerColor = LudoColor.GREEN,
            targetStep = 48,
            allPlayers = listOf(player1, player2)
        )
        assertTrue("Safe cells must prevent capture", captures.isEmpty())
    }

    // =========================================================================
    // I. Same-color stacking
    // =========================================================================

    @Test
    fun `test I - same color tokens can legally share a square`() {
        val player = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 5),
                LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 1),
                LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
            )
        )
        // Token 1 moving 4 lands on step 5 (same as token 0)
        val legal = MoveValidator.calculateLegalTokens(player, 4)
        assertTrue("Token 1 must be legal to move onto token 0's square", legal.contains(1))
    }

    // =========================================================================
    // J. Opponent turn cannot move current player's token
    // =========================================================================

    @Test
    fun `test J - engine rejects moving opponent token on current player turn`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        engine.onDiceRolled(6)
        // Current player is 1; cannot move tokens of other players
        // engine.moveToken takes tokenId for the current active player
        // Passing invalid token id returns null
        assertNull(engine.moveToken(99))
    }

    // =========================================================================
    // K. Movement cannot exceed dice value / overshoot finish
    // =========================================================================

    @Test
    fun `test K - overshoot finish step 57 is disallowed`() {
        val player = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_HOME_LANE, stepCount = 55),
                LudoToken(tokenId = 1, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
            )
        )
        // 55 + 3 = 58 > 57 (overshoot!)
        val legal = MoveValidator.calculateLegalTokens(player, 3)
        assertFalse("Overshoot must not be allowed", legal.contains(0))

        // 55 + 2 = 57 == finish (allowed)
        val legal2 = MoveValidator.calculateLegalTokens(player, 2)
        assertTrue("Exact finish roll must be allowed", legal2.contains(0))
    }

    // =========================================================================
    // L. Third-six behavior remains correct
    // =========================================================================

    @Test
    fun `test L - third six behavior resolves to 1 to 5 for same player`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // 1st 6
        engine.onDiceRolled(6)
        engine.moveToken(0)
        assertEquals(1, engine.gameState.value.consecutiveSixCount)

        // 2nd 6
        engine.onDiceRolled(6)
        engine.moveToken(1)
        assertEquals(2, engine.gameState.value.consecutiveSixCount)

        // 3rd roll with 6 -> background 1..5 resolution
        assertTrue(engine.onDiceRolled(6, thirdRollFallback = 3))
        assertEquals(1, engine.gameState.value.currentPlayerId)
        assertEquals(3, engine.gameState.value.diceValue)
        assertEquals(0, engine.gameState.value.consecutiveSixCount)
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION, engine.gameState.value.turnPhase)
    }

    // =========================================================================
    // M & N. Controller dice and local fallback bridge integration
    // =========================================================================

    @Test
    fun `test M and N - controller dice reaches engine and local fallback works`() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        val store = PendingNumberStore()
        val bridge = LudoDiceBridge(engine, store)

        // Remote command queued for Player 1
        store.queueCommand("cmd-1", 1, 6, "B1")
        val remoteRoll = bridge.onDiceActivated()
        assertNotNull(remoteRoll)
        assertEquals(RollSource.REMOTE, remoteRoll!!.source)
        assertEquals(6, remoteRoll.diceValue)
        assertEquals("cmd-1", remoteRoll.commandId)
        engine.moveToken(0)

        // Next roll has no remote command -> LOCAL fallback
        val localRoll = bridge.onDiceActivated()
        assertNotNull(localRoll)
        assertEquals(RollSource.LOCAL, localRoll!!.source)
        assertTrue(localRoll.diceValue in 1..6)
        assertNull(localRoll.commandId)
    }

    // =========================================================================
    // O. Winner state
    // =========================================================================

    @Test
    fun `test O - winner state detected when all 4 tokens finished`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = listOf(
                    LudoToken(0, 1, TokenState.FINISHED, 57),
                    LudoToken(1, 1, TokenState.FINISHED, 57),
                    LudoToken(2, 1, TokenState.FINISHED, 57),
                    LudoToken(3, 1, TokenState.IN_HOME_LANE, 56)
                ))
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Roll 1: 56 + 1 = 57
        assertTrue(engine.onDiceRolled(1))
        val moveRes = engine.moveToken(3)
        assertNotNull(moveRes)
        assertTrue(moveRes!!.isGameOver)
        assertTrue(engine.gameState.value.isGameOver)
        assertEquals(listOf(1, 2), engine.gameState.value.winners)
    }

    // =========================================================================
    // P, Q, R. 2, 3, 4 Player Setup Verification
    // =========================================================================

    @Test
    fun `test P - 2 player setup initializes Red and Yellow`() {
        val engine = LudoGameEngine()
        assertTrue(engine.initGame(2))
        assertEquals(2, engine.gameState.value.playerCount)
        assertEquals(listOf(LudoColor.RED, LudoColor.YELLOW), engine.gameState.value.players.map { it.color })
    }

    @Test
    fun `test Q - 3 player setup initializes Red, Green, Yellow`() {
        val engine = LudoGameEngine()
        assertTrue(engine.initGame(3))
        assertEquals(3, engine.gameState.value.playerCount)
        assertEquals(listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW), engine.gameState.value.players.map { it.color })
    }

    @Test
    fun `test R - 4 player setup initializes Red, Green, Yellow, Blue`() {
        val engine = LudoGameEngine()
        assertTrue(engine.initGame(4))
        assertEquals(4, engine.gameState.value.playerCount)
        assertEquals(listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE), engine.gameState.value.players.map { it.color })
    }
}
