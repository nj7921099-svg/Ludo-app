package com.example.game.ludo.persistence

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.example.game.ludo.engine.MoveValidator
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoGameStateSerializer
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.model.TurnPhase
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Robust, production-grade persistence manager for Ludo game state snapshots.
 *
 * Characteristics:
 * - Uses [AtomicFile] for crash-safe, atomic disk writes preventing file corruption.
 * - Stores state in internal app private storage (`context.filesDir`) or a direct [File] for tests.
 * - Encodes strictly authoritative state into JSON with strict schema versioning.
 * - On startup/load, validates data consistency; safely discards invalid/corrupted snapshots.
 */
class LudoStatePersistence {

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_SNAPSHOT_FILENAME = "ludo_game_snapshot.json"
        private const val TAG = "LudoStatePersistence"
    }

    private val snapshotFile: File

    /**
     * Context-based constructor used by application/ViewModel.
     */
    constructor(context: Context, fileName: String = DEFAULT_SNAPSHOT_FILENAME) {
        this.snapshotFile = File(context.filesDir, fileName)
    }

    /**
     * File-based constructor used by unit/Robolectric tests.
     */
    constructor(file: File) {
        this.snapshotFile = file
    }

    /**
     * Atomically saves the authoritative fields of [gameState] to internal storage.
     *
     * @param gameState Current authoritative state.
     * @return true if write succeeded, false otherwise.
     */
    @Synchronized
    fun saveState(gameState: LudoGameState): Boolean {
        val atomicFile = AtomicFile(snapshotFile)
        var stream: FileOutputStream? = null
        return try {
            val jsonObject = stateToSnapshotJson(gameState)
            val jsonBytes = jsonObject.toString(2).toByteArray(StandardCharsets.UTF_8)

            stream = atomicFile.startWrite()
            stream.write(jsonBytes)
            stream.flush()
            atomicFile.finishWrite(stream)
            Log.d(TAG, "Successfully persisted game snapshot (playerCount=${gameState.playerCount}, turnPhase=${gameState.turnPhase})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing game snapshot", e)
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
            false
        }
    }

    /**
     * Atomically loads and validates a saved game state snapshot from disk.
     * If the file does not exist, or validation fails, returns null.
     * Discards any malformed or incompatible state to prevent inconsistent game sessions.
     *
     * @return Validated [LudoGameState] or null.
     */
    @Synchronized
    fun loadState(): LudoGameState? {
        if (!snapshotFile.exists()) {
            return null
        }

        val atomicFile = AtomicFile(snapshotFile)
        return try {
            val bytes = atomicFile.readFully()
            val jsonString = String(bytes, StandardCharsets.UTF_8)
            val json = JSONObject(jsonString)

            val restoredState = snapshotJsonToState(json)
            if (restoredState == null) {
                Log.w(TAG, "Snapshot validation failed; purging corrupted snapshot.")
                clearState()
            }
            restoredState
        } catch (e: Exception) {
            Log.e(TAG, "Error reading or parsing state snapshot; clearing corrupted file.", e)
            clearState()
            null
        }
    }

    /**
     * Safely deletes any existing snapshot file.
     *
     * @return true if file does not exist or was successfully removed.
     */
    @Synchronized
    fun clearState(): Boolean {
        return try {
            val atomicFile = AtomicFile(snapshotFile)
            atomicFile.delete()
            if (snapshotFile.exists()) {
                snapshotFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed clearing snapshot file", e)
            false
        }
    }

    /**
     * Checks if a saved snapshot currently exists on disk.
     */
    fun hasSavedState(): Boolean = snapshotFile.exists()

    /**
     * Serializes authoritative fields of [LudoGameState] into a JSON representation
     * conforming to [CURRENT_SCHEMA_VERSION].
     */
    fun stateToSnapshotJson(state: LudoGameState): JSONObject = JSONObject().apply {
        put("schemaVersion", CURRENT_SCHEMA_VERSION)
        put("gameStarted", state.isGameStarted)
        put("isGameStarted", state.isGameStarted)
        put("playerCount", state.playerCount)
        put("currentPlayerId", state.currentPlayerId)
        if (state.diceValue != null) {
            put("diceValue", state.diceValue)
        }
        put("turnPhase", state.turnPhase.name)
        put("consecutiveSixCount", state.consecutiveSixCount)

        val winnersArray = JSONArray()
        state.winners.forEach { winnersArray.put(it) }
        put("winners", winnersArray)

        val playersArray = JSONArray()
        state.players.forEach { playersArray.put(LudoGameStateSerializer.playerToJson(it)) }
        put("players", playersArray)

        put("gameMode", state.gameMode.name)
        if (state.winningTeamId != null) {
            put("winningTeamId", state.winningTeamId.name)
        }
    }

    /**
     * Parses and strictly validates a snapshot [JSONObject].
     *
     * Returns null if:
     * - schemaVersion != CURRENT_SCHEMA_VERSION
     * - playerCount !in 2..4
     * - currentPlayerId !in 1..playerCount
     * - diceValue !in 1..6 (when non-null)
     * - turnPhase is invalid
     * - players count does not match playerCount (when gameStarted == true)
     * - any token is malformed or out of bounds (stepCount !in 0..57)
     * - TEAM_UP mode requested but playerCount != 4
     */
    fun snapshotJsonToState(json: JSONObject): LudoGameState? {
        val schemaVersion = json.optInt("schemaVersion", -1)
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            return null
        }

        val gameStarted = if (json.has("gameStarted")) {
            json.optBoolean("gameStarted")
        } else {
            json.optBoolean("isGameStarted", false)
        }

        val playerCount = json.optInt("playerCount", 0)
        if (playerCount !in 2..4) return null

        val currentPlayerId = json.optInt("currentPlayerId", 0)
        if (currentPlayerId !in 1..playerCount) return null

        val diceValue = if (json.has("diceValue") && !json.isNull("diceValue")) {
            val dv = json.optInt("diceValue")
            if (dv in 1..6) dv else return null
        } else {
            null
        }

        val turnPhaseStr = json.optString("turnPhase", "")
        val turnPhase = try {
            TurnPhase.valueOf(turnPhaseStr)
        } catch (_: Exception) {
            return null
        }

        val consecutiveSixCount = json.optInt("consecutiveSixCount", 0)
        if (consecutiveSixCount !in 0..3) return null

        val winners = mutableListOf<Int>()
        val winArr = json.optJSONArray("winners")
        if (winArr != null) {
            for (i in 0 until winArr.length()) {
                val winnerId = winArr.getInt(i)
                if (winnerId !in 1..playerCount) return null
                winners.add(winnerId)
            }
        }

        val gameMode = if (json.has("gameMode")) {
            try {
                LudoGameMode.valueOf(json.getString("gameMode"))
            } catch (_: Exception) {
                LudoGameMode.INDIVIDUAL
            }
        } else {
            LudoGameMode.INDIVIDUAL
        }

        if (gameMode == LudoGameMode.TEAM_UP && playerCount != 4) {
            return null
        }

        val winningTeamId = if (json.has("winningTeamId") && !json.isNull("winningTeamId")) {
            try {
                LudoTeamId.valueOf(json.getString("winningTeamId"))
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val players = mutableListOf<LudoPlayer>()
        if (gameStarted) {
            val playersArr = json.optJSONArray("players") ?: return null
            if (playersArr.length() != playerCount) return null

            for (i in 0 until playersArr.length()) {
                val playerObj = playersArr.optJSONObject(i) ?: return null
                val player = LudoGameStateSerializer.playerFromJson(playerObj)
                if (player.playerId !in 1..playerCount) return null
                if (player.tokens.size != 4) return null
                for (token in player.tokens) {
                    if (token.playerId != player.playerId) return null
                    if (token.stepCount !in 0..57) return null
                }
                players.add(player)
            }
        }

        // Build temporary state for legalTokens calculation
        val tempState = LudoGameState(
            isGameStarted = gameStarted,
            playerCount = playerCount,
            players = players,
            currentPlayerId = currentPlayerId,
            diceValue = diceValue,
            turnPhase = turnPhase,
            gameMode = gameMode,
            winningTeamId = winningTeamId
        )

        // Recompute legalTokenIds deterministically if in WAITING_FOR_TOKEN_SELECTION with active dice
        val legalTokens = if (turnPhase == TurnPhase.WAITING_FOR_TOKEN_SELECTION && diceValue != null) {
            MoveValidator.calculateLegalTokens(tempState, diceValue)
        } else {
            emptySet()
        }

        return LudoGameState(
            isGameStarted = gameStarted,
            playerCount = playerCount,
            players = players,
            currentPlayerId = currentPlayerId,
            diceValue = diceValue,
            turnPhase = turnPhase,
            legalTokenIds = legalTokens,
            consecutiveSixCount = consecutiveSixCount,
            winners = winners,
            lastMoveResult = null,
            gameMode = gameMode,
            winningTeamId = winningTeamId
        )
    }
}
