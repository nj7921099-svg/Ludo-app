package com.example

import com.example.game.ludo.engine.CaptureResolver
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.engine.MoveValidator
import com.example.game.ludo.model.BoardCoordinate
import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoBoardCoordinates
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
class LudoCoreEngineTest {

    // =========================================================================
    // Category A: Initialization
    // =========================================================================

    @Test
    fun `test A1 - 2 players initializes canonical opposite colors with 4 tokens each`() {
        val engine = LudoGameEngine()
        assertTrue(engine.initGame(2))

        val state = engine.gameState.value
        assertTrue(state.isGameStarted)
        assertEquals(2, state.playerCount)
        assertEquals(2, state.players.size)

        // 2 players use RED and YELLOW (canonical opposite quadrants)
        assertEquals(LudoColor.RED, state.players[0].color)
        assertEquals(LudoColor.YELLOW, state.players[1].color)

        state.players.forEach { player ->
            assertEquals(4, player.tokens.size)
            player.tokens.forEach { token ->
                assertEquals(TokenState.IN_BASE, token.state)
                assertEquals(0, token.stepCount)
            }
        }
        assertEquals(1, state.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)
    }

    @Test
    fun `test A2 - 3 and 4 players initialize correctly with exactly 4 tokens each`() {
        val engine3 = LudoGameEngine()
        assertTrue(engine3.initGame(3))
        assertEquals(3, engine3.gameState.value.playerCount)
        assertEquals(listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW), engine3.gameState.value.players.map { it.color })

        val engine4 = LudoGameEngine()
        assertTrue(engine4.initGame(4))
        assertEquals(4, engine4.gameState.value.playerCount)
        assertEquals(
            listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE),
            engine4.gameState.value.players.map { it.color }
        )
        engine4.gameState.value.players.forEach {
            assertEquals(4, it.tokens.size)
        }
    }

    // =========================================================================
    // Category B: Base Entry
    // =========================================================================

    @Test
    fun `test B1 - roll 1 to 5 cannot release token from base`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Player 1 rolls 5
        assertTrue(engine.onDiceRolled(5))

        // All tokens in base: no legal moves! Turn automatically passes to Player 2
        val state = engine.gameState.value
        assertEquals(2, state.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)
        assertTrue(state.players[0].tokens.all { it.isInBase })
    }

    @Test
    fun `test B2 - roll 6 releases token from base to step 1`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Player 1 rolls 6
        assertTrue(engine.onDiceRolled(6))
        val stateAfterRoll = engine.gameState.value
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION, stateAfterRoll.turnPhase)
        assertEquals(setOf(0, 1, 2, 3), stateAfterRoll.legalTokenIds)

        // Player 1 moves token 0
        val moveResult = engine.moveToken(0)
        assertNotNull(moveResult)
        assertEquals(0, moveResult!!.fromStep)
        assertEquals(1, moveResult.toStep)
        assertEquals(TokenState.ON_BOARD, engine.gameState.value.players[0].tokens[0].state)
        assertEquals(1, engine.gameState.value.players[0].tokens[0].stepCount)

        // Rolling 6 grants an extra turn: active player remains Player 1!
        assertEquals(1, engine.gameState.value.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)
    }

    // =========================================================================
    // Category C: Normal Movement
    // =========================================================================

    @Test
    fun `test C1 - step 1 plus 4 becomes step 5`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Release token 0 to step 1
        engine.onDiceRolled(6)
        engine.moveToken(0)

        // Next roll (extra turn) is 4
        assertTrue(engine.onDiceRolled(4))
        assertEquals(setOf(0), engine.gameState.value.legalTokenIds)

        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertEquals(1, moveRes!!.fromStep)
        assertEquals(5, moveRes.toStep)
        assertEquals(listOf(2, 3, 4, 5), moveRes.traversedSteps)
        assertEquals(5, engine.gameState.value.players[0].tokens[0].stepCount)

        // Non-6 roll with no captures advances turn to Player 2
        assertEquals(2, engine.gameState.value.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)
    }

    // =========================================================================
    // Category D: Overshoot Prevention & Finishing
    // =========================================================================

    @Test
    fun `test D1 - token at step 55 cannot move on roll 3, but moves on 2`() {
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

        // Roll 3: 55 + 3 = 58 > 57 (illegal overshoot!)
        val legalOn3 = MoveValidator.calculateLegalTokens(player, 3)
        assertTrue("55 + 3 > 57 must have no legal moves", legalOn3.isEmpty())

        // Roll 2: 55 + 2 = 57 (exact finish!)
        val legalOn2 = MoveValidator.calculateLegalTokens(player, 2)
        assertEquals(setOf(0), legalOn2)
    }

    @Test
    fun `test D2 - token at step 56 finishes exactly on roll 1`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Put Player 1 token 0 at step 56
        val playersWithNearHome = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.IN_HOME_LANE, stepCount = 56) else t
                })
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = playersWithNearHome)

        // Player 1 rolls 1
        assertTrue(engine.onDiceRolled(1))
        assertEquals(setOf(0), engine.gameState.value.legalTokenIds)

        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertEquals(57, moveRes!!.toStep)
        assertTrue(moveRes.finishedToken)
        assertEquals(TokenState.FINISHED, engine.gameState.value.players[0].tokens[0].state)

        // Finishing a token awards an extra turn!
        assertTrue(moveRes.extraTurnGranted)
        assertEquals(ExtraTurnReason.FINISHED_TOKEN, moveRes.extraTurnReason)
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // Category E: Safe Cells (No Capture)
    // =========================================================================

    @Test
    fun `test E1 - landing on an opponent on a safe cell does not capture`() {
        val player1 = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 9), // track index 8 (STAR)
                LudoToken(tokenId = 1, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_BASE, stepCount = 0)
            )
        )
        val player2 = LudoPlayer(
            playerId = 2,
            color = LudoColor.GREEN,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 2, state = TokenState.ON_BOARD, stepCount = 48), // also lands on track index 8
                LudoToken(tokenId = 1, playerId = 2, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 2, playerId = 2, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 3, playerId = 2, state = TokenState.IN_BASE, stepCount = 0)
            )
        )

        // Player 2 moves to track index 8 (star safe cell)
        // For Green (startOffset = 13): step 48 -> (13 + 48 - 1) % 52 = 60 % 52 = 8
        val captures = CaptureResolver.resolveCaptures(
            movingPlayerId = 2,
            movingPlayerColor = LudoColor.GREEN,
            targetStep = 48,
            allPlayers = listOf(player1, player2)
        )

        assertTrue("Safe cell must not capture opponent token", captures.isEmpty())
    }

    // =========================================================================
    // Category F: Capture Mechanics
    // =========================================================================

    @Test
    fun `test F1 - landing on opponent on a non-safe cell sends opponent back to base and awards extra turn`() {
        val engine = LudoGameEngine()
        engine.initGame(2) // Player 1 (Red), Player 2 (Yellow)

        // Setup:
        // Red token 0 at step 5 (track index 4 - NOT safe)
        // Yellow token 0 at step 31: Yellow start = 26. (26 + 31 - 1) % 52 = 56 % 52 = 4 (same tile!)
        val customizedPlayers = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 1) else t // starts at step 1
                })
                2 -> p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 31) else t // at track index 4
                })
                else -> p
            }
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customizedPlayers)

        // Player 1 rolls 3: 1 + 3 = 4 (track index 3) -> let's roll 4: 1 + 4 = 5 (track index 4!)
        assertTrue(engine.onDiceRolled(4))
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertEquals(5, moveRes!!.toStep)

        // Capture verification
        assertEquals(1, moveRes.capturedTokens.size)
        assertEquals(2, moveRes.capturedTokens[0].playerId)
        assertEquals(0, moveRes.capturedTokens[0].tokenId)

        // Opponent token is sent back to base
        val yellowToken = engine.gameState.value.players[1].tokens[0]
        assertEquals(TokenState.IN_BASE, yellowToken.state)
        assertEquals(0, yellowToken.stepCount)

        // Extra turn awarded for capture!
        assertTrue(moveRes.extraTurnGranted)
        assertEquals(ExtraTurnReason.CAPTURED_OPPONENT, moveRes.extraTurnReason)
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // Category G: Home Lane Connection
    // =========================================================================

    @Test
    fun `test G1 - token steps 51 to 52 transitions from ON_BOARD to IN_HOME_LANE`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Put token at step 50
        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = p.tokens.mapIndexed { idx, t ->
                    if (idx == 0) t.copy(state = TokenState.ON_BOARD, stepCount = 50) else t
                })
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Roll 3: 50 + 3 = 53 (in home lane!)
        assertTrue(engine.onDiceRolled(3))
        val res = engine.moveToken(0)
        assertNotNull(res)
        assertEquals(53, res!!.toStep)
        assertTrue(res.enteredHome)
        assertEquals(TokenState.IN_HOME_LANE, engine.gameState.value.players[0].tokens[0].state)
    }

    // =========================================================================
    // Category H: Consecutive Six Rule
    // =========================================================================

    @Test
    fun `test H1 - third consecutive six forfeits the turn`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // First 6
        assertTrue(engine.onDiceRolled(6))
        engine.moveToken(0)
        assertEquals(1, engine.gameState.value.currentPlayerId)
        assertEquals(1, engine.gameState.value.consecutiveSixCount)

        // Second 6
        assertTrue(engine.onDiceRolled(6))
        engine.moveToken(1)
        assertEquals(1, engine.gameState.value.currentPlayerId)
        assertEquals(2, engine.gameState.value.consecutiveSixCount)

        // Third 6 -> turn forfeited immediately, passes to Player 2!
        assertTrue(engine.onDiceRolled(6))
        assertEquals(2, engine.gameState.value.currentPlayerId)
        assertEquals(0, engine.gameState.value.consecutiveSixCount)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)
    }

    // =========================================================================
    // Category L & M: Turn Authority & Win Detection
    // =========================================================================

    @Test
    fun `test L1 - cannot move when waiting for dice roll or out of turn`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Attempting to move without rolling dice must return null
        assertNull("Cannot move before dice is rolled", engine.moveToken(0))
    }

    @Test
    fun `test M1 - all 4 tokens finished triggers winner ranking and game over in 2-player game`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Put Player 1's tokens 0, 1, 2 in FINISHED state, and token 3 at step 56
        val customPlayers = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(tokens = listOf(
                    LudoToken(tokenId = 0, playerId = 1, state = TokenState.FINISHED, stepCount = 57),
                    LudoToken(tokenId = 1, playerId = 1, state = TokenState.FINISHED, stepCount = 57),
                    LudoToken(tokenId = 2, playerId = 1, state = TokenState.FINISHED, stepCount = 57),
                    LudoToken(tokenId = 3, playerId = 1, state = TokenState.IN_HOME_LANE, stepCount = 56)
                ))
            } else p
        }
        val reflection = engine.javaClass.getDeclaredField("_gameState")
        reflection.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = reflection.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<com.example.game.ludo.model.LudoGameState>
        stateFlow.value = engine.gameState.value.copy(players = customPlayers)

        // Player 1 rolls 1 -> moves token 3 to 57!
        assertTrue(engine.onDiceRolled(1))
        val res = engine.moveToken(3)
        assertNotNull(res)
        assertTrue(res!!.finishedToken)
        assertTrue(res.isGameOver)

        val finalState = engine.gameState.value
        assertTrue(finalState.isGameOver)
        assertEquals(listOf(1, 2), finalState.winners)
        assertEquals(1, finalState.players[0].finishRank)
        assertEquals(2, finalState.players[1].finishRank)
    }

    // =========================================================================
    // Category N: Multi-player Isolation
    // =========================================================================

    @Test
    fun `test N1 - moving player 1 token does not alter player 2 tokens`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        engine.onDiceRolled(6)
        engine.moveToken(0)

        // Player 2's tokens must be completely untouched
        val p2Tokens = engine.gameState.value.players[1].tokens
        assertEquals(4, p2Tokens.size)
        assertTrue(p2Tokens.all { it.isInBase && it.stepCount == 0 })
    }

    // =========================================================================
    // Category O: Board Mathematics Verification
    // =========================================================================

    @Test
    fun `test O1 - board topology contains exactly 52 unique common track cells`() {
        val track = LudoBoardCoordinates.commonTrack
        assertEquals("Common track must contain exactly 52 positions", 52, track.size)

        val uniqueSet = track.toSet()
        assertEquals("All 52 common track positions must be unique", 52, uniqueSet.size)

        // Check bounds
        track.forEach { coord ->
            assertTrue("col must be in 0..14", coord.col in 0..14)
            assertTrue("row must be in 0..14", coord.row in 0..14)
        }
    }

    @Test
    fun `test O2 - safe cells are exactly 8 canonical cells`() {
        assertEquals("Must have exactly 8 safe cells", 8, LudoBoardCoordinates.safeTrackIndices.size)
        assertEquals(setOf(0, 8, 13, 21, 26, 34, 39, 47), LudoBoardCoordinates.safeTrackIndices)
    }

    @Test
    fun `test O3 - home lanes contain exactly 5 unique coordinates each`() {
        LudoColor.values().forEach { color ->
            val lane = LudoBoardCoordinates.homeLanes[color]
            assertNotNull(lane)
            assertEquals("Each color home lane must have 5 cells", 5, lane!!.size)
            assertEquals("Home lane cells must be unique", 5, lane.toSet().size)
        }
    }
}
