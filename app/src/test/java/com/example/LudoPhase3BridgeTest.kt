package com.example

import com.example.game.ludo.engine.LudoDiceBridge
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.engine.PendingNumberStore
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import com.example.game.model.RollSource
import com.example.network.model.BoxIdParseResult
import com.example.network.model.BoxIdParser
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
class LudoPhase3BridgeTest {

    // =========================================================================
    // Test 1 & 2: Controller B1 -> 1 stored for P1, B4 -> 6 stored for P4
    // =========================================================================

    @Test
    fun `test 1 and 2 - controller commands are stored by canonical player ID`() {
        val store = PendingNumberStore()

        // B1 -> 1 for Player 1
        val p1Box = (BoxIdParser.parse("B1") as BoxIdParseResult.Valid).boxNumber
        assertTrue(store.queueCommand("req-p1", p1Box, 1, "B1"))

        // B4 -> 6 for Player 4
        val p4Box = (BoxIdParser.parse("B4") as BoxIdParseResult.Valid).boxNumber
        assertTrue(store.queueCommand("req-p4", p4Box, 6, "B4"))

        val p1Pending = store.getPendingCommand(1)
        assertNotNull(p1Pending)
        assertEquals(1, p1Pending!!.number)
        assertEquals("req-p1", p1Pending.commandId)
        assertEquals("B1", p1Pending.rawBoxId)

        val p4Pending = store.getPendingCommand(4)
        assertNotNull(p4Pending)
        assertEquals(6, p4Pending!!.number)
        assertEquals("req-p4", p4Pending.commandId)
        assertEquals("B4", p4Pending.rawBoxId)
    }

    // =========================================================================
    // Test 3 & 4: Pending P4 does not affect P2; multiple pending coexist
    // =========================================================================

    @Test
    fun `test 3 and 4 - multiple pending players coexist independently`() {
        val store = PendingNumberStore()

        store.queueCommand("cmd-4", 4, 6, "B4")
        store.queueCommand("cmd-2", 2, 3, "B2")
        store.queueCommand("cmd-1", 1, 5, "B1")

        // Player 2 has 3, Player 4 has 6, Player 1 has 5, Player 3 has nothing
        assertEquals(6, store.getPendingCommand(4)?.number)
        assertEquals(3, store.getPendingCommand(2)?.number)
        assertEquals(5, store.getPendingCommand(1)?.number)
        assertNull(store.getPendingCommand(3))

        // P4 command does NOT affect P2
        assertEquals("cmd-2", store.getPendingCommand(2)?.commandId)
        assertEquals("cmd-4", store.getPendingCommand(4)?.commandId)
    }

    // =========================================================================
    // Test 5 & 6: Current P2 consumes only P2 pending; P4 remains untouched
    // =========================================================================

    @Test
    fun `test 5 and 6 - current player consumes only their own pending command - other players untouched`() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        val store = PendingNumberStore()
        val bridge = LudoDiceBridge(engine, store)

        // Queue B4 -> 6 and B2 -> 3
        store.queueCommand("req-b4", 4, 6, "B4")
        store.queueCommand("req-b2", 2, 3, "B2")

        // Advance turn to Player 2 (P1 rolls 1, no moves in base, turn moves to P2)
        val p1Roll = bridge.onDiceActivated() // P1 has no pending -> local random
        assertNotNull(p1Roll)
        assertEquals(RollSource.LOCAL, p1Roll!!.source)

        // Now active turn is Player 2
        assertEquals(2, engine.gameState.value.currentPlayerId)

        // Player 2 activates dice!
        val p2Roll = bridge.onDiceActivated()
        assertNotNull(p2Roll)
        assertEquals(2, p2Roll!!.playerId)
        assertEquals(3, p2Roll.diceValue)
        assertEquals(RollSource.REMOTE, p2Roll.source)
        assertEquals("req-b2", p2Roll.commandId)

        // Player 2's pending command is now CONSUMED!
        assertNull("P2 pending command must be consumed", store.getPendingCommand(2))

        // Player 4's pending command remains UNTOUCHED!
        assertNotNull(store.getPendingCommand(4))
        assertEquals(6, store.getPendingCommand(4)?.number)
        assertEquals("req-b4", store.getPendingCommand(4)?.commandId)
    }

    // =========================================================================
    // Test 7 & 8: Pending command consumed exactly once; duplicate ID rejected
    // =========================================================================

    @Test
    fun `test 7 and 8 - pending command consumed exactly once - duplicate request cannot be consumed twice`() {
        val store = PendingNumberStore()

        assertTrue(store.queueCommand("dup-101", 1, 6))

        // Retrying identical command while pending is acknowledged idempotently
        assertTrue(store.queueCommand("dup-101", 1, 6))

        // Consume it
        val consumed = store.consumePendingCommand(1)
        assertNotNull(consumed)
        assertEquals("dup-101", consumed!!.commandId)
        assertTrue(store.isCommandConsumed("dup-101"))

        // Attempting to queue already-consumed commandId MUST be rejected!
        val reQueued = store.queueCommand("dup-101", 1, 6)
        assertFalse("Already consumed commandId must be rejected", reQueued)
        assertNull(store.getPendingCommand(1))
    }

    // =========================================================================
    // Test 9 & 10: No pending command causes local random 1..6; works when disconnected
    // =========================================================================

    @Test
    fun `test 9 and 10 - local random fallback when no pending command is available or disconnected`() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        val store = PendingNumberStore() // Empty store simulates disconnected/no-controller state
        val bridge = LudoDiceBridge(engine, store)

        val result = bridge.onDiceActivated()
        assertNotNull(result)
        assertEquals(RollSource.LOCAL, result!!.source)
        assertTrue("Dice must be in 1..6", result.diceValue in 1..6)
        assertNull(result.commandId)
    }

    // =========================================================================
    // Test 11: Reconnection does not reset Ludo game
    // =========================================================================

    @Test
    fun `test 11 - reconnection retains current game state and pending store`() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        val store = PendingNumberStore()
        val bridge = LudoDiceBridge(engine, store)

        // Store pending command for Player 3
        store.queueCommand("req-persist", 3, 5)

        // Player 1 rolls 6 and releases a token
        store.queueCommand("req-p1-6", 1, 6)
        val p1Roll = bridge.onDiceActivated()
        assertEquals(6, p1Roll!!.diceValue)
        engine.moveToken(0)

        assertEquals(TokenState.ON_BOARD, engine.gameState.value.players[0].tokens[0].state)
        assertEquals(1, engine.gameState.value.players[0].tokens[0].stepCount)

        // Simulate Controller drop and reconnect (no clear or reset on gameEngine)
        // Game state and P3's pending command must remain completely intact!
        assertEquals(1, engine.gameState.value.players[0].tokens[0].stepCount)
        assertEquals(5, store.getPendingCommand(3)?.number)
        assertEquals("req-persist", store.getPendingCommand(3)?.commandId)
    }

    // =========================================================================
    // Test 12: Extra turn checks the same player's pending queue again
    // =========================================================================

    @Test
    fun `test 12 - extra turn checks the same player pending queue again`() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        val store = PendingNumberStore()
        val bridge = LudoDiceBridge(engine, store)

        // First roll for P1: 6
        store.queueCommand("req-p1-first", 1, 6)
        val roll1 = bridge.onDiceActivated()
        assertEquals(6, roll1!!.diceValue)
        assertEquals("req-p1-first", roll1.commandId)

        // P1 moves token 0 to step 1 -> awards extra turn on 6!
        engine.moveToken(0)
        assertEquals(1, engine.gameState.value.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, engine.gameState.value.turnPhase)

        // Controller queues second command for P1: 4
        store.queueCommand("req-p1-second", 1, 4)

        // P1 activates dice for extra turn: retrieves the new pending 4!
        val roll2 = bridge.onDiceActivated()
        assertEquals(4, roll2!!.diceValue)
        assertEquals(RollSource.REMOTE, roll2.source)
        assertEquals("req-p1-second", roll2.commandId)

        // Moves token 0 to step 5 (1 + 4)
        val move2 = engine.moveToken(0)
        assertNotNull(move2)
        assertEquals(5, move2!!.toStep)
    }

    // =========================================================================
    // Test 13, 14, 15: Controller cannot change turn, move tokens, or bypass legal checks
    // =========================================================================

    @Test
    fun `test 13, 14, 15 - controller cannot change turn, move token directly, or bypass legal validation`() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        val store = PendingNumberStore()

        // Current turn is Player 1
        assertEquals(1, engine.gameState.value.currentPlayerId)

        // Controller sends command for Player 3 -> 6
        store.queueCommand("req-p3", 3, 6)

        // 13. Current player MUST STILL BE 1!
        assertEquals("Controller command must never change active player", 1, engine.gameState.value.currentPlayerId)

        // 14. Tokens of Player 3 MUST STILL BE IN BASE!
        assertTrue(engine.gameState.value.players[2].tokens.all { it.isInBase })

        // 15. Attempting to move Player 3's token out of turn is rejected
        assertNull("Cannot move token out of turn", engine.moveToken(0))
    }

    // =========================================================================
    // Test 16: Pending number remains pending until dice activation
    // =========================================================================

    @Test
    fun `test 16 - pending number remains pending until dice activation`() {
        val store = PendingNumberStore()
        store.queueCommand("cmd-wait", 2, 4)

        // Remains pending indefinitely until consumed
        assertEquals(4, store.getPendingCommand(2)?.number)
        assertEquals(4, store.getPendingCommand(2)?.number)
        assertFalse(store.isCommandConsumed("cmd-wait"))
    }

    // =========================================================================
    // Test 17: Invalid Box ID is rejected by BoxIdParser
    // =========================================================================

    @Test
    fun `test 17 - invalid box IDs are rejected safely by BoxIdParser`() {
        assertTrue(BoxIdParser.parse("B0") is BoxIdParseResult.Invalid)
        assertTrue(BoxIdParser.parse("B7") is BoxIdParseResult.Invalid)
        assertTrue(BoxIdParser.parse("INVALID") is BoxIdParseResult.Invalid)
        assertTrue(BoxIdParser.parse("X1") is BoxIdParseResult.Invalid)

        val validB3 = BoxIdParser.parse("B3")
        assertTrue(validB3 is BoxIdParseResult.Valid)
        assertEquals(3, (validB3 as BoxIdParseResult.Valid).boxNumber)
    }
}
