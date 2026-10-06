package com.example

import com.example.game.ludo.engine.CaptureResolver
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.engine.MoveValidator
import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
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
class LudoPhase51TurnExtraTurnAuditTest {

    // =========================================================================
    // 1. SIX WITH NO LEGAL MOVES
    // =========================================================================

    @Test
    fun `audit 1 - six with no legal moves passes turn and resets consecutiveSixCount`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Setup: all tokens for Player 1 are near home, e.g. at step 55
        // On rolling 6: 55 + 6 = 61 > 57 (overshoot), so NO legal moves!
        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = (0..3).map { LudoToken(it, 1, TokenState.IN_HOME_LANE, 55) })
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Roll 6 with 0 legal moves
        assertTrue(engine.onDiceRolled(6))
        val state = engine.gameState.value

        assertEquals("No legal moves on 6 must advance turn to next player", 2, state.currentPlayerId)
        assertEquals("Waiting for dice roll", TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)
        assertEquals("consecutiveSixCount must reset to 0", 0, state.consecutiveSixCount)
        assertTrue("legalTokenIds must be empty", state.legalTokenIds.isEmpty())
    }

    // =========================================================================
    // 2. SECOND SIX WITH NO LEGAL MOVES
    // =========================================================================

    @Test
    fun `audit 2 - second six with no legal moves passes turn and resets consecutiveSixCount`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Player 1 has 3 tokens finished at 57, and 1 token at 55
        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = listOf(
                    LudoToken(0, 1, TokenState.IN_HOME_LANE, 51), // will move to step 1 + 6 = 57 on first roll
                    LudoToken(1, 1, TokenState.IN_HOME_LANE, 55),
                    LudoToken(2, 1, TokenState.IN_HOME_LANE, 55),
                    LudoToken(3, 1, TokenState.IN_HOME_LANE, 55)
                ))
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // 1st 6: token 0 moves 51 + 6 = 57 (finishes)
        assertTrue(engine.onDiceRolled(6))
        engine.moveToken(0)
        assertEquals(1, engine.gameState.value.currentPlayerId)
        assertEquals(1, engine.gameState.value.consecutiveSixCount)

        // 2nd roll is 6: remaining tokens at 55 cannot move on 6 (overshoot)
        assertTrue(engine.onDiceRolled(6))
        val state = engine.gameState.value
        assertEquals("Second six with no legal moves must advance turn to Player 2", 2, state.currentPlayerId)
        assertEquals(0, state.consecutiveSixCount)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)
    }

    // =========================================================================
    // 3. THIRD SIX WITH NO LEGAL MOVES ON FALLBACK
    // =========================================================================

    @Test
    fun `audit 3 - third six background fallback with no legal moves passes turn safely`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Roll 1: 6
        engine.onDiceRolled(6)
        engine.moveToken(0)
        assertEquals(1, engine.gameState.value.consecutiveSixCount)

        // Roll 2: 6
        engine.onDiceRolled(6)
        engine.moveToken(1)
        assertEquals(2, engine.gameState.value.consecutiveSixCount)

        // Position all tokens such that fallback of 4 has no legal moves
        // E.g. token 0 and 1 are at 55 (overshoot on 4), token 2 and 3 in base (cannot move on 4)
        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = listOf(
                    LudoToken(0, 1, TokenState.IN_HOME_LANE, 55),
                    LudoToken(1, 1, TokenState.IN_HOME_LANE, 55),
                    LudoToken(2, 1, TokenState.IN_BASE, 0),
                    LudoToken(3, 1, TokenState.IN_BASE, 0)
                ))
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Third roll: 6 (fallback = 4)
        assertTrue(engine.onDiceRolled(6, thirdRollFallback = 4))
        val state = engine.gameState.value
        assertEquals("Fallback 4 had no legal moves: turn must advance to Player 2", 2, state.currentPlayerId)
        assertEquals(0, state.consecutiveSixCount)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)
    }

    // =========================================================================
    // 4. SIX + CAPTURE PRECEDENCE
    // =========================================================================

    @Test
    fun `audit 4 - six plus capture grants CAPTURED_OPPONENT extra turn and retains turn`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Red token 0 at step 5
        // Yellow token 0 at step 37 (track index 10: (26 + 37 - 1) % 52 = 62 % 52 = 10)
        // Red from step 5 on roll 6: 5 + 6 = 11 -> track index (0 + 11 - 1) = 10 (same tile!)
        val customPlayers = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 5) else t
                })
                2 -> p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 37) else t
                })
                else -> p
            }
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        assertTrue(engine.onDiceRolled(6))
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertTrue(moveRes!!.extraTurnGranted)
        assertEquals(ExtraTurnReason.CAPTURED_OPPONENT, moveRes.extraTurnReason)
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // 5. SIX + FINISH PRECEDENCE
    // =========================================================================

    @Test
    fun `audit 5 - six plus finish grants FINISHED_TOKEN extra turn and retains turn`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Red token 0 at step 51. Roll 6: 51 + 6 = 57 (FINISH)
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

        assertTrue(engine.onDiceRolled(6))
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertTrue(moveRes!!.extraTurnGranted)
        assertEquals(ExtraTurnReason.FINISHED_TOKEN, moveRes.extraTurnReason)
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // 6. THIRD-SIX FALLBACK + CAPTURE
    // =========================================================================

    @Test
    fun `audit 6 - third-six background fallback plus capture grants extra turn`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Roll 1: 6
        engine.onDiceRolled(6)
        engine.moveToken(0)

        // Roll 2: 6
        engine.onDiceRolled(6)
        engine.moveToken(1)

        // Setup for capture on fallback 3:
        // Red token 0 at step 5. Red + 3 = 8 (track index 7)
        // Yellow token 0 at track index 7: (26 + 34 - 1) % 52 = 59 % 52 = 7 -> step 34
        val customPlayers = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 5) else t
                })
                2 -> p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 34) else t
                })
                else -> p
            }
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Third roll: 6, resolved to fallback 3
        assertTrue(engine.onDiceRolled(6, thirdRollFallback = 3))
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertTrue(moveRes!!.extraTurnGranted)
        assertEquals(ExtraTurnReason.CAPTURED_OPPONENT, moveRes.extraTurnReason)
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // 7. THIRD-SIX FALLBACK + FINISH
    // =========================================================================

    @Test
    fun `audit 7 - third-six background fallback plus finish grants FINISHED_TOKEN extra turn`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Roll 1: 6
        engine.onDiceRolled(6)
        engine.moveToken(0)

        // Roll 2: 6
        engine.onDiceRolled(6)
        engine.moveToken(1)

        // Setup token 0 at step 54. Fallback 3: 54 + 3 = 57 (FINISH)
        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.IN_HOME_LANE, stepCount = 54) else t
                })
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        assertTrue(engine.onDiceRolled(6, thirdRollFallback = 3))
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertTrue(moveRes!!.extraTurnGranted)
        assertEquals(ExtraTurnReason.FINISHED_TOKEN, moveRes.extraTurnReason)
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // 8 & 9. CAPTURE & FINISH GRANT EXTRA TURN EXACTLY ONCE
    // =========================================================================

    @Test
    fun `audit 8 and 9 - extra turn granted exactly once on non-six move`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Red token at 55. Roll 2: finishes token
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

        assertTrue(engine.onDiceRolled(2))
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertTrue(moveRes!!.extraTurnGranted)
        assertEquals(1, engine.gameState.value.currentPlayerId)

        // Active player rolls 4 (extra turn roll) with no legal moves -> turn passes to Player 2
        assertTrue(engine.onDiceRolled(4))
        assertEquals(2, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // 10. FOURTH-TOKEN FINISH TRIGGERS GAME OVER WITHOUT EXTRA TURN
    // =========================================================================

    @Test
    fun `audit 10 - fourth token finish finishes game without extra turn`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Player 1 has 3 tokens finished at 57, and token 3 at 55
        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = listOf(
                    LudoToken(0, 1, TokenState.FINISHED, 57),
                    LudoToken(1, 1, TokenState.FINISHED, 57),
                    LudoToken(2, 1, TokenState.FINISHED, 57),
                    LudoToken(3, 1, TokenState.IN_HOME_LANE, 55)
                ))
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Roll 2 to finish last token
        assertTrue(engine.onDiceRolled(2))
        val moveRes = engine.moveToken(3)
        assertNotNull(moveRes)
        assertTrue(moveRes!!.finishedToken)
        assertTrue(moveRes.isGameOver)
        assertFalse("Finished player must not get extra turn", moveRes.extraTurnGranted)
        assertTrue(engine.gameState.value.isGameOver)
        assertEquals(listOf(1, 2), engine.gameState.value.winners)
        assertEquals(TurnPhase.GAME_OVER, engine.gameState.value.turnPhase)
    }

    // =========================================================================
    // 11. DOUBLE MOVE PREVENTION
    // =========================================================================

    @Test
    fun `audit 11 - moving once consumes turn phase and rejects second move`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        engine.onDiceRolled(6)
        val firstMove = engine.moveToken(0)
        assertNotNull(firstMove)

        // After first move, turnPhase is WAITING_FOR_DICE_ROLL
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)
        assertTrue(engine.gameState.value.legalTokenIds.isEmpty())

        // Calling moveToken again without rolling dice must be rejected
        val secondMove = engine.moveToken(0)
        assertNull(secondMove)
    }
}
