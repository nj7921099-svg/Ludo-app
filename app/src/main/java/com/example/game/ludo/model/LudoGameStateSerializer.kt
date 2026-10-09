package com.example.game.ludo.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Authoritative JSON serializer and deserializer for [LudoGameState] and its constituent models.
 *
 * Provides a lossless, robust wire format for synchronizing complete board and player state
 * across the network between the Ludo Host and any connected Controller or observer clients.
 */
object LudoGameStateSerializer {

    // =========================================================================
    // LudoToken serialization
    // =========================================================================

    fun tokenToJson(token: LudoToken): JSONObject = JSONObject().apply {
        put("tokenId", token.tokenId)
        put("playerId", token.playerId)
        put("state", token.state.name)
        put("stepCount", token.stepCount)
    }

    fun tokenFromJson(json: JSONObject): LudoToken {
        val tokenId = json.optInt("tokenId", 0).coerceIn(0, 3)
        val playerId = json.optInt("playerId", 1).coerceIn(1, 4)
        val stateStr = json.optString("state", TokenState.IN_BASE.name)
        val state = try {
            TokenState.valueOf(stateStr)
        } catch (_: Exception) {
            TokenState.IN_BASE
        }
        val stepCount = json.optInt("stepCount", 0).coerceIn(0, 57)
        return LudoToken(
            tokenId = tokenId,
            playerId = playerId,
            state = state,
            stepCount = stepCount
        )
    }

    // =========================================================================
    // LudoPlayer serialization
    // =========================================================================

    fun playerToJson(player: LudoPlayer): JSONObject = JSONObject().apply {
        put("playerId", player.playerId)
        put("color", player.color.name)
        put("name", player.name)
        val tokensArray = JSONArray()
        player.tokens.forEach { tokensArray.put(tokenToJson(it)) }
        put("tokens", tokensArray)
        put("isFinished", player.isFinished)
        if (player.finishRank != null) {
            put("finishRank", player.finishRank)
        }
        if (player.teamId != null) {
            put("teamId", player.teamId.name)
        }
    }

    fun playerFromJson(json: JSONObject): LudoPlayer {
        val playerId = json.optInt("playerId", 1).coerceIn(1, 4)
        val colorStr = json.optString("color", LudoColor.RED.name)
        val color = try {
            LudoColor.valueOf(colorStr)
        } catch (_: Exception) {
            when (playerId) {
                1 -> LudoColor.RED
                2 -> LudoColor.GREEN
                3 -> LudoColor.YELLOW
                else -> LudoColor.BLUE
            }
        }
        val name = json.optString("name", "Player $playerId")
        val tokensArray = json.optJSONArray("tokens")
        val tokens = if (tokensArray != null && tokensArray.length() == 4) {
            (0 until 4).map { tokenFromJson(tokensArray.getJSONObject(it)) }
        } else {
            (0..3).map { LudoToken(tokenId = it, playerId = playerId) }
        }
        val isFinished = json.optBoolean("isFinished", false)
        val finishRank = if (json.has("finishRank") && !json.isNull("finishRank")) {
            json.optInt("finishRank")
        } else {
            null
        }
        val teamId = if (json.has("teamId") && !json.isNull("teamId")) {
            try {
                LudoTeamId.valueOf(json.getString("teamId"))
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        return LudoPlayer(
            playerId = playerId,
            color = color,
            name = name,
            tokens = tokens,
            isFinished = isFinished,
            finishRank = finishRank,
            teamId = teamId
        )
    }

    // =========================================================================
    // LudoMoveResult serialization
    // =========================================================================

    fun moveResultToJson(result: LudoMoveResult): JSONObject = JSONObject().apply {
        put("playerId", result.playerId)
        put("tokenId", result.tokenId)
        put("diceValue", result.diceValue)
        put("fromStep", result.fromStep)
        put("toStep", result.toStep)
        val traversedArray = JSONArray()
        result.traversedSteps.forEach { traversedArray.put(it) }
        put("traversedSteps", traversedArray)
        val capturedArray = JSONArray()
        result.capturedTokens.forEach { capturedArray.put(tokenToJson(it)) }
        put("capturedTokens", capturedArray)
        put("enteredHome", result.enteredHome)
        put("finishedToken", result.finishedToken)
        put("extraTurnGranted", result.extraTurnGranted)
        if (result.extraTurnReason != null) {
            put("extraTurnReason", result.extraTurnReason.name)
        }
        put("isGameOver", result.isGameOver)
        put("nextPlayerId", result.nextPlayerId)
    }

    fun moveResultFromJson(json: JSONObject): LudoMoveResult {
        val playerId = json.optInt("playerId", 1)
        val tokenId = json.optInt("tokenId", 0)
        val diceValue = json.optInt("diceValue", 1)
        val fromStep = json.optInt("fromStep", 0)
        val toStep = json.optInt("toStep", 0)
        val traversedSteps = mutableListOf<Int>()
        val stepsArr = json.optJSONArray("traversedSteps")
        if (stepsArr != null) {
            for (i in 0 until stepsArr.length()) {
                traversedSteps.add(stepsArr.getInt(i))
            }
        }
        val capturedTokens = mutableListOf<LudoToken>()
        val capArr = json.optJSONArray("capturedTokens")
        if (capArr != null) {
            for (i in 0 until capArr.length()) {
                capturedTokens.add(tokenFromJson(capArr.getJSONObject(i)))
            }
        }
        val enteredHome = json.optBoolean("enteredHome", false)
        val finishedToken = json.optBoolean("finishedToken", false)
        val extraTurnGranted = json.optBoolean("extraTurnGranted", false)
        val extraTurnReason = if (json.has("extraTurnReason") && !json.isNull("extraTurnReason")) {
            try {
                ExtraTurnReason.valueOf(json.getString("extraTurnReason"))
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        val isGameOver = json.optBoolean("isGameOver", false)
        val nextPlayerId = json.optInt("nextPlayerId", 1)

        return LudoMoveResult(
            playerId = playerId,
            tokenId = tokenId,
            diceValue = diceValue,
            fromStep = fromStep,
            toStep = toStep,
            traversedSteps = traversedSteps,
            capturedTokens = capturedTokens,
            enteredHome = enteredHome,
            finishedToken = finishedToken,
            extraTurnGranted = extraTurnGranted,
            extraTurnReason = extraTurnReason,
            isGameOver = isGameOver,
            nextPlayerId = nextPlayerId
        )
    }

    // =========================================================================
    // LudoGameState serialization
    // =========================================================================

    fun stateToJson(state: LudoGameState): JSONObject = JSONObject().apply {
        put("isGameStarted", state.isGameStarted)
        put("playerCount", state.playerCount)
        put("currentPlayerId", state.currentPlayerId)
        if (state.diceValue != null) {
            put("diceValue", state.diceValue)
        }
        put("turnPhase", state.turnPhase.name)
        val legalArray = JSONArray()
        state.legalTokenIds.forEach { legalArray.put(it) }
        put("legalTokenIds", legalArray)
        put("consecutiveSixCount", state.consecutiveSixCount)
        val winnersArray = JSONArray()
        state.winners.forEach { winnersArray.put(it) }
        put("winners", winnersArray)
        val playersArray = JSONArray()
        state.players.forEach { playersArray.put(playerToJson(it)) }
        put("players", playersArray)
        if (state.lastMoveResult != null) {
            put("lastMoveResult", moveResultToJson(state.lastMoveResult))
        }
        put("gameMode", state.gameMode.name)
        if (state.winningTeamId != null) {
            put("winningTeamId", state.winningTeamId.name)
        }
    }

    fun stateFromJson(json: JSONObject): LudoGameState {
        val isGameStarted = json.optBoolean("isGameStarted", false)
        val playerCount = json.optInt("playerCount", 4)
        val currentPlayerId = json.optInt("currentPlayerId", 1)
        val diceValue = if (json.has("diceValue") && !json.isNull("diceValue")) {
            json.optInt("diceValue")
        } else {
            null
        }
        val turnPhaseStr = json.optString("turnPhase", TurnPhase.WAITING_FOR_DICE_ROLL.name)
        val turnPhase = try {
            TurnPhase.valueOf(turnPhaseStr)
        } catch (_: Exception) {
            TurnPhase.WAITING_FOR_DICE_ROLL
        }
        val legalTokenIds = mutableSetOf<Int>()
        val legalArr = json.optJSONArray("legalTokenIds")
        if (legalArr != null) {
            for (i in 0 until legalArr.length()) {
                legalTokenIds.add(legalArr.getInt(i))
            }
        }
        val consecutiveSixCount = json.optInt("consecutiveSixCount", 0)
        val winners = mutableListOf<Int>()
        val winArr = json.optJSONArray("winners")
        if (winArr != null) {
            for (i in 0 until winArr.length()) {
                winners.add(winArr.getInt(i))
            }
        }
        val players = mutableListOf<LudoPlayer>()
        val playersArr = json.optJSONArray("players")
        if (playersArr != null) {
            for (i in 0 until playersArr.length()) {
                players.add(playerFromJson(playersArr.getJSONObject(i)))
            }
        }
        val lastMoveResult = if (json.has("lastMoveResult") && !json.isNull("lastMoveResult")) {
            moveResultFromJson(json.getJSONObject("lastMoveResult"))
        } else {
            null
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

        val winningTeamId = if (json.has("winningTeamId") && !json.isNull("winningTeamId")) {
            try {
                LudoTeamId.valueOf(json.getString("winningTeamId"))
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        return LudoGameState(
            isGameStarted = isGameStarted,
            playerCount = playerCount,
            players = players,
            currentPlayerId = currentPlayerId,
            diceValue = diceValue,
            turnPhase = turnPhase,
            legalTokenIds = legalTokenIds,
            consecutiveSixCount = consecutiveSixCount,
            winners = winners,
            lastMoveResult = lastMoveResult,
            gameMode = gameMode,
            winningTeamId = winningTeamId
        )
    }
}

/**
 * Convenience extension to serialize a [LudoGameState] to [JSONObject].
 */
fun LudoGameState.toJsonObject(): JSONObject = LudoGameStateSerializer.stateToJson(this)

/**
 * Convenience extension to serialize a [LudoGameState] to JSON string.
 */
fun LudoGameState.toJsonString(): String = toJsonObject().toString()
