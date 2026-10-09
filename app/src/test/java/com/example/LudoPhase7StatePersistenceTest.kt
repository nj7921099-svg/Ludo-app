package com.example

import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import com.example.game.ludo.persistence.LudoStatePersistence
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verification test suite for Phase 7 State Persistence & Recovery.
 *
 * Verifies atomic serialization, file I/O round trips, field fidelity,
 * schema validation, failure safety, error recovery, and engine state restoration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase7StatePersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var snapshotFile: File
    private lateinit var persistence: LudoStatePersistence

    @Before
    fun setUp() {
        snapshotFile = tempFolder.newFile("test_ludo_snapshot.json")
        persistence = LudoStatePersistence(snapshotFile)
    }

    @Test
    fun testSnapshotSerializationAndDeserializationRoundTrip() {
        val player1 = LudoPlayer(
            playerId = 1,
            color = LudoColor.RED,
            name = "Red Player",
            tokens = listOf(
                LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
                LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 14),
                LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_HOME_LANE, stepCount = 53),
                LudoToken(tokenId = 3, playerId = 1, state = TokenState.FINISHED, stepCount = 57)
            ),
            isFinished = false
        )
        val player2 = LudoPlayer(
            playerId = 2,
            color = LudoColor.YELLOW,
            name = "Yellow Player",
            tokens = (0..3).map { LudoToken(tokenId = it, playerId = 2, state = TokenState.IN_BASE, stepCount = 0) },
            isFinished = false
        )

        val originalState = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(player1, player2),
            currentPlayerId = 1,
            diceValue = 6,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = setOf(0, 1, 2),
            consecutiveSixCount = 1,
            winners = emptyList(),
            lastMoveResult = null
        )

        val json = persistence.stateToSnapshotJson(originalState)

        // Verify required authoritative fields are present
        assertEquals(LudoStatePersistence.CURRENT_SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertTrue(json.getBoolean("gameStarted"))
        assertEquals(2, json.getInt("playerCount"))
        assertEquals(1, json.getInt("currentPlayerId"))
        assertEquals(6, json.getInt("diceValue"))
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION.name, json.getString("turnPhase"))
        assertEquals(1, json.getInt("consecutiveSixCount"))

        // Verify transient fields are NOT persisted
        assertFalse(json.has("legalTokenIds"))
        assertFalse(json.has("lastMoveResult"))

        // Deserialization round trip
        val restored = persistence.snapshotJsonToState(json)
        assertNotNull(restored)
        assertEquals(originalState.isGameStarted, restored!!.isGameStarted)
        assertEquals(originalState.playerCount, restored.playerCount)
        assertEquals(originalState.currentPlayerId, restored.currentPlayerId)
        assertEquals(originalState.diceValue, restored.diceValue)
        assertEquals(originalState.turnPhase, restored.turnPhase)
        assertEquals(originalState.consecutiveSixCount, restored.consecutiveSixCount)
        assertEquals(2, restored.players.size)
        assertNull(restored.lastMoveResult)
    }

    @Test
    fun testPersistenceCompleteFileRoundTrip() {
        val engine = LudoGameEngine()
        assertTrue(engine.initGame(4))
        val state = engine.gameState.value

        assertTrue(persistence.saveState(state))
        assertTrue(persistence.hasSavedState())

        val restored = persistence.loadState()
        assertNotNull(restored)
        assertEquals(state.isGameStarted, restored!!.isGameStarted)
        assertEquals(state.playerCount, restored.playerCount)
        assertEquals(state.currentPlayerId, restored.currentPlayerId)
        assertEquals(4, restored.players.size)
    }

    @Test
    fun testRestoringPlayersAndTokenPositions() {
        val p1Tokens = listOf(
            LudoToken(tokenId = 0, playerId = 1, state = TokenState.IN_BASE, stepCount = 0),
            LudoToken(tokenId = 1, playerId = 1, state = TokenState.ON_BOARD, stepCount = 22),
            LudoToken(tokenId = 2, playerId = 1, state = TokenState.IN_HOME_LANE, stepCount = 55),
            LudoToken(tokenId = 3, playerId = 1, state = TokenState.FINISHED, stepCount = 57)
        )
        val p1 = LudoPlayer(playerId = 1, color = LudoColor.RED, name = "P1", tokens = p1Tokens)
        val p2 = LudoPlayer(
            playerId = 2,
            color = LudoColor.YELLOW,
            name = "P2",
            tokens = (0..3).map { LudoToken(tokenId = it, playerId = 2) }
        )

        val state = LudoGameState(
            isGameStarted = true,
            playerCount = 2,
            players = listOf(p1, p2),
            currentPlayerId = 1,
            turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL
        )

        assertTrue(persistence.saveState(state))
        val loaded = persistence.loadState()
        assertNotNull(loaded)

        val loadedP1 = loaded!!.players.find { it.playerId == 1 }
        assertNotNull(loadedP1)
        assertEquals(TokenState.IN_BASE, loadedP1!!.tokens[0].state)
        assertEquals(0, loadedP1.tokens[0].stepCount)

        assertEquals(TokenState.ON_BOARD, loadedP1.tokens[1].state)
        assertEquals(22, loadedP1.tokens[1].stepCount)

        assertEquals(TokenState.IN_HOME_LANE, loadedP1.tokens[2].state)
        assertEquals(55, loadedP1.tokens[2].stepCount)

        assertEquals(TokenState.FINISHED, loadedP1.tokens[3].state)
        assertEquals(57, loadedP1.tokens[3].stepCount)
    }

    @Test
    fun testRestoringCurrentPlayerIdAndDiceValue() {
        val engine = LudoGameEngine()
        engine.initGame(3)
        val baseState = engine.gameState.value
        val stateWithDice = baseState.copy(
            currentPlayerId = 2,
            diceValue = 4,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION
        )

        assertTrue(persistence.saveState(stateWithDice))
        val restored = persistence.loadState()
        assertNotNull(restored)
        assertEquals(2, restored!!.currentPlayerId)
        assertEquals(4, restored.diceValue)
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION, restored.turnPhase)
    }

    @Test
    fun testRestoringTurnPhaseAndConsecutiveSixCount() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        val stateWithSixes = engine.gameState.value.copy(
            currentPlayerId = 3,
            consecutiveSixCount = 2,
            turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL
        )

        assertTrue(persistence.saveState(stateWithSixes))
        val restored = persistence.loadState()
        assertNotNull(restored)
        assertEquals(3, restored!!.currentPlayerId)
        assertEquals(2, restored.consecutiveSixCount)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, restored.turnPhase)
    }

    @Test
    fun testRestoringWinnersAndGameOverState() {
        val engine = LudoGameEngine()
        engine.initGame(2)
        val gameOverState = engine.gameState.value.copy(
            turnPhase = TurnPhase.GAME_OVER,
            winners = listOf(2, 1)
        )

        assertTrue(persistence.saveState(gameOverState))
        val restored = persistence.loadState()
        assertNotNull(restored)
        assertTrue(restored!!.isGameOver)
        assertEquals(TurnPhase.GAME_OVER, restored.turnPhase)
        assertEquals(listOf(2, 1), restored.winners)
    }

    @Test
    fun testSchemaVersionValidationRejectsMismatch() {
        val engine = LudoGameEngine()
        engine.initGame(2)
        val json = persistence.stateToSnapshotJson(engine.gameState.value)
        json.put("schemaVersion", 999) // Incompatible future schema

        snapshotFile.writeText(json.toString(), Charsets.UTF_8)
        val restored = persistence.loadState()

        // Must reject incompatible schema and delete corrupted/unsupported file
        assertNull(restored)
        assertFalse(persistence.hasSavedState())
    }

    @Test
    fun testCorruptedJsonSnapshotHandledSafely() {
        // Write raw corrupted gibberish
        snapshotFile.writeText("{invalid json truncated 123", Charsets.UTF_8)
        val restored = persistence.loadState()

        // Failure safety: must not throw, must purge corrupted file
        assertNull(restored)
        assertFalse(persistence.hasSavedState())
    }

    @Test
    fun testInvalidStateHandlingStructuralInconsistencies() {
        val engine = LudoGameEngine()
        engine.initGame(2)

        // Case A: Invalid playerCount
        val jsonInvalidPlayerCount = persistence.stateToSnapshotJson(engine.gameState.value).apply {
            put("playerCount", 99)
        }
        assertNull(persistence.snapshotJsonToState(jsonInvalidPlayerCount))

        // Case B: currentPlayerId out of range
        val jsonInvalidCurrentPlayer = persistence.stateToSnapshotJson(engine.gameState.value).apply {
            put("currentPlayerId", 5)
        }
        assertNull(persistence.snapshotJsonToState(jsonInvalidCurrentPlayer))

        // Case C: diceValue out of 1..6
        val jsonInvalidDice = persistence.stateToSnapshotJson(engine.gameState.value).apply {
            put("diceValue", 12)
        }
        assertNull(persistence.snapshotJsonToState(jsonInvalidDice))

        // Case D: invalid turnPhase string
        val jsonInvalidPhase = persistence.stateToSnapshotJson(engine.gameState.value).apply {
            put("turnPhase", "NON_EXISTENT_PHASE")
        }
        assertNull(persistence.snapshotJsonToState(jsonInvalidPhase))
    }

    @Test
    fun testResetAndNewGameClearsPersistedSnapshot() {
        val engine = LudoGameEngine()
        engine.initGame(4)
        assertTrue(persistence.saveState(engine.gameState.value))
        assertTrue(persistence.hasSavedState())

        assertTrue(persistence.clearState())
        assertFalse(persistence.hasSavedState())
        assertNull(persistence.loadState())
    }

    @Test
    fun testEngineRestoreStateRecomputesLegalMoves() {
        val engine = LudoGameEngine()
        engine.initGame(4)

        // Player 1 in base with roll 6: should have legal moves 0..3
        val stateToRestore = engine.gameState.value.copy(
            currentPlayerId = 1,
            diceValue = 6,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = emptySet() // simulate missing or stripped legalTokenIds
        )

        engine.restoreState(stateToRestore)
        val restoredInEngine = engine.gameState.value

        assertEquals(1, restoredInEngine.currentPlayerId)
        assertEquals(6, restoredInEngine.diceValue)
        assertEquals(TurnPhase.WAITING_FOR_TOKEN_SELECTION, restoredInEngine.turnPhase)
        // Legal moves recomputed deterministically by MoveValidator!
        assertEquals(setOf(0, 1, 2, 3), restoredInEngine.legalTokenIds)
    }

    @Test
    fun testRestoringUnstartedSetupMode() {
        val unstartedState = LudoGameState(
            isGameStarted = false,
            playerCount = 4,
            currentPlayerId = 1,
            turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL
        )

        assertTrue(persistence.saveState(unstartedState))
        val restored = persistence.loadState()
        assertNotNull(restored)
        assertFalse(restored!!.isGameStarted)
    }
}
