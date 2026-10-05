package com.example

import com.example.game.ludo.engine.CaptureResolver
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.engine.MoveValidator
import com.example.game.ludo.model.BoardCoordinate
import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase25VerificationTest {

    // =========================================================================
    // SECTION 1: 52-CELL BOARD PATH VERIFICATION
    // =========================================================================

    @Test
    fun `verify 1 - common track contains exactly 52 unique cells`() {
        val track = LudoBoardCoordinates.commonTrack
        assertEquals(52, track.size)
        assertEquals(52, track.toSet().size)
    }

    @Test
    fun `verify 1b - all track cells are within 15x15 board bounds`() {
        LudoBoardCoordinates.commonTrack.forEachIndexed { index, coord ->
            assertTrue("Track index $index col ${coord.col} out of 0..14", coord.col in 0..14)
            assertTrue("Track index $index row ${coord.row} out of 0..14", coord.row in 0..14)
        }
    }

    @Test
    fun `verify 1c - adjacent track cells are connected with no invalid jumps`() {
        val track = LudoBoardCoordinates.commonTrack
        for (i in 0 until track.size) {
            val current = track[i]
            val next = track[(i + 1) % track.size]

            val dCol = abs(current.col - next.col)
            val dRow = abs(current.row - next.row)

            // Either orthogonal (dCol + dRow == 1) OR inner corner turn around 6x6 base (dCol == 1 && dRow == 1)
            val isOrthogonal = (dCol + dRow == 1)
            val isInnerCornerTurn = (dCol == 1 && dRow == 1)

            assertTrue(
                "Track jump between index $i $current and ${(i + 1) % track.size} $next is invalid (dCol=$dCol, dRow=$dRow)",
                isOrthogonal || isInnerCornerTurn
            )
        }
    }

    @Test
    fun `verify 1d - correct start index for every player`() {
        assertEquals(0, LudoBoardCoordinates.getStartTrackIndex(LudoColor.RED))
        assertEquals(13, LudoBoardCoordinates.getStartTrackIndex(LudoColor.GREEN))
        assertEquals(26, LudoBoardCoordinates.getStartTrackIndex(LudoColor.YELLOW))
        assertEquals(39, LudoBoardCoordinates.getStartTrackIndex(LudoColor.BLUE))

        // Check start cell coordinates
        assertEquals(BoardCoordinate(col = 1, row = 6), LudoBoardCoordinates.commonTrack[0])
        assertEquals(BoardCoordinate(col = 8, row = 1), LudoBoardCoordinates.commonTrack[13])
        assertEquals(BoardCoordinate(col = 13, row = 8), LudoBoardCoordinates.commonTrack[26])
        assertEquals(BoardCoordinate(col = 6, row = 13), LudoBoardCoordinates.commonTrack[39])
    }

    // =========================================================================
    // SECTION 2: STEP MATHEMATICS & BOUNDARIES
    // =========================================================================

    @Test
    fun `verify 2a - step 0 to 1 on dice 6 only`() {
        val player = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = (0..3).map { LudoToken(it, 1, TokenState.IN_BASE, 0) }
        )

        for (dice in 1..5) {
            val legal = MoveValidator.calculateLegalTokens(player, dice)
            assertTrue("Dice $dice must not allow base exit", legal.isEmpty())
        }

        val legalOn6 = MoveValidator.calculateLegalTokens(player, 6)
        assertEquals(setOf(0, 1, 2, 3), legalOn6)
    }

    @Test
    fun `verify 2b - step transitions 1 to 2, 50 to 51, 51 to 52, 56 to 57`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // 1 -> 2:
        // Put token 0 at step 1
        setPlayerToken(engine, 1, 0, TokenState.ON_BOARD, 1)
        assertTrue(engine.onDiceRolled(1))
        val res1 = engine.moveToken(0)
        assertNotNull(res1)
        assertEquals(1, res1!!.fromStep)
        assertEquals(2, res1.toStep)
        assertEquals(TokenState.ON_BOARD, engine.gameState.value.players[0].tokens[0].state)

        // 50 -> 51:
        setPlayerToken(engine, 1, 0, TokenState.ON_BOARD, 50)
        resetToDiceRoll(engine, 1)
        assertTrue(engine.onDiceRolled(1))
        val res50 = engine.moveToken(0)
        assertNotNull(res50)
        assertEquals(50, res50!!.fromStep)
        assertEquals(51, res50.toStep)
        assertEquals(TokenState.ON_BOARD, engine.gameState.value.players[0].tokens[0].state)

        // 51 -> 52 (enters home lane):
        resetToDiceRoll(engine, 1)
        assertTrue(engine.onDiceRolled(1))
        val res51 = engine.moveToken(0)
        assertNotNull(res51)
        assertEquals(51, res51!!.fromStep)
        assertEquals(52, res51.toStep)
        assertTrue(res51.enteredHome)
        assertEquals(TokenState.IN_HOME_LANE, engine.gameState.value.players[0].tokens[0].state)

        // 56 -> 57 (finishes):
        setPlayerToken(engine, 1, 0, TokenState.IN_HOME_LANE, 56)
        resetToDiceRoll(engine, 1)
        assertTrue(engine.onDiceRolled(1))
        val res56 = engine.moveToken(0)
        assertNotNull(res56)
        assertEquals(56, res56!!.fromStep)
        assertEquals(57, res56.toStep)
        assertTrue(res56.finishedToken)
        assertEquals(TokenState.FINISHED, engine.gameState.value.players[0].tokens[0].state)
    }

    @Test
    fun `verify 2c - step 57 finished token cannot move`() {
        val player = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = listOf(
                LudoToken(0, 1, TokenState.FINISHED, 57),
                LudoToken(1, 1, TokenState.IN_BASE, 0),
                LudoToken(2, 1, TokenState.IN_BASE, 0),
                LudoToken(3, 1, TokenState.IN_BASE, 0)
            )
        )

        for (dice in 1..6) {
            val legal = MoveValidator.calculateLegalTokens(player, dice)
            assertFalse("Finished token cannot move on dice $dice", legal.contains(0))
        }
    }

    @Test
    fun `verify 2d - overshoot beyond 57 is strictly illegal for all dice 1 to 6`() {
        for (step in 52..56) {
            for (dice in 1..6) {
                val player = LudoPlayer(
                    playerId = 1,
                    color = LudoColor.RED,
                    tokens = listOf(
                        LudoToken(0, 1, TokenState.IN_HOME_LANE, step),
                        LudoToken(1, 1, TokenState.IN_BASE, 0),
                        LudoToken(2, 1, TokenState.IN_BASE, 0),
                        LudoToken(3, 1, TokenState.IN_BASE, 0)
                    )
                )

                val legal = MoveValidator.calculateLegalTokens(player, dice)
                if (step + dice > 57) {
                    assertFalse("Step $step + dice $dice > 57 must be illegal", legal.contains(0))
                } else {
                    assertTrue("Step $step + dice $dice <= 57 must be legal", legal.contains(0))
                }
            }
        }
    }

    // =========================================================================
    // SECTION 3: PLAYER RELATIVE POSITION & WRAPPING
    // =========================================================================

    @Test
    fun `verify 3a - step 1 maps to exact starting coordinates for all 4 players`() {
        assertEquals(
            BoardCoordinate(1, 6),
            LudoBoardCoordinates.getCoordinateForToken(LudoColor.RED, 0, 1)
        )
        assertEquals(
            BoardCoordinate(8, 1),
            LudoBoardCoordinates.getCoordinateForToken(LudoColor.GREEN, 0, 1)
        )
        assertEquals(
            BoardCoordinate(13, 8),
            LudoBoardCoordinates.getCoordinateForToken(LudoColor.YELLOW, 0, 1)
        )
        assertEquals(
            BoardCoordinate(6, 13),
            LudoBoardCoordinates.getCoordinateForToken(LudoColor.BLUE, 0, 1)
        )
    }

    @Test
    fun `verify 3b - track wrapping works correctly for all 4 players`() {
        // Red start offset = 0. Does not wrap around 52 boundary during 1..51.
        assertEquals(0, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.RED, 1))
        assertEquals(50, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.RED, 51))

        // Green start offset = 13.
        // Step 1 -> index 13
        assertEquals(13, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.GREEN, 1))
        // Step 39 -> (13 + 39 - 1) % 52 = 51
        assertEquals(51, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.GREEN, 39))
        // Step 40 -> (13 + 40 - 1) % 52 = 0 (wrapped!)
        assertEquals(0, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.GREEN, 40))
        // Step 51 -> (13 + 51 - 1) % 52 = 11
        assertEquals(11, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.GREEN, 51))

        // Yellow start offset = 26.
        // Step 1 -> index 26
        assertEquals(26, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.YELLOW, 1))
        // Step 26 -> (26 + 26 - 1) % 52 = 51
        assertEquals(51, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.YELLOW, 26))
        // Step 27 -> (26 + 27 - 1) % 52 = 0 (wrapped!)
        assertEquals(0, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.YELLOW, 27))
        // Step 51 -> (26 + 51 - 1) % 52 = 24
        assertEquals(24, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.YELLOW, 51))

        // Blue start offset = 39.
        // Step 1 -> index 39
        assertEquals(39, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.BLUE, 1))
        // Step 13 -> (39 + 13 - 1) % 52 = 51
        assertEquals(51, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.BLUE, 13))
        // Step 14 -> (39 + 14 - 1) % 52 = 0 (wrapped!)
        assertEquals(0, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.BLUE, 14))
        // Step 51 -> (39 + 51 - 1) % 52 = 37
        assertEquals(37, LudoBoardCoordinates.getGlobalTrackIndex(LudoColor.BLUE, 51))
    }

    // =========================================================================
    // SECTION 4: HOME-LANE TRANSITION & ISOLATION
    // =========================================================================

    @Test
    fun `verify 4a - final track step orthogonally enters that player's private home lane`() {
        val colors = listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)

        colors.forEach { color ->
            val finalTrackCoord = LudoBoardCoordinates.getCoordinateForToken(color, 0, 51)
            val firstHomeCoord = LudoBoardCoordinates.getCoordinateForToken(color, 0, 52)

            val dCol = abs(finalTrackCoord.col - firstHomeCoord.col)
            val dRow = abs(finalTrackCoord.row - firstHomeCoord.row)

            assertEquals("Transition 51 -> 52 for $color must be strictly orthogonal (distance 1)", 1, dCol + dRow)
        }
    }

    @Test
    fun `verify 4b - each player's home lane contains exactly 5 logical cells`() {
        LudoColor.values().forEach { color ->
            val lane = LudoBoardCoordinates.homeLanes[color]
            assertNotNull(lane)
            assertEquals(5, lane!!.size)
            assertEquals(5, lane.toSet().size)
        }
    }

    @Test
    fun `verify 4c - home lanes are mutually disjoint`() {
        val allHomeCells = mutableSetOf<BoardCoordinate>()
        var count = 0
        LudoColor.values().forEach { color ->
            val lane = LudoBoardCoordinates.homeLanes[color]!!
            lane.forEach { coord ->
                assertFalse("Home cell $coord must not belong to another player", allHomeCells.contains(coord))
                allHomeCells.add(coord)
                count++
            }
        }
        assertEquals(20, count)
        assertEquals(20, allHomeCells.size)
    }

    @Test
    fun `verify 4d - step 57 finishes in center for all players`() {
        LudoColor.values().forEach { color ->
            val finishCoord = LudoBoardCoordinates.getCoordinateForToken(color, 0, 57)
            assertTrue("Finish col must be in 6..8", finishCoord.col in 6..8)
            assertTrue("Finish row must be in 6..8", finishCoord.row in 6..8)
        }
    }

    // =========================================================================
    // SECTION 5: SAFE CELLS
    // =========================================================================

    @Test
    fun `verify 5a - safe cell index set and coordinates`() {
        assertEquals(8, LudoBoardCoordinates.safeTrackIndices.size)
        val expected = setOf(0, 8, 13, 21, 26, 34, 39, 47)
        assertEquals(expected, LudoBoardCoordinates.safeTrackIndices)

        expected.forEach { idx ->
            assertTrue(LudoBoardCoordinates.isTrackIndexSafe(idx))
        }
    }

    @Test
    fun `verify 5b - non-safe track cells are not safe`() {
        val nonSafe = (0..51).toSet() - LudoBoardCoordinates.safeTrackIndices
        assertEquals(44, nonSafe.size)
        nonSafe.forEach { idx ->
            assertFalse("Track index $idx must NOT be safe", LudoBoardCoordinates.isTrackIndexSafe(idx))
        }
    }

    // =========================================================================
    // SECTION 6: CAPTURE LOGIC
    // =========================================================================

    @Test
    fun `verify 6a - same player tokens on same non-safe cell do not capture each other`() {
        val player1 = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = listOf(
                LudoToken(0, 1, TokenState.ON_BOARD, 5),
                LudoToken(1, 1, TokenState.ON_BOARD, 5),
                LudoToken(2, 1, TokenState.IN_BASE, 0),
                LudoToken(3, 1, TokenState.IN_BASE, 0)
            )
        )

        val captures = CaptureResolver.resolveCaptures(
            movingPlayerId = 1,
            movingPlayerColor = LudoColor.RED,
            targetStep = 5,
            allPlayers = listOf(player1)
        )

        assertTrue("Player cannot capture their own token", captures.isEmpty())
    }

    @Test
    fun `verify 6b - opponent on non-safe position is captured and returned to base`() {
        // Red start = 0. Step 5 = track index 4 (non-safe)
        // Yellow start = 26. Step 31 = (26 + 31 - 1) % 52 = 4 (same cell)
        val player1 = LudoPlayer(
            1, LudoColor.RED, tokens = listOf(
                LudoToken(0, 1, TokenState.ON_BOARD, 1),
                LudoToken(1, 1, TokenState.IN_BASE, 0),
                LudoToken(2, 1, TokenState.IN_BASE, 0),
                LudoToken(3, 1, TokenState.IN_BASE, 0)
            )
        )
        val player2 = LudoPlayer(
            2, LudoColor.YELLOW, tokens = listOf(
                LudoToken(0, 2, TokenState.ON_BOARD, 31),
                LudoToken(1, 2, TokenState.IN_BASE, 0),
                LudoToken(2, 2, TokenState.IN_BASE, 0),
                LudoToken(3, 2, TokenState.IN_BASE, 0)
            )
        )

        // Player 1 moves to step 5
        val captures = CaptureResolver.resolveCaptures(
            movingPlayerId = 1,
            movingPlayerColor = LudoColor.RED,
            targetStep = 5,
            allPlayers = listOf(player1, player2)
        )

        assertEquals(1, captures.size)
        assertEquals(2, captures[0].playerId)
        assertEquals(0, captures[0].tokenId)
    }

    @Test
    fun `verify 6c - opponent on safe position is NOT captured`() {
        // Safe cell index 8 (Star): Red step 9 -> (0 + 9 - 1) % 52 = 8
        // Green start = 13. Green step 48 -> (13 + 48 - 1) % 52 = 8
        val player1 = LudoPlayer(
            1, LudoColor.RED, tokens = listOf(
                LudoToken(0, 1, TokenState.ON_BOARD, 9),
                LudoToken(1, 1, TokenState.IN_BASE, 0),
                LudoToken(2, 1, TokenState.IN_BASE, 0),
                LudoToken(3, 1, TokenState.IN_BASE, 0)
            )
        )
        val player2 = LudoPlayer(
            2, LudoColor.GREEN, tokens = listOf(
                LudoToken(0, 2, TokenState.ON_BOARD, 48),
                LudoToken(1, 2, TokenState.IN_BASE, 0),
                LudoToken(2, 2, TokenState.IN_BASE, 0),
                LudoToken(3, 2, TokenState.IN_BASE, 0)
            )
        )

        val captures = CaptureResolver.resolveCaptures(
            movingPlayerId = 2,
            movingPlayerColor = LudoColor.GREEN,
            targetStep = 48,
            allPlayers = listOf(player1, player2)
        )

        assertTrue("Safe cell must never capture", captures.isEmpty())
    }

    @Test
    fun `verify 6d - home lane tokens are immune from captures`() {
        val player1 = LudoPlayer(
            1, LudoColor.RED, tokens = listOf(
                LudoToken(0, 1, TokenState.IN_HOME_LANE, 53),
                LudoToken(1, 1, TokenState.IN_BASE, 0),
                LudoToken(2, 1, TokenState.IN_BASE, 0),
                LudoToken(3, 1, TokenState.IN_BASE, 0)
            )
        )
        val player2 = LudoPlayer(
            2, LudoColor.YELLOW, tokens = listOf(
                LudoToken(0, 2, TokenState.ON_BOARD, 10),
                LudoToken(1, 2, TokenState.IN_BASE, 0),
                LudoToken(2, 2, TokenState.IN_BASE, 0),
                LudoToken(3, 2, TokenState.IN_BASE, 0)
            )
        )

        val captures = CaptureResolver.resolveCaptures(
            movingPlayerId = 2,
            movingPlayerColor = LudoColor.YELLOW,
            targetStep = 53,
            allPlayers = listOf(player1, player2)
        )

        assertTrue("Home lane cannot be captured", captures.isEmpty())
    }

    // =========================================================================
    // SECTION 7: TURN ENGINE AUTHORITY
    // =========================================================================

    @Test
    fun `verify 7a - only currentPlayer can roll`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Turn is P1
        assertEquals(1, engine.gameState.value.currentPlayerId)
        assertTrue(engine.onDiceRolled(4))

        // P1 rolled 4 (no legal moves since all in base). Turn advanced to P2
        assertEquals(2, engine.gameState.value.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)
    }

    @Test
    fun `verify 7b - cannot roll twice before resolving current dice`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        assertTrue(engine.onDiceRolled(6))
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION, engine.gameState.value.turnPhase)

        // Attempting to roll again while WAITING_FOR_TOKEN_SELECTION must return false
        assertFalse("Cannot roll dice twice before moving a token", engine.onDiceRolled(5))
    }

    @Test
    fun `verify 7c - cannot move a token before rolling dice`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)
        assertNull("Cannot move token while waiting for dice roll", engine.moveToken(0))
    }

    @Test
    fun `verify 7d - cannot move an illegal token`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Release token 0 to step 1
        engine.onDiceRolled(6)
        engine.moveToken(0)

        // Next roll is 3 (extra turn). Token 0 is at step 1. Tokens 1, 2, 3 in base.
        engine.onDiceRolled(3)
        assertEquals(setOf(0), engine.gameState.value.legalTokenIds)

        // Attempting to move token 1 (in base) on roll 3 must be rejected
        assertNull("Cannot move token 1 on roll 3", engine.moveToken(1))
    }

    // =========================================================================
    // SECTION 8: SIX RULE
    // =========================================================================

    @Test
    fun `verify 8a - 6 then 4 resets consecutive six count correctly`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // 1st roll: 6
        engine.onDiceRolled(6)
        engine.moveToken(0)
        assertEquals(1, engine.gameState.value.consecutiveSixCount)
        assertEquals(1, engine.gameState.value.currentPlayerId)

        // 2nd roll: 4
        engine.onDiceRolled(4)
        engine.moveToken(0)
        assertEquals(0, engine.gameState.value.consecutiveSixCount)
        assertEquals(2, engine.gameState.value.currentPlayerId)
    }

    @Test
    fun `verify 8b - 6 and capture does not grant duplicate extra turns`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Setup: P1 token 0 at step 1. P2 token 0 at track index 6 (P1 step 7)
        // P1 rolls 6: 1 + 6 = 7 (lands on P2 token!)
        setPlayerToken(engine, 1, 0, TokenState.ON_BOARD, 1)
        // For P2 (Yellow start = 26): step 33 -> (26 + 33 - 1) % 52 = 6
        setPlayerToken(engine, 2, 0, TokenState.ON_BOARD, 33)

        assertTrue(engine.onDiceRolled(6))
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)

        // Both rolled 6 AND captured
        assertEquals(6, moveRes!!.diceValue)
        assertEquals(1, moveRes.capturedTokens.size)
        assertTrue(moveRes.extraTurnGranted)

        // Exact ONE next turn decision: P1 remains active, waiting for dice
        assertEquals(1, engine.gameState.value.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)
    }

    // =========================================================================
    // SECTION 9: EXTRA-TURN PRECEDENCE
    // =========================================================================

    @Test
    fun `verify 9 - extra-turn precedence is deterministic (CAPTURED over FINISHED over SIX)`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Test capture reason priority
        setPlayerToken(engine, 1, 0, TokenState.ON_BOARD, 1)
        setPlayerToken(engine, 2, 0, TokenState.ON_BOARD, 33)
        engine.onDiceRolled(6)
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        assertTrue(moveRes!!.extraTurnGranted)
        assertEquals(ExtraTurnReason.CAPTURED_OPPONENT, moveRes.extraTurnReason)
    }

    // =========================================================================
    // SECTION 10: WINNING
    // =========================================================================

    @Test
    fun `verify 10 - winning condition requires all 4 tokens finished`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // 3 finished tokens -> NOT won
        setPlayerToken(engine, 1, 0, TokenState.FINISHED, 57)
        setPlayerToken(engine, 1, 1, TokenState.FINISHED, 57)
        setPlayerToken(engine, 1, 2, TokenState.FINISHED, 57)
        setPlayerToken(engine, 1, 3, TokenState.IN_HOME_LANE, 56)

        assertFalse(engine.gameState.value.players[0].hasWon)

        // Roll 1 to finish 4th token
        assertTrue(engine.onDiceRolled(1))
        val res = engine.moveToken(3)
        assertNotNull(res)
        assertTrue(res!!.finishedToken)
        assertTrue(res.isGameOver)

        val p1 = engine.gameState.value.players[0]
        assertTrue(p1.hasWon)
        assertEquals(1, p1.finishRank)
        assertEquals(2, engine.gameState.value.players[1].finishRank)
    }

    // =========================================================================
    // SECTION 11: 2, 3, 4 PLAYER SETUP
    // =========================================================================

    @Test
    fun `verify 11 - 2, 3, 4 player setups adhere to canonical specifications`() {
        val e2 = LudoGameEngine()
        assertTrue(e2.initGame(2))
        assertEquals(listOf(LudoColor.RED, LudoColor.YELLOW), e2.gameState.value.players.map { it.color })

        val e3 = LudoGameEngine()
        assertTrue(e3.initGame(3))
        assertEquals(listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW), e3.gameState.value.players.map { it.color })

        val e4 = LudoGameEngine()
        assertTrue(e4.initGame(4))
        assertEquals(listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE), e4.gameState.value.players.map { it.color })

        // Check token integrity
        listOf(e2, e3, e4).forEach { e ->
            e.gameState.value.players.forEach { p ->
                assertEquals(4, p.tokens.size)
                assertEquals(setOf(0, 1, 2, 3), p.tokens.map { it.tokenId }.toSet())
            }
        }
    }

    // =========================================================================
    // SECTION 12: IMMUTABILITY & STATE SAFETY
    // =========================================================================

    @Test
    fun `verify 12 - game state updates produce new immutable copies without mutating old states`() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        val stateBefore = engine.gameState.value
        engine.onDiceRolled(6)
        val stateAfterRoll = engine.gameState.value

        assertNotEquals(stateBefore, stateAfterRoll)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, stateBefore.turnPhase)
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION, stateAfterRoll.turnPhase)
    }

    // =========================================================================
    // Helper utilities for precise state-testing
    // =========================================================================

    private fun setPlayerToken(engine: LudoGameEngine, playerId: Int, tokenId: Int, state: TokenState, step: Int) {
        val field = engine.javaClass.getDeclaredField("_gameState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<LudoGameState>
        val current = stateFlow.value

        val updatedPlayers = current.players.map { p ->
            if (p.playerId == playerId) {
                p.copy(tokens = p.tokens.map { t ->
                    if (t.tokenId == tokenId) t.copy(state = state, stepCount = step) else t
                })
            } else p
        }
        stateFlow.value = current.copy(players = updatedPlayers)
    }

    private fun resetToDiceRoll(engine: LudoGameEngine, activePlayerId: Int) {
        val field = engine.javaClass.getDeclaredField("_gameState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<LudoGameState>
        val current = stateFlow.value
        stateFlow.value = current.copy(
            currentPlayerId = activePlayerId,
            diceValue = null,
            turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL,
            legalTokenIds = emptySet()
        )
    }
}
