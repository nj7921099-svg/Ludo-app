package com.example.game.ludo.stats.persistence

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.stats.model.LudoStatisticsData
import com.example.game.ludo.stats.model.MatchHistoryRecord
import com.example.game.ludo.stats.model.PlayerMatchContribution
import com.example.game.ludo.stats.model.PlayerStatistics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Crash-safe, atomic persistence manager for [LudoStatisticsData] using [AtomicFile].
 * Stores statistics and match history completely independent of the live game snapshot.
 */
class LudoStatisticsPersistence {

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_STATISTICS_FILENAME = "ludo_statistics.json"
        private const val TAG = "LudoStatsPersistence"
    }

    private val statisticsFile: File

    constructor(context: Context, fileName: String = DEFAULT_STATISTICS_FILENAME) {
        this.statisticsFile = File(context.filesDir, fileName)
    }

    constructor(file: File) {
        this.statisticsFile = file
    }

    @Synchronized
    fun saveStatistics(data: LudoStatisticsData): Boolean {
        val atomicFile = AtomicFile(statisticsFile)
        var stream: FileOutputStream? = null
        return try {
            val jsonObject = dataToJson(data)
            val jsonBytes = jsonObject.toString(2).toByteArray(StandardCharsets.UTF_8)

            stream = atomicFile.startWrite()
            stream.write(jsonBytes)
            stream.flush()
            atomicFile.finishWrite(stream)
            Log.d(TAG, "Successfully persisted Ludo statistics (${data.matchHistory.size} matches)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing Ludo statistics", e)
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
            false
        }
    }

    @Synchronized
    fun loadStatistics(): LudoStatisticsData {
        if (!statisticsFile.exists()) {
            return LudoStatisticsData()
        }

        val atomicFile = AtomicFile(statisticsFile)
        return try {
            val bytes = atomicFile.readFully()
            val jsonString = String(bytes, StandardCharsets.UTF_8)
            val json = JSONObject(jsonString)
            jsonToData(json) ?: LudoStatisticsData()
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading or parsing Ludo statistics; recovering with default empty stats.", e)
            LudoStatisticsData()
        }
    }

    @Synchronized
    fun clearStatistics(): Boolean {
        return try {
            val atomicFile = AtomicFile(statisticsFile)
            atomicFile.delete()
            if (statisticsFile.exists()) {
                statisticsFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed clearing statistics file", e)
            false
        }
    }

    private fun dataToJson(data: LudoStatisticsData): JSONObject = JSONObject().apply {
        put("schemaVersion", data.schemaVersion)

        val playerStatsObj = JSONObject()
        data.playerStats.forEach { (playerId, stats) ->
            playerStatsObj.put(playerId.toString(), JSONObject().apply {
                put("playerId", stats.playerId)
                put("gamesPlayed", stats.gamesPlayed)
                put("gamesWon", stats.gamesWon)
                put("tokensFinished", stats.tokensFinished)
                put("tokensCaptured", stats.tokensCaptured)
                put("timesCaptured", stats.timesCaptured)
                put("sixesRolled", stats.sixesRolled)
                put("extraTurnsGranted", stats.extraTurnsGranted)
                put("totalMoves", stats.totalMoves)
                put("teamUpMatchesPlayed", stats.teamUpMatchesPlayed)
                put("teamUpMatchesWon", stats.teamUpMatchesWon)
            })
        }
        put("playerStats", playerStatsObj)

        val historyArray = JSONArray()
        data.matchHistory.forEach { match ->
            historyArray.put(JSONObject().apply {
                put("matchId", match.matchId)
                put("timestamp", match.timestamp)
                put("durationSeconds", match.durationSeconds)
                put("gameMode", match.gameMode.name)
                put("playerCount", match.playerCount)
                if (match.winningPlayerId != null) put("winningPlayerId", match.winningPlayerId)
                if (match.winningTeamId != null) put("winningTeamId", match.winningTeamId.name)

                val playersArray = JSONArray()
                match.players.forEach { p ->
                    playersArray.put(JSONObject().apply {
                        put("playerId", p.playerId)
                        put("color", p.color.name)
                        if (p.teamId != null) put("teamId", p.teamId.name)
                        if (p.finishRank != null) put("finishRank", p.finishRank)
                        put("isWinner", p.isWinner)
                        put("tokensFinished", p.tokensFinished)
                        put("tokensCaptured", p.tokensCaptured)
                        put("timesCaptured", p.timesCaptured)
                        put("sixesRolled", p.sixesRolled)
                        put("extraTurns", p.extraTurns)
                        put("movesCount", p.movesCount)
                    })
                }
                put("players", playersArray)
            })
        }
        put("matchHistory", historyArray)
    }

    private fun jsonToData(json: JSONObject): LudoStatisticsData? {
        val schemaVersion = json.optInt("schemaVersion", -1)
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            return null
        }

        val playerStats = mutableMapOf<Int, PlayerStatistics>()
        val statsObj = json.optJSONObject("playerStats")
        if (statsObj != null) {
            val keys = statsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val pObj = statsObj.optJSONObject(key)
                if (pObj != null) {
                    val pId = pObj.optInt("playerId", key.toIntOrNull() ?: 0)
                    if (pId in 1..4) {
                        playerStats[pId] = PlayerStatistics(
                            playerId = pId,
                            gamesPlayed = pObj.optInt("gamesPlayed", 0),
                            gamesWon = pObj.optInt("gamesWon", 0),
                            tokensFinished = pObj.optInt("tokensFinished", 0),
                            tokensCaptured = pObj.optInt("tokensCaptured", 0),
                            timesCaptured = pObj.optInt("timesCaptured", 0),
                            sixesRolled = pObj.optInt("sixesRolled", 0),
                            extraTurnsGranted = pObj.optInt("extraTurnsGranted", 0),
                            totalMoves = pObj.optInt("totalMoves", 0),
                            teamUpMatchesPlayed = pObj.optInt("teamUpMatchesPlayed", 0),
                            teamUpMatchesWon = pObj.optInt("teamUpMatchesWon", 0)
                        )
                    }
                }
            }
        }

        val matchHistory = mutableListOf<MatchHistoryRecord>()
        val historyArray = json.optJSONArray("matchHistory")
        if (historyArray != null) {
            for (i in 0 until historyArray.length()) {
                val mObj = historyArray.optJSONObject(i) ?: continue
                val matchId = mObj.optString("matchId", "")
                if (matchId.isBlank()) continue

                val timestamp = mObj.optLong("timestamp", 0L)
                val durationSeconds = mObj.optLong("durationSeconds", 0L)
                val gameMode = try {
                    LudoGameMode.valueOf(mObj.optString("gameMode", LudoGameMode.INDIVIDUAL.name))
                } catch (_: Exception) {
                    LudoGameMode.INDIVIDUAL
                }
                val playerCount = mObj.optInt("playerCount", 4)
                val winningPlayerId = if (mObj.has("winningPlayerId")) mObj.optInt("winningPlayerId") else null
                val winningTeamId = if (mObj.has("winningTeamId")) {
                    try {
                        LudoTeamId.valueOf(mObj.optString("winningTeamId"))
                    } catch (_: Exception) {
                        null
                    }
                } else null

                val players = mutableListOf<PlayerMatchContribution>()
                val playersArr = mObj.optJSONArray("players")
                if (playersArr != null) {
                    for (j in 0 until playersArr.length()) {
                        val pObj = playersArr.optJSONObject(j) ?: continue
                        val pId = pObj.optInt("playerId", 0)
                        val color = try {
                            LudoColor.valueOf(pObj.optString("color", LudoColor.RED.name))
                        } catch (_: Exception) {
                            LudoColor.RED
                        }
                        val teamId = if (pObj.has("teamId")) {
                            try {
                                LudoTeamId.valueOf(pObj.optString("teamId"))
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                        val finishRank = if (pObj.has("finishRank")) pObj.optInt("finishRank") else null
                        val isWinner = pObj.optBoolean("isWinner", false)

                        players.add(
                            PlayerMatchContribution(
                                playerId = pId,
                                color = color,
                                teamId = teamId,
                                finishRank = finishRank,
                                isWinner = isWinner,
                                tokensFinished = pObj.optInt("tokensFinished", 0),
                                tokensCaptured = pObj.optInt("tokensCaptured", 0),
                                timesCaptured = pObj.optInt("timesCaptured", 0),
                                sixesRolled = pObj.optInt("sixesRolled", 0),
                                extraTurns = pObj.optInt("extraTurns", 0),
                                movesCount = pObj.optInt("movesCount", 0)
                            )
                        )
                    }
                }

                matchHistory.add(
                    MatchHistoryRecord(
                        matchId = matchId,
                        timestamp = timestamp,
                        durationSeconds = durationSeconds,
                        gameMode = gameMode,
                        playerCount = playerCount,
                        winningPlayerId = winningPlayerId,
                        winningTeamId = winningTeamId,
                        players = players
                    )
                )
            }
        }

        return LudoStatisticsData(
            schemaVersion = schemaVersion,
            playerStats = playerStats,
            matchHistory = matchHistory
        )
    }
}
