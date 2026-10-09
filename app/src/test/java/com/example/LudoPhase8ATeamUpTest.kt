package com.example

import com.example.game.ludo.engine.CaptureResolver
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.engine.MoveValidator
import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoGameStateSerializer
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
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
 * Phase 8A Team-Up Core Engine Unit Tests (including Partner Assistance).
 *
 * Covers:
 * 1. Team-Up game initialization (4 players only, explicit team assignment)
 * 2. Rejection of invalid player counts for Team-Up mode
 * 3. Clockwise alternating turn order (P1 -> P2 -> P3 -> P4 -> P1)
 * 4. Friendly fire prevention (teammates share cells without capturing)
 * 5. Hostile capture between opposing teams
 * 6. Partner Assistance: Finished player remains active and gets future turns
 * 7. Partner Assistance: P1 moves P3 token while P1 remains currentPlayerId
 * 8. Partner Assistance: Hostile capture by assisted partner token awards extra turn to active player
 * 9. Partner Assistance: Finishing partner token counts towards teammate and awards extra turn to active player
 * 10. Partner Assistance: Third-six rule is preserved for the active player
 * 11. Individual mode backward compatibility (finished players skipped in Individual mode)
 * 12. Team victory when entire team has all 8 tokens finished
 * 13. Serialization and persistence validation of Team-Up state
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase8ATeamUpTest {

    private lateinit var engine: LudoGameEngine

    @Before
    fun setUp() {
        engine = LudoGameEngine()
    }

    // =========================================================================
    // 1. Initialization & Validation Tests
    // =========================================================================

    @Test
    fun testTeamUpInitialization_FourPlayers_AssignsTeamsExplicitly() {
        val success = engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP)
        assertTrue(success)

        val state = engine.gameState.value
        assertEquals(LudoGameMode.TEAM_UP, state.gameMode)
        assertEquals(4, state.playerCount)
        assertEquals(4, state.players.size)
        assertNull(state.winningTeamId)

        val p1 = state.players.find { it.playerId == 1 }!!
        val p2 = state.players.find { it.playerId == 2 }!!
        val p3 = state.players.find { it.playerId == 3 }!!
        val p4 = state.players.find { it.playerId == 4 }!!

        assertEquals(LudoTeamId.TEAM_1, p1.teamId)
        assertEquals(LudoTeamId.TEAM_2, p2.teamId)
        assertEquals(LudoTeamId.TEAM_1, p3.teamId)
        assertEquals(LudoTeamId.TEAM_2, p4.teamId)

        assertEquals(LudoColor.RED, p1.color)
        assertEquals(LudoColor.GREEN, p2.color)
        assertEquals(LudoColor.YELLOW, p3.color)
        assertEquals(LudoColor.BLUE, p4.color)
    }

    @Test
    fun testTeamUpInitialization_InvalidPlayerCount_RejectedSafely() {
        assertFalse(engine.initGame(playerCount = 2, gameMode = LudoGameMode.TEAM_UP))
        assertFalse(engine.initGame(playerCount = 3, gameMode = LudoGameMode.TEAM_UP))
        assertFalse(engine.initGame(playerCount = 5, gameMode = LudoGameMode.TEAM_UP))
        assertFalse(engine.gameState.value.isGameStarted)
    }

    @Test
    fun testIndividualMode_DefaultInitialization_NoTeamAssigned() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val state = engine.gameState.value
        assertEquals(LudoGameMode.INDIVIDUAL, state.gameMode)
        assertNull(state.winningTeamId)
        state.players.forEach {
            assertNull(it.teamId)
        }
    }

    // =========================================================================
    // 2. Friendly Fire & Hostile Capture Tests
    // =========================================================================

    @Test
    fun testFriendlyFirePrevention_TeammatesDoNotCaptureEachOther() {
        val p1 = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 13)
            ) + (1..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.IN_BASE, stepCount = 0) },
            teamId = LudoTeamId.TEAM_1
        )
        val p3 = LudoPlayer(
            playerId = 3,
            color = LudoColor.YELLOW,
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 3, state = TokenState.ON_BOARD, stepCount = 39)
            ) + (1..3).map { LudoToken(tokenId = it, playerId = 3, state = TokenState.IN_BASE, stepCount = 0) },
            teamId = LudoTeamId.TEAM_1
        )

        // In TEAM_UP mode, p1 moving to step 13 (where p3 token is) must NOT capture p3
        val capturesTeamUp = CaptureResolver.resolveCaptures(
            movingPlayerId = 1,
            movingPlayerColor = LudoColor.RED,
            targetStep = 13,
            allPlayers = listOf(p1, p3),
            gameMode = LudoGameMode.TEAM_UP
        )
        assertTrue("Teammates must not capture each other in TEAM_UP mode", capturesTeamUp.isEmpty())

        // In INDIVIDUAL mode, p1 moving to step 13 DOES capture p3
        val p1Ind = p1.copy(teamId = null)
        val p3Ind = p3.copy(teamId = null)
        val capturesIndividual = CaptureResolver.resolveCaptures(
            movingPlayerId = 1,
            movingPlayerColor = LudoColor.RED,
            targetStep = 13,
            allPlayers = listOf(p1Ind, p3Ind),
            gameMode = LudoGameMode.INDIVIDUAL
        )
        assertEquals(1, capturesIndividual.size)
        assertEquals(3, capturesIndividual[0].playerId)
    }

    @Test
    fun testHostileCapture_OpponentIsCapturedAndGrantsExtraTurn() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))

        // P1 (Red, Team 1) at step 7 -> rolling 3 moves to step 10 (global track 9, not safe).
        // P2 (Green, Team 2) at step 49 (global track 9).
        val state = engine.gameState.value
        val customPlayers = state.players.map { player ->
            when (player.playerId) {
                1 -> player.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 1, state = TokenState.ON_BOARD, stepCount = 7)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.IN_BASE, stepCount = 0) }
                )
                2 -> player.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 2, state = TokenState.ON_BOARD, stepCount = 49)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 2, state = TokenState.IN_BASE, stepCount = 0) }
                )
                else -> player
            }
        }

        engine.restoreState(
            state.copy(
                players = customPlayers,
                currentPlayerId = 1,
                turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL
            )
        )

        assertTrue(engine.onDiceRolled(3))
        val moveResult = engine.moveToken(0)
        assertNotNull(moveResult)
        assertEquals(1, moveResult!!.capturedTokens.size)
        assertEquals(2, moveResult.capturedTokens[0].playerId)
        assertTrue(moveResult.extraTurnGranted)
        assertEquals(ExtraTurnReason.CAPTURED_OPPONENT, moveResult.extraTurnReason)
        assertEquals(1, moveResult.nextPlayerId)

        // P2's token is returned to base
        val updatedP2 = engine.gameState.value.players.find { it.playerId == 2 }!!
        assertEquals(TokenState.IN_BASE, updatedP2.tokens[0].state)
        assertEquals(0, updatedP2.tokens[0].stepCount)
    }

    // =========================================================================
    // 3. Turn Progression & Clockwise Alternation
    // =========================================================================

    @Test
    fun testTurnOrder_ClockwiseAlternation() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))

        val playersWithTokens = engine.gameState.value.players.map { p ->
            p.copy(
                tokens = listOf(
                    LudoToken(tokenId = 0, playerId = p.playerId, state = TokenState.ON_BOARD, stepCount = 5)
                ) + (1..3).map { LudoToken(tokenId = it, playerId = p.playerId, state = TokenState.IN_BASE, stepCount = 0) }
            )
        }
        engine.restoreState(engine.gameState.value.copy(players = playersWithTokens, currentPlayerId = 1))

        // P1 moves (rolls 1 -> no extra turn) -> next is P2
        engine.onDiceRolled(1)
        engine.moveToken(0)
        assertEquals(2, engine.gameState.value.currentPlayerId)

        // P2 moves (rolls 1) -> next is P3
        engine.onDiceRolled(1)
        engine.moveToken(0)
        assertEquals(3, engine.gameState.value.currentPlayerId)

        // P3 moves (rolls 1) -> next is P4
        engine.onDiceRolled(1)
        engine.moveToken(0)
        assertEquals(4, engine.gameState.value.currentPlayerId)

        // P4 moves (rolls 1) -> next is P1
        engine.onDiceRolled(1)
        engine.moveToken(0)
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // 4. Partner Assistance Tests (Phase 8A Correction)
    // =========================================================================

    @Test
    fun testPartnerAssistance_FinishedPlayerReceivesTurnAndMovesTeammateToken() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))

        // Player 1 (Team 1) has finished ALL 4 of their own tokens (isFinished = true)
        // Player 3 (Teammate on Team 1) has 1 token ON_BOARD at step 10, others in base
        val players = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(
                    isFinished = true,
                    finishRank = 1,
                    tokens = (0..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57) }
                )
                3 -> p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 3, state = TokenState.ON_BOARD, stepCount = 10)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 3, state = TokenState.IN_BASE, stepCount = 0) }
                )
                else -> p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = p.playerId, state = TokenState.ON_BOARD, stepCount = 5)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = p.playerId, state = TokenState.IN_BASE, stepCount = 0) }
                )
            }
        }

        // Set current player to P4 so when P4 completes their move, turn passes to P1
        engine.restoreState(engine.gameState.value.copy(players = players, currentPlayerId = 4))

        // P4 rolls 1 and moves -> Next turn MUST be P1 (Partner Assistance, NOT skipped!)
        assertTrue(engine.onDiceRolled(1))
        val p4Move = engine.moveToken(0)
        assertNotNull(p4Move)
        assertEquals(1, p4Move!!.nextPlayerId)

        // Assert P1 is the active player despite having isFinished = true
        val stateP1Turn = engine.gameState.value
        assertEquals(1, stateP1Turn.currentPlayerId)
        assertTrue(stateP1Turn.activePlayer!!.isFinished)

        // P1 rolls 4
        assertTrue(engine.onDiceRolled(4))
        val stateAfterRoll = engine.gameState.value
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION, stateAfterRoll.turnPhase)
        // Legal token must be P3's token 0!
        assertEquals(setOf(0), stateAfterRoll.legalTokenIds)

        // P1 executes moveToken(0) -> moves P3's token from 10 to 14
        val p1AssistMove = engine.moveToken(0)
        assertNotNull(p1AssistMove)
        assertEquals(1, p1AssistMove!!.playerId) // Active player remains P1!
        assertEquals(0, p1AssistMove.tokenId)
        assertEquals(10, p1AssistMove.fromStep)
        assertEquals(14, p1AssistMove.toStep)
        assertEquals(2, p1AssistMove.nextPlayerId) // Turn passes to P2

        // Verify P3's token state is updated on the board
        val updatedP3 = engine.gameState.value.players.find { it.playerId == 3 }!!
        assertEquals(14, updatedP3.tokens[0].stepCount)
        assertEquals(TokenState.ON_BOARD, updatedP3.tokens[0].state)
    }

    @Test
    fun testPartnerAssistance_HostileCaptureGrantsExtraTurnToActivePlayer() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))

        // P1 is finished.
        // P3 (Yellow, Team 1) has token at step 35 (global track 8: Yellow start is 26, (26+35-1)%52 = 60%52 = 8).
        // Wait, track 8 is a star square (safe)!
        // Let's use global track 9 (not safe).
        // Yellow step for track 9: (9 - 26 + 52) % 52 + 1 = 35 + 1 = 36.
        // P2 (Green, Team 2) has token at track 9 (Green step 49).
        // P3 token at step 33 -> rolls 3 -> reaches step 36 (track 9).
        val players = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(
                    isFinished = true,
                    tokens = (0..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57) }
                )
                2 -> p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 2, state = TokenState.ON_BOARD, stepCount = 49)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 2, state = TokenState.IN_BASE, stepCount = 0) }
                )
                3 -> p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 3, state = TokenState.ON_BOARD, stepCount = 33)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 3, state = TokenState.IN_BASE, stepCount = 0) }
                )
                else -> p
            }
        }

        engine.restoreState(engine.gameState.value.copy(players = players, currentPlayerId = 1))

        // P1 rolls 3 and moves P3's token 0 onto enemy P2
        assertTrue(engine.onDiceRolled(3))
        val moveResult = engine.moveToken(0)
        assertNotNull(moveResult)
        assertEquals(1, moveResult!!.playerId) // P1 is turn owner
        assertEquals(1, moveResult.capturedTokens.size)
        assertEquals(2, moveResult.capturedTokens[0].playerId)
        assertTrue(moveResult.extraTurnGranted)
        assertEquals(ExtraTurnReason.CAPTURED_OPPONENT, moveResult.extraTurnReason)
        assertEquals(1, moveResult.nextPlayerId) // Extra turn goes to P1!
        assertEquals(1, engine.gameState.value.currentPlayerId)
    }

    @Test
    fun testPartnerAssistance_FinishingPartnerTokenCountsTowardsTeammateAndAwardsExtraTurn() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))

        // P1 is finished (all 4 tokens finished).
        // P3 has token 0 at step 56 (home lane), tokens 1..3 in base.
        val players = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(
                    isFinished = true,
                    tokens = (0..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57) }
                )
                3 -> p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 3, state = TokenState.IN_HOME_LANE, stepCount = 56)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 3, state = TokenState.IN_BASE, stepCount = 0) }
                )
                else -> p
            }
        }

        engine.restoreState(engine.gameState.value.copy(players = players, currentPlayerId = 1))

        // P1 rolls 1 -> moves P3's token 0 to 57 (finish)
        assertTrue(engine.onDiceRolled(1))
        val moveResult = engine.moveToken(0)
        assertNotNull(moveResult)
        assertEquals(1, moveResult!!.playerId)
        assertTrue(moveResult.finishedToken)
        assertTrue(moveResult.extraTurnGranted) // Finished token grants extra turn to P1
        assertEquals(ExtraTurnReason.FINISHED_TOKEN, moveResult.extraTurnReason)
        assertEquals(1, moveResult.nextPlayerId)
        assertFalse(moveResult.isGameOver) // Team has 5 finished tokens, match continues

        // Verify P3 token 0 is FINISHED
        val updatedP3 = engine.gameState.value.players.find { it.playerId == 3 }!!
        assertEquals(TokenState.FINISHED, updatedP3.tokens[0].state)
        assertEquals(1, updatedP3.finishedTokenCount)
    }

    @Test
    fun testPartnerAssistance_ThirdSixRulePreservedForActivePlayer() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))

        // P1 is finished. P3 has token 0 ON_BOARD.
        val players = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(
                    isFinished = true,
                    tokens = (0..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57) }
                )
                3 -> p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 3, state = TokenState.ON_BOARD, stepCount = 10)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 3, state = TokenState.IN_BASE, stepCount = 0) }
                )
                else -> p
            }
        }

        // Set P1 with consecutiveSixCount = 2
        engine.restoreState(
            engine.gameState.value.copy(
                players = players,
                currentPlayerId = 1,
                consecutiveSixCount = 2
            )
        )

        // 3rd consecutive 6 roll -> triggers third-six rule fallback (e.g. 4)
        assertTrue(engine.onDiceRolled(6, thirdRollFallback = 4))
        assertEquals(4, engine.gameState.value.diceValue)
        assertEquals(1, engine.gameState.value.currentPlayerId) // Same player P1!
        assertEquals(0, engine.gameState.value.consecutiveSixCount) // Reset to 0
    }

    @Test
    fun testIndividualMode_FinishedPlayerIsSkippedPermanently() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))

        // In Individual mode, P1 has won / finished
        val players = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(
                    isFinished = true,
                    finishRank = 1,
                    tokens = (0..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57) }
                )
            } else {
                p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = p.playerId, state = TokenState.ON_BOARD, stepCount = 5)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = p.playerId, state = TokenState.IN_BASE, stepCount = 0) }
                )
            }
        }

        engine.restoreState(engine.gameState.value.copy(players = players, currentPlayerId = 4))

        // P4 rolls and moves -> in INDIVIDUAL mode, P1 MUST be skipped and turn passes to P2
        assertTrue(engine.onDiceRolled(1))
        val move = engine.moveToken(0)
        assertNotNull(move)
        assertEquals(2, move!!.nextPlayerId)
        assertEquals(2, engine.gameState.value.currentPlayerId)
    }

    // =========================================================================
    // 5. Team Victory Determination
    // =========================================================================

    @Test
    fun testTeamVictory_RequiresBothTeammatesToFinish() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))

        // P1 has 3 tokens finished, token 0 at step 56.
        // P3 has all 4 tokens finished.
        // Team 1 total finished is 7 tokens.
        val players = engine.gameState.value.players.map { p ->
            when (p.playerId) {
                1 -> p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_HOME_LANE, stepCount = 56)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57) }
                )
                3 -> p.copy(
                    isFinished = true,
                    tokens = (0..3).map { LudoToken(tokenId = it, playerId = 3, state = TokenState.FINISHED, stepCount = 57) }
                )
                else -> p
            }
        }
        engine.restoreState(
            engine.gameState.value.copy(
                players = players,
                currentPlayerId = 1,
                turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL
            )
        )

        assertFalse(engine.gameState.value.isGameOver)
        assertNull(engine.gameState.value.winningTeamId)

        // P1 rolls 1 -> token 0 reaches 57 (finished)
        assertTrue(engine.onDiceRolled(1))
        val moveResult = engine.moveToken(0)
        assertNotNull(moveResult)
        assertTrue(moveResult!!.finishedToken)
        assertTrue(moveResult.isGameOver)

        val finalState = engine.gameState.value
        assertTrue(finalState.isGameOver)
        assertEquals(TurnPhase.GAME_OVER, finalState.turnPhase)
        assertEquals(LudoTeamId.TEAM_1, finalState.winningTeamId)
        assertTrue(finalState.isTeamFinished(LudoTeamId.TEAM_1))
        assertEquals(8, finalState.getTeamFinishedTokenCount(LudoTeamId.TEAM_1))
        assertFalse(finalState.isTeamFinished(LudoTeamId.TEAM_2))
    }

    @Test
    fun testOneTeammateFinished_DoesNotEndGamePrematurely() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))

        // P1 finishes 4th token, but P3 has 0 finished tokens
        val players = engine.gameState.value.players.map { p ->
            if (p.playerId == 1) {
                p.copy(
                    tokens = listOf(
                        LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_HOME_LANE, stepCount = 56)
                    ) + (1..3).map { LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57) }
                )
            } else {
                p
            }
        }
        engine.restoreState(engine.gameState.value.copy(players = players, currentPlayerId = 1))

        assertTrue(engine.onDiceRolled(1))
        val moveResult = engine.moveToken(0)
        assertNotNull(moveResult)
        assertTrue(moveResult!!.finishedToken)
        assertFalse(moveResult.isGameOver)

        val state = engine.gameState.value
        assertFalse(state.isGameOver)
        assertNull(state.winningTeamId)
        // P1 is finished, but Team 1 is not finished
        assertTrue(state.players.find { it.playerId == 1 }!!.isFinished)
        assertFalse(state.isTeamFinished(LudoTeamId.TEAM_1))
        assertEquals(4, state.getTeamFinishedTokenCount(LudoTeamId.TEAM_1))
    }

    // =========================================================================
    // 6. Persistence & Serialization Tests
    // =========================================================================

    @Test
    fun testSerialization_PreservesGameModeAndTeamId() {
        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 4,
            currentPlayerId = 2,
            gameMode = LudoGameMode.TEAM_UP,
            winningTeamId = LudoTeamId.TEAM_2,
            players = listOf(
                LudoPlayer(playerId = 1, color = LudoColor.RED, teamId = LudoTeamId.TEAM_1),
                LudoPlayer(playerId = 2, color = LudoColor.GREEN, teamId = LudoTeamId.TEAM_2),
                LudoPlayer(playerId = 3, color = LudoColor.YELLOW, teamId = LudoTeamId.TEAM_1),
                LudoPlayer(playerId = 4, color = LudoColor.BLUE, teamId = LudoTeamId.TEAM_2)
            )
        )

        val json = LudoGameStateSerializer.stateToJson(state)
        assertEquals("TEAM_UP", json.getString("gameMode"))
        assertEquals("TEAM_2", json.getString("winningTeamId"))

        val deserialized = LudoGameStateSerializer.stateFromJson(json)
        assertEquals(LudoGameMode.TEAM_UP, deserialized.gameMode)
        assertEquals(LudoTeamId.TEAM_2, deserialized.winningTeamId)
        assertEquals(LudoTeamId.TEAM_1, deserialized.players[0].teamId)
        assertEquals(LudoTeamId.TEAM_2, deserialized.players[1].teamId)
        assertEquals(LudoTeamId.TEAM_1, deserialized.players[2].teamId)
        assertEquals(LudoTeamId.TEAM_2, deserialized.players[3].teamId)
    }
}
