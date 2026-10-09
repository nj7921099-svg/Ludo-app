package com.example

import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoGameStateSerializer
import com.example.game.ludo.model.LudoMoveResult
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import com.example.game.ludo.model.toJsonObject
import com.example.game.ludo.model.toJsonString
import com.example.network.model.ConfigMsg
import com.example.network.model.GetLudoStateMsg
import com.example.network.model.LudoStateSyncMsg
import com.example.network.model.NetworkMessage
import com.example.network.model.StateSyncMsg
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
class LudoPhase7StateSyncTest {

    // =========================================================================
    // 1. LudoToken serialization & deserialization tests
    // =========================================================================

    @Test
    fun `test token serialization roundtrip across all lifecycle states`() {
        val tokens = listOf(
            LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 1),
            LudoToken(tokenId = 2, playerId = 2, state = TokenState.ON_BOARD, stepCount = 51),
            LudoToken(tokenId = 3, playerId = 3, state = TokenState.IN_HOME_LANE, stepCount = 54),
            LudoToken(tokenId = 0, playerId = 4, state = TokenState.FINISHED, stepCount = 57)
        )

        tokens.forEach { original ->
            val json = LudoGameStateSerializer.tokenToJson(original)
            val parsed = LudoGameStateSerializer.tokenFromJson(json)
            assertEquals("TokenId must match", original.tokenId, parsed.tokenId)
            assertEquals("PlayerId must match", original.playerId, parsed.playerId)
            assertEquals("State must match", original.state, parsed.state)
            assertEquals("StepCount must match", original.stepCount, parsed.stepCount)
        }
    }

    // =========================================================================
    // 2. LudoPlayer serialization & deserialization tests
    // =========================================================================

    @Test
    fun `test player serialization roundtrip with tokens and finish rank`() {
        val player = LudoPlayer(
            playerId = 2,
            color = LudoColor.GREEN,
            name = "Green (P2)",
            tokens = listOf(
                LudoToken(0, 2, TokenState.FINISHED, 57),
                LudoToken(1, 2, TokenState.FINISHED, 57),
                LudoToken(2, 2, TokenState.FINISHED, 57),
                LudoToken(3, 2, TokenState.FINISHED, 57)
            ),
            isFinished = true,
            finishRank = 1
        )

        val json = LudoGameStateSerializer.playerToJson(player)
        val parsed = LudoGameStateSerializer.playerFromJson(json)

        assertEquals(2, parsed.playerId)
        assertEquals(LudoColor.GREEN, parsed.color)
        assertEquals("Green (P2)", parsed.name)
        assertTrue(parsed.isFinished)
        assertEquals(1, parsed.finishRank)
        assertEquals(4, parsed.finishedTokenCount)
        assertTrue(parsed.hasWon)
    }

    // =========================================================================
    // 3. LudoMoveResult serialization & deserialization tests
    // =========================================================================

    @Test
    fun `test move result serialization roundtrip with captures and extra turns`() {
        val moveResult = LudoMoveResult(
            playerId = 1,
            tokenId = 0,
            diceValue = 6,
            fromStep = 10,
            toStep = 16,
            traversedSteps = listOf(11, 12, 13, 14, 15, 16),
            capturedTokens = listOf(
                LudoToken(tokenId = 2, playerId = 3, state = TokenState.IN_BASE, stepCount = 0)
            ),
            enteredHome = false,
            finishedToken = false,
            extraTurnGranted = true,
            extraTurnReason = ExtraTurnReason.CAPTURED_OPPONENT,
            isGameOver = false,
            nextPlayerId = 1
        )

        val json = LudoGameStateSerializer.moveResultToJson(moveResult)
        val parsed = LudoGameStateSerializer.moveResultFromJson(json)

        assertEquals(1, parsed.playerId)
        assertEquals(0, parsed.tokenId)
        assertEquals(6, parsed.diceValue)
        assertEquals(10, parsed.fromStep)
        assertEquals(16, parsed.toStep)
        assertEquals(listOf(11, 12, 13, 14, 15, 16), parsed.traversedSteps)
        assertEquals(1, parsed.capturedTokens.size)
        assertEquals(2, parsed.capturedTokens[0].tokenId)
        assertEquals(3, parsed.capturedTokens[0].playerId)
        assertTrue(parsed.extraTurnGranted)
        assertEquals(ExtraTurnReason.CAPTURED_OPPONENT, parsed.extraTurnReason)
        assertEquals(1, parsed.nextPlayerId)
    }

    // =========================================================================
    // 4. Complete LudoGameState roundtrip tests
    // =========================================================================

    @Test
    fun `test complete LudoGameState roundtrip serialization for 4-player match`() {
        val engine = LudoGameEngine()
        assertTrue(engine.initGame(4))

        // Roll 6 -> tokens 0..3 are legal
        assertTrue(engine.onDiceRolled(6))
        val stateAfterRoll = engine.gameState.value

        val json = stateAfterRoll.toJsonObject()
        val restored = LudoGameStateSerializer.stateFromJson(json)

        assertTrue(restored.isGameStarted)
        assertEquals(4, restored.playerCount)
        assertEquals(1, restored.currentPlayerId)
        assertEquals(6, restored.diceValue)
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION, restored.turnPhase)
        assertEquals(setOf(0, 1, 2, 3), restored.legalTokenIds)
        assertEquals(1, restored.consecutiveSixCount)
        assertEquals(4, restored.players.size)

        // Move token 0 -> verify move result serialization
        val moveRes = engine.moveToken(0)
        assertNotNull(moveRes)
        val stateAfterMove = engine.gameState.value

        val moveJson = stateAfterMove.toJsonObject()
        val restoredAfterMove = LudoGameStateSerializer.stateFromJson(moveJson)

        assertEquals(1, restoredAfterMove.players[0].tokens[0].stepCount)
        assertEquals(TokenState.ON_BOARD, restoredAfterMove.players[0].tokens[0].state)
        assertNotNull(restoredAfterMove.lastMoveResult)
        assertEquals(1, restoredAfterMove.lastMoveResult?.toStep)
    }

    // =========================================================================
    // 5. ConfigMsg backward-compatibility & Ludo state payload tests
    // =========================================================================

    @Test
    fun `test ConfigMsg backward-compatibility preserves root fields and includes authoritative ludoState`() {
        val engine = LudoGameEngine()
        engine.initGame(3)

        val configMsg = ConfigMsg(
            boxCount = 3,
            turnId = 42L,
            activeBoxId = 1,
            isGameStarted = true,
            ludoState = engine.gameState.value,
            requestId = "req-cfg-test"
        )

        val jsonStr = configMsg.toJsonString()
        val parsed = NetworkMessage.fromJson(jsonStr) as? ConfigMsg

        assertNotNull("ConfigMsg must parse successfully", parsed)
        assertEquals(3, parsed!!.boxCount)
        assertEquals(42L, parsed.turnId)
        assertEquals(1, parsed.activeBoxId)
        assertTrue(parsed.isGameStarted)
        assertEquals("req-cfg-test", parsed.requestId)

        // Authoritative Ludo state payload
        assertNotNull("LudoState payload must be present in parsed ConfigMsg", parsed.ludoState)
        assertEquals(3, parsed.ludoState!!.playerCount)
        assertEquals(1, parsed.ludoState!!.currentPlayerId)
        assertEquals(3, parsed.ludoState!!.players.size)
    }

    // =========================================================================
    // 6. StateSyncMsg roundtrip and payload tests
    // =========================================================================

    @Test
    fun `test StateSyncMsg transports complete authoritative Ludo board state`() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        engine.onDiceRolled(6)
        engine.moveToken(0) // Token 0 now at step 1

        val stateSyncMsg = StateSyncMsg(
            boxCount = 4,
            turnId = 5L,
            activeBoxId = 1,
            isGameStarted = true,
            ludoState = engine.gameState.value,
            requestId = "sync-req-123"
        )

        val jsonStr = stateSyncMsg.toJsonString()
        val parsed = NetworkMessage.fromJson(jsonStr) as? StateSyncMsg

        assertNotNull(parsed)
        assertEquals(4, parsed!!.boxCount)
        assertEquals(5L, parsed.turnId)
        assertEquals(1, parsed.activeBoxId)
        assertTrue(parsed.isGameStarted)
        assertEquals("sync-req-123", parsed.requestId)

        val ludoState = parsed.ludoState
        assertNotNull(ludoState)
        assertEquals(TokenState.ON_BOARD, ludoState!!.players[0].tokens[0].state)
        assertEquals(1, ludoState.players[0].tokens[0].stepCount)
        assertEquals(TokenState.IN_BASE, ludoState.players[0].tokens[1].state)
    }

    // =========================================================================
    // 7. LudoStateSyncMsg and GetLudoStateMsg protocol verification
    // =========================================================================

    @Test
    fun `test dedicated LudoStateSyncMsg and GetLudoStateMsg wire protocol framing`() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        val ludoSync = LudoStateSyncMsg(
            ludoState = engine.gameState.value,
            turnId = 10L,
            requestId = "ludo-sync-req"
        )

        val jsonStr = ludoSync.toJsonString()
        val parsedSync = NetworkMessage.fromJson(jsonStr) as? LudoStateSyncMsg

        assertNotNull(parsedSync)
        assertEquals("LUDO_STATE_SYNC", parsedSync!!.type)
        assertEquals(2, parsedSync.ludoState.playerCount)
        assertEquals(10L, parsedSync.turnId)
        assertEquals("ludo-sync-req", parsedSync.requestId)

        // GetLudoStateMsg
        val getLudo = GetLudoStateMsg(requestId = "get-ludo-req")
        val parsedGet = NetworkMessage.fromJson(getLudo.toJsonString()) as? GetLudoStateMsg
        assertNotNull(parsedGet)
        assertEquals("GET_LUDO_STATE", parsedGet!!.type)
        assertEquals("get-ludo-req", parsedGet.requestId)
    }
}
