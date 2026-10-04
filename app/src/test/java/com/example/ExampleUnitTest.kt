package com.example

import com.example.bluetooth.model.BluetoothMessage
import com.example.bluetooth.model.CommandAckMessage
import com.example.bluetooth.model.ControllerCommandMessage
import com.example.bluetooth.model.GameConfigurationMessage
import com.example.bluetooth.model.NumberResultMessage
import com.example.game.RandomNumberGameEngine
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
class ExampleUnitTest {

    @Test
    fun `test scenario 1 - controller can queue commands for any box at any time`() {
        val engine = RandomNumberGameEngine(5)
        engine.selectBoxCount(5)
        engine.startGame()

        // Turn is currently R1
        assertEquals(1, engine.activeBoxId.value)

        // Controller sends command for R4 -> 6
        val r4Queued = engine.queueControllerCommand("cmd-1001", 4, 6)
        assertTrue("Command for R4 should be accepted while turn is R1", r4Queued)

        // Controller sends command for R2 -> 3
        val r2Queued = engine.queueControllerCommand("cmd-1002", 2, 3)
        assertTrue("Command for R2 should be accepted while turn is R1", r2Queued)

        // Both pending commands coexist independently
        assertEquals(6, engine.boxesState.value[4]?.pendingApp2Value)
        assertEquals("cmd-1001", engine.boxesState.value[4]?.pendingApp2RequestId)

        assertEquals(3, engine.boxesState.value[2]?.pendingApp2Value)
        assertEquals("cmd-1002", engine.boxesState.value[2]?.pendingApp2RequestId)

        // Neither R4 nor R2 is revealed yet!
        assertNull(engine.boxesState.value[4]?.currentValue)
        assertNull(engine.boxesState.value[2]?.currentValue)

        // R1 has NO pending command
        assertNull(engine.boxesState.value[1]?.pendingApp2Value)

        // User taps R1: Normal local random fallback occurs
        val r1Result = engine.tapBox(1)
        assertNotNull(r1Result)
        assertEquals(RollSource.LOCAL, r1Result!!.source)
        assertTrue("R1 value must be in 1..6", r1Result.value in 1..6)
        assertEquals(r1Result.value, engine.boxesState.value[1]?.currentValue)

        // Turn moves to R2
        assertEquals(2, engine.activeBoxId.value)

        // User taps R2: Reveals pending 3!
        val r2Result = engine.tapBox(2)
        assertNotNull(r2Result)
        assertEquals(3, r2Result!!.value)
        assertEquals(RollSource.REMOTE, r2Result.source)
        assertEquals("cmd-1002", r2Result.commandId)
        assertEquals(3, engine.boxesState.value[2]?.currentValue)
        assertNull("R2 pending command must be cleared after consumption", engine.boxesState.value[2]?.pendingApp2Value)

        // R4 pending command remains UNTOUCHED
        assertEquals(6, engine.boxesState.value[4]?.pendingApp2Value)
        assertNull(engine.boxesState.value[4]?.currentValue)

        // Turn moves to R3
        assertEquals(3, engine.activeBoxId.value)
        val r3Result = engine.tapBox(3) // R3 local roll
        assertNotNull(r3Result)
        assertEquals(RollSource.LOCAL, r3Result!!.source)

        // Turn moves to R4
        assertEquals(4, engine.activeBoxId.value)
        val r4Result = engine.tapBox(4) // Reveals pending 6!
        assertNotNull(r4Result)
        assertEquals(6, r4Result!!.value)
        assertEquals(RollSource.REMOTE, r4Result.source)
        assertEquals("cmd-1001", r4Result.commandId)
        assertEquals(6, engine.boxesState.value[4]?.currentValue)
        assertNull(engine.boxesState.value[4]?.pendingApp2Value)
    }

    @Test
    fun `test scenario 2 - duplicate commandId is recognized and idempotent`() {
        val engine = RandomNumberGameEngine(4)
        engine.selectBoxCount(4)
        engine.startGame()

        val first = engine.queueControllerCommand("cmd-555", 3, 4)
        assertTrue(first)

        // Retrying the same commandId should succeed without duplicating
        val duplicate = engine.queueControllerCommand("cmd-555", 3, 4)
        assertTrue(duplicate)

        assertEquals(4, engine.boxesState.value[3]?.pendingApp2Value)
    }

    @Test
    fun `test scenario 3 - consumed command cannot be reused`() {
        val engine = RandomNumberGameEngine(4)
        engine.selectBoxCount(4)
        engine.startGame()

        engine.queueControllerCommand("cmd-single-use", 1, 5)
        assertEquals(1, engine.activeBoxId.value)

        // Tap R1 -> consumes cmd-single-use
        val res = engine.tapBox(1)
        assertNotNull(res)
        assertEquals(5, res!!.value)

        // Attempting to send cmd-single-use again after consumption must be rejected
        val rejected = engine.queueControllerCommand("cmd-single-use", 1, 5)
        assertFalse("Consumed command must not be re-queued", rejected)
    }

    @Test
    fun `test scenario 4 - values never migrate across boxes`() {
        val engine = RandomNumberGameEngine(5)
        engine.selectBoxCount(5)
        engine.startGame()

        // Set R4 -> 6
        engine.queueControllerCommand("cmd-r4", 4, 6)

        // Tap R1, R2, R3, R5
        val r1 = engine.tapBox(1)
        val r2 = engine.tapBox(2)
        val r3 = engine.tapBox(3)

        // Neither R1, R2, R3 should ever take R4's value
        assertEquals(RollSource.LOCAL, r1!!.source)
        assertEquals(RollSource.LOCAL, r2!!.source)
        assertEquals(RollSource.LOCAL, r3!!.source)

        // R4 still preserves its pending 6
        assertEquals(6, engine.boxesState.value[4]?.pendingApp2Value)
    }

    @Test
    fun `test scenario 5 - message serialization for ControllerCommandMessage and CommandAckMessage`() {
        val cmd = ControllerCommandMessage(
            commandId = "1001",
            boxId = 4,
            value = 6
        )
        val jsonStr = cmd.toJsonString()
        val parsed = BluetoothMessage.fromJson(jsonStr) as? ControllerCommandMessage
        assertNotNull(parsed)
        assertEquals("1001", parsed?.commandId)
        assertEquals(4, parsed?.boxId)
        assertEquals(6, parsed?.value)

        val ack = CommandAckMessage(
            commandId = "1001",
            boxId = 4,
            accepted = true,
            reason = "Stored specifically for R4"
        )
        val ackJson = ack.toJsonString()
        val parsedAck = BluetoothMessage.fromJson(ackJson) as? CommandAckMessage
        assertNotNull(parsedAck)
        assertEquals("1001", parsedAck?.commandId)
        assertEquals(4, parsedAck?.boxId)
        assertTrue(parsedAck?.accepted == true)
        assertEquals("Stored specifically for R4", parsedAck?.reason)
    }

    @Test
    fun `test scenario 6 - boxId string like R4 is parsed correctly`() {
        val rawJson = """
            {
               "type": "CONTROLLER_COMMAND",
               "commandId": "cmd-test",
               "boxId": "R4",
               "value": 5
            }
        """.trimIndent()
        val parsed = BluetoothMessage.fromJson(rawJson) as? ControllerCommandMessage
        assertNotNull(parsed)
        assertEquals(4, parsed?.boxId)
        assertEquals(5, parsed?.value)
        assertEquals("cmd-test", parsed?.commandId)
    }
}
