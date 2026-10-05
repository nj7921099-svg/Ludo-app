package com.example

import com.example.game.RandomNumberGameEngine
import com.example.game.model.RollSource
import com.example.network.NetworkConstants
import com.example.network.model.AckMsg
import com.example.network.model.BoxIdParseResult
import com.example.network.model.BoxIdParser
import com.example.network.model.ConfigMsg
import com.example.network.model.GameEventMsg
import com.example.network.model.HandshakeAckMsg
import com.example.network.model.HandshakeMsg
import com.example.network.model.HostConnectionState
import com.example.network.model.NetworkConnectionState
import com.example.network.model.NetworkMessage
import com.example.network.model.NumberResultMsg
import com.example.network.model.NumberSelectionMsg
import com.example.network.model.PingMsg
import com.example.network.model.PongMsg
import com.example.network.model.SendResult
import com.example.network.service.NetworkRequestRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

        // Tap R1, R2, R3
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
    fun `test scenario 5 - Wi-Fi NetworkMessage serialization and framing`() {
        // Handshake
        val hs = HandshakeMsg(
            protocolVersion = 1,
            role = NetworkConstants.ROLE_CONTROLLER,
            deviceName = "Controller Phone",
            requestId = "req-100"
        )
        val hsJson = hs.toJsonString()
        val parsedHs = NetworkMessage.fromJson(hsJson) as? HandshakeMsg
        assertNotNull(parsedHs)
        assertEquals(1, parsedHs?.protocolVersion)
        assertEquals(NetworkConstants.ROLE_CONTROLLER, parsedHs?.role)
        assertEquals("Controller Phone", parsedHs?.deviceName)

        // Handshake ACK
        val hsAck = HandshakeAckMsg(
            protocolVersion = 1,
            role = NetworkConstants.ROLE_HOST,
            status = "OK",
            deviceName = "Ludo Host",
            requestId = "req-100"
        )
        val parsedHsAck = NetworkMessage.fromJson(hsAck.toJsonString()) as? HandshakeAckMsg
        assertNotNull(parsedHsAck)
        assertEquals("OK", parsedHsAck?.status)
        assertEquals("req-100", parsedHsAck?.requestId)

        // Number Selection
        val numSel = NumberSelectionMsg(
            value = 5,
            boxId = 4,
            requestId = "req-999"
        )
        val parsedNum = NetworkMessage.fromJson(numSel.toJsonString()) as? NumberSelectionMsg
        assertNotNull(parsedNum)
        assertEquals(5, parsedNum?.value)
        assertEquals(4, parsedNum?.boxId)
        assertEquals("req-999", parsedNum?.requestId)

        // ACK
        val ack = AckMsg(
            requestId = "req-999",
            status = "OK",
            reason = "Stored pending for R4"
        )
        val parsedAck = NetworkMessage.fromJson(ack.toJsonString()) as? AckMsg
        assertNotNull(parsedAck)
        assertEquals("req-999", parsedAck?.requestId)
        assertEquals("OK", parsedAck?.status)

        // Number Result
        val res = NumberResultMsg(
            boxId = 4,
            turnId = 3,
            value = 5,
            source = "REMOTE",
            rawBoxId = "B4",
            requestId = "req-999"
        )
        val parsedRes = NetworkMessage.fromJson(res.toJsonString()) as? NumberResultMsg
        assertNotNull(parsedRes)
        assertEquals(4, parsedRes?.boxId)
        assertEquals(5, parsedRes?.value)
        assertEquals("REMOTE", parsedRes?.source)

        // Ping / Pong
        val ping = PingMsg(requestId = "ping-1")
        val pong = PongMsg(requestId = "ping-1", originalTimestamp = 1000L)
        assertNotNull(NetworkMessage.fromJson(ping.toJsonString()) as? PingMsg)
        assertNotNull(NetworkMessage.fromJson(pong.toJsonString()) as? PongMsg)
    }

    @Test
    fun `test scenario 6 - boxId string like R4 is parsed correctly`() {
        val rawJson = """
            {
               "type": "NUMBER_SELECTION",
               "requestId": "cmd-test",
               "boxId": "R4",
               "value": 5
            }
        """.trimIndent()
        val parsed = NetworkMessage.fromJson(rawJson) as? NumberSelectionMsg
        assertNotNull(parsed)
        assertEquals(4, parsed?.boxId)
        assertEquals("R4", parsed?.rawBoxId)
        assertTrue(parsed!!.isExplicitBoxIdProvided)
        assertTrue(parsed.isBoxIdValid)
        assertEquals(5, parsed.value)
        assertEquals("cmd-test", parsed.requestId)
    }

    @Test
    fun `test scenario 7 - TCP port is consistently unified to 8888`() {
        assertEquals("Authoritative TCP port must be 8888", 8888, NetworkConstants.LUDO_TCP_PORT)
        assertEquals("DEFAULT_PORT must equal LUDO_TCP_PORT", 8888, NetworkConstants.DEFAULT_PORT)
        val state = NetworkConnectionState()
        assertEquals("NetworkConnectionState serverPort must default to 8888", 8888, state.serverPort)
    }

    @Test
    fun `test scenario 8 - real TCP write SendResult model behavior`() {
        val success = SendResult.Success("HANDSHAKE", 42)
        assertEquals("HANDSHAKE", success.messageType)
        assertEquals(42, success.bytesWritten)

        val failure = SendResult.Failure("Socket is closed", java.net.SocketException("Closed"))
        assertTrue("Failure must detect closed socket", failure.isClosedSocket)
    }

    @Test
    fun `test scenario 9 - NetworkRequestRegistry correlates requestId with ACK and NUMBER_RESULT`() = runBlocking {
        val registry = NetworkRequestRegistry()
        val testReqId = "req-test-1234"

        val ackDeferred = registry.registerPendingAck(testReqId)
        val resultDeferred = registry.registerPendingResult(testReqId)

        assertFalse(ackDeferred.isCompleted)
        assertFalse(resultDeferred.isCompleted)

        // Dispatch unrelated ACK (different requestId) -> must NOT complete deferred
        val unrelatedAck = AckMsg(requestId = "unrelated-id", status = "OK")
        val dispatchedUnrelated = registry.dispatchAck(unrelatedAck)
        assertFalse("Unrelated ACK should not match", dispatchedUnrelated)
        assertFalse(ackDeferred.isCompleted)

        // Dispatch matching ACK
        val matchingAck = AckMsg(requestId = testReqId, status = "OK", reason = "Accepted")
        val dispatchedAck = registry.dispatchAck(matchingAck)
        assertTrue("Matching ACK must dispatch", dispatchedAck)
        assertTrue(ackDeferred.isCompleted)
        val receivedAck = ackDeferred.await()
        assertEquals(testReqId, receivedAck.requestId)
        assertEquals("OK", receivedAck.status)

        // Dispatch matching NUMBER_RESULT
        val matchingResult = NumberResultMsg(boxId = 2, turnId = 1, value = 6, source = "REMOTE", requestId = testReqId)
        val dispatchedResult = registry.dispatchResult(matchingResult)
        assertTrue("Matching NUMBER_RESULT must dispatch", dispatchedResult)
        assertTrue(resultDeferred.isCompleted)
        val receivedResult = resultDeferred.await()
        assertEquals(testReqId, receivedResult.requestId)
        assertEquals(6, receivedResult.value)
    }

    @Test
    fun `test scenario 10 - request timeout protection works properly`() = runBlocking {
        val registry = NetworkRequestRegistry()
        val testReqId = "req-timeout-check"

        val ackDeferred = registry.registerPendingAck(testReqId)

        try {
            withTimeout(100L) {
                ackDeferred.await()
            }
            fail("Expected TimeoutCancellationException")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Expected
            registry.remove(testReqId)
        }

        assertFalse("Ack should remain uncompleted on timeout", ackDeferred.isCompleted)
    }

    @Test
    fun `test scenario 11 - BoxIdParser correctly normalizes B1-B6 to 1-6`() {
        val bMappings = mapOf(
            "B1" to 1,
            "B2" to 2,
            "B3" to 3,
            "B4" to 4,
            "B5" to 5,
            "B6" to 6,
            "b1" to 1,
            "b6" to 6
        )

        bMappings.forEach { (raw, expectedNum) ->
            val result = BoxIdParser.parse(raw)
            assertTrue("Expected Valid for $raw", result is BoxIdParseResult.Valid)
            assertEquals("Normalized number for $raw", expectedNum, (result as BoxIdParseResult.Valid).boxNumber)
        }
    }

    @Test
    fun `test scenario 12 - BoxIdParser correctly normalizes R1-R6 to 1-6`() {
        val rMappings = mapOf(
            "R1" to 1,
            "R2" to 2,
            "R3" to 3,
            "R4" to 4,
            "R5" to 5,
            "R6" to 6,
            "r3" to 3
        )

        rMappings.forEach { (raw, expectedNum) ->
            val result = BoxIdParser.parse(raw)
            assertTrue("Expected Valid for $raw", result is BoxIdParseResult.Valid)
            assertEquals("Normalized number for $raw", expectedNum, (result as BoxIdParseResult.Valid).boxNumber)
        }
    }

    @Test
    fun `test scenario 13 - BoxIdParser identifies invalid boxIds without silent fallback`() {
        val invalidInputs = listOf("B0", "B7", "B99", "R0", "R8", "0", "7", "invalid", "X3", "-1")

        invalidInputs.forEach { invalid ->
            val result = BoxIdParser.parse(invalid)
            assertTrue("Expected Invalid for '$invalid'", result is BoxIdParseResult.Invalid)
        }

        val notSpecifiedInputs = listOf(null, "", "   ")
        notSpecifiedInputs.forEach { empty ->
            val result = BoxIdParser.parse(empty)
            assertEquals("Expected NotSpecified for '$empty'", BoxIdParseResult.NotSpecified, result)
        }
    }

    @Test
    fun `test scenario 14 - Controller NUMBER_SELECTION with B3 parses accurately`() {
        val json = """
            {
               "type": "NUMBER_SELECTION",
               "requestId": "ctrl-req-123",
               "boxId": "B3",
               "value": 5
            }
        """.trimIndent()

        val parsed = NetworkMessage.fromJson(json) as? NumberSelectionMsg
        assertNotNull(parsed)
        assertEquals(3, parsed?.boxId)
        assertEquals("B3", parsed?.rawBoxId)
        assertTrue(parsed!!.isExplicitBoxIdProvided)
        assertTrue(parsed.isBoxIdValid)
        assertEquals(5, parsed.value)
        assertEquals("ctrl-req-123", parsed.requestId)
    }

    @Test
    fun `test scenario 15 - Controller NUMBER_SELECTION with invalid boxId flags error`() {
        val json = """
            {
               "type": "NUMBER_SELECTION",
               "requestId": "ctrl-req-bad",
               "boxId": "INVALID_BOX",
               "value": 4
            }
        """.trimIndent()

        val parsed = NetworkMessage.fromJson(json) as? NumberSelectionMsg
        assertNotNull(parsed)
        assertNull(parsed?.boxId)
        assertEquals("INVALID_BOX", parsed?.rawBoxId)
        assertTrue(parsed!!.isExplicitBoxIdProvided)
        assertFalse(parsed.isBoxIdValid)
        assertNotNull(parsed.boxIdErrorReason)
    }

    @Test
    fun `test required case 1 - Controller B4 to 6 stores as pending and nothing is revealed immediately`() {
        val engine = RandomNumberGameEngine(4)
        engine.selectBoxCount(4)
        engine.startGame()

        // Incoming Controller command: B4 -> 6
        val parsedBox = (BoxIdParser.parse("B4") as BoxIdParseResult.Valid).boxNumber
        val queued = engine.queueControllerCommand("req-t1", parsedBox, 6)
        assertTrue("Command B4 -> 6 must be accepted", queued)

        // R4 must store 6 as pending
        assertEquals(6, engine.boxesState.value[4]?.pendingApp2Value)
        assertEquals("req-t1", engine.boxesState.value[4]?.pendingApp2RequestId)

        // CRITICAL: Nothing is revealed immediately!
        assertNull("R4 must NOT be revealed immediately upon command receipt", engine.boxesState.value[4]?.currentValue)
        assertEquals("Active turn must still be R1", 1, engine.activeBoxId.value)
    }

    @Test
    fun `test required case 2 - Current turn is R2, user taps R2, R2 does not get R4 pending value`() {
        val engine = RandomNumberGameEngine(4)
        engine.selectBoxCount(4)
        engine.startGame()

        // Controller sent B4 -> 6
        engine.queueControllerCommand("req-t2", 4, 6)

        // Move to turn R2 by tapping R1
        engine.tapBox(1)
        assertEquals(2, engine.activeBoxId.value)

        // User taps R2
        val r2Result = engine.tapBox(2)
        assertNotNull(r2Result)

        // R2 must NOT get 6 from R4! It uses its own local random (1..6)
        assertEquals(RollSource.LOCAL, r2Result!!.source)
        assertNull("R2 has no pending command", r2Result.commandId)

        // R4 remains pending with 6!
        assertEquals("R4 pending value 6 must remain intact", 6, engine.boxesState.value[4]?.pendingApp2Value)
        assertNull("R4 is not revealed yet", engine.boxesState.value[4]?.currentValue)
    }

    @Test
    fun `test required case 3 - Current turn is R4, user taps R4, R4 reveals 6 and consumes command exactly once`() {
        val engine = RandomNumberGameEngine(4)
        engine.selectBoxCount(4)
        engine.startGame()

        // Controller sent B4 -> 6
        engine.queueControllerCommand("req-t3", 4, 6)

        // Progress turns: R1 -> R2 -> R3 -> R4
        engine.tapBox(1)
        engine.tapBox(2)
        engine.tapBox(3)
        assertEquals(4, engine.activeBoxId.value)

        // User taps R4
        val r4Result = engine.tapBox(4)
        assertNotNull(r4Result)
        assertEquals(6, r4Result!!.value)
        assertEquals(RollSource.REMOTE, r4Result.source)
        assertEquals("req-t3", r4Result.commandId)
        assertEquals("req-t3", r4Result.requestId)

        // Pending command must be cleared/consumed exactly once!
        assertNull("R4 pending command must be cleared after consumption", engine.boxesState.value[4]?.pendingApp2Value)
        assertEquals(6, engine.boxesState.value[4]?.currentValue)

        // Consumed command cannot be reused or re-queued
        val reuseRejected = engine.queueControllerCommand("req-t3", 4, 6)
        assertFalse("Consumed commandId must be rejected if resent", reuseRejected)
    }

    @Test
    fun `test required case 4 - Controller sends B4 to 6, B2 to 3, B5 to 1, independent pending commands`() {
        val engine = RandomNumberGameEngine(5)
        engine.selectBoxCount(5)
        engine.startGame()

        assertTrue(engine.queueControllerCommand("cmd-b4", 4, 6))
        assertTrue(engine.queueControllerCommand("cmd-b2", 2, 3))
        assertTrue(engine.queueControllerCommand("cmd-b5", 5, 1))

        assertEquals(6, engine.boxesState.value[4]?.pendingApp2Value)
        assertEquals(3, engine.boxesState.value[2]?.pendingApp2Value)
        assertEquals(1, engine.boxesState.value[5]?.pendingApp2Value)

        // Tap R1 -> local random
        val r1 = engine.tapBox(1)
        assertEquals(RollSource.LOCAL, r1!!.source)

        // Tap R2 -> reveals 3
        val r2 = engine.tapBox(2)
        assertEquals(3, r2!!.value)
        assertEquals(RollSource.REMOTE, r2.source)
        assertEquals("cmd-b2", r2.commandId)

        // R4 and R5 still preserve their exact pending values!
        assertEquals(6, engine.boxesState.value[4]?.pendingApp2Value)
        assertEquals(1, engine.boxesState.value[5]?.pendingApp2Value)

        // Tap R3 -> local random
        val r3 = engine.tapBox(3)
        assertEquals(RollSource.LOCAL, r3!!.source)

        // Tap R4 -> reveals 6
        val r4 = engine.tapBox(4)
        assertEquals(6, r4!!.value)
        assertEquals("cmd-b4", r4.commandId)

        // Tap R5 -> reveals 1
        val r5 = engine.tapBox(5)
        assertEquals(1, r5!!.value)
        assertEquals("cmd-b5", r5.commandId)
    }

    @Test
    fun `test required case 5 - Duplicate requestId received, only one command exists and only one game action can consume it`() {
        val engine = RandomNumberGameEngine(4)
        engine.selectBoxCount(4)
        engine.startGame()

        // First attempt with req-dup
        val first = engine.queueControllerCommand("req-dup", 2, 5)
        assertTrue(first)

        // Second attempt with identical req-dup (e.g. TCP retry)
        val second = engine.queueControllerCommand("req-dup", 2, 5)
        assertTrue(second) // Idempotent acknowledgment

        // Only ONE pending command exists on R2
        assertEquals(5, engine.boxesState.value[2]?.pendingApp2Value)

        // Advance to R2 and tap
        engine.tapBox(1)
        val r2Result = engine.tapBox(2)
        assertEquals(5, r2Result!!.value)
        assertEquals("req-dup", r2Result.commandId)

        // After consumption, a third attempt with req-dup MUST be rejected
        val thirdAfterConsume = engine.queueControllerCommand("req-dup", 2, 5)
        assertFalse("Already-consumed command must be rejected", thirdAfterConsume)
    }

    @Test
    fun `test required case 6 - Invalid boxId or invalid number is rejected safely without fallback to active box`() {
        val engine = RandomNumberGameEngine(4)
        engine.selectBoxCount(4)
        engine.startGame()

        // Turn is currently R1
        assertEquals(1, engine.activeBoxId.value)

        // Case 6A: Number out of range (> 6 or < 1)
        assertFalse("Value 0 must be rejected", engine.queueControllerCommand("bad-1", 1, 0))
        assertFalse("Value 7 must be rejected", engine.queueControllerCommand("bad-2", 1, 7))

        // Case 6B: Box out of range (> 4 for 4-player game)
        assertFalse("Box 5 in a 4-player game must be rejected", engine.queueControllerCommand("bad-3", 5, 4))
        assertFalse("Box 0 must be rejected", engine.queueControllerCommand("bad-4", 0, 4))

        // Active box R1 must NOT have any pending command created by these invalid requests
        assertNull("R1 must remain free of invalid pending commands", engine.boxesState.value[1]?.pendingApp2Value)

        // BoxIdParser rejection verification
        assertTrue(BoxIdParser.parse("B99") is BoxIdParseResult.Invalid)
        assertTrue(BoxIdParser.parse("INVALID_BOX") is BoxIdParseResult.Invalid)
    }
}
