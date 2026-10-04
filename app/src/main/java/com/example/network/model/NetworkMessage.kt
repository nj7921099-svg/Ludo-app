package com.example.network.model

import com.example.network.NetworkConstants
import org.json.JSONObject
import java.util.UUID

/**
 * Clean versioned JSON wire protocol for Ludo-app Local Network Communication.
 *
 * All messages end with a newline character (\n) for robust line framing.
 */
sealed class NetworkMessage {
    abstract val type: String
    abstract val timestamp: Long
    abstract val requestId: String

    fun toJsonString(): String = toJsonObject().toString()

    abstract fun toJsonObject(): JSONObject

    companion object {
        const val TYPE_HANDSHAKE = "HANDSHAKE"
        const val TYPE_HANDSHAKE_ACK = "HANDSHAKE_ACK"
        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"
        const val TYPE_ACK = "ACK"
        const val TYPE_ERROR = "ERROR"
        const val TYPE_GET_CONFIG = "GET_CONFIG"
        const val TYPE_CONFIG = "CONFIG"
        const val TYPE_STATE_SYNC = "STATE_SYNC"
        const val TYPE_NUMBER_SELECTION = "NUMBER_SELECTION"
        const val TYPE_NUMBER_RESULT = "NUMBER_RESULT"
        const val TYPE_GAME_EVENT = "GAME_EVENT"
        const val TYPE_DISCONNECT = "DISCONNECT"
        const val TYPE_RECONNECT = "RECONNECT"

        // Compatibility aliases for Ludo game turn updates & commands
        const val TYPE_CONTROLLER_COMMAND = "CONTROLLER_COMMAND"
        const val TYPE_TURN_UPDATE = "TURN_UPDATE"
        const val TYPE_RESET_GAME = "RESET_GAME"

        private fun parseBoxId(json: JSONObject): Int? {
            if (!json.has("boxId")) return null
            val raw = json.opt("boxId")
            if (raw is Number) return raw.toInt()
            val str = raw?.toString()?.trim() ?: ""
            val digits = str.removePrefix("R").removePrefix("r").trim()
            return digits.toIntOrNull()
        }

        private fun parseRequestId(json: JSONObject): String {
            return when {
                json.has("requestId") && json.optString("requestId").isNotBlank() -> json.optString("requestId")
                json.has("commandId") && json.optString("commandId").isNotBlank() -> json.optString("commandId")
                else -> UUID.randomUUID().toString()
            }
        }

        fun fromJson(jsonStr: String): NetworkMessage? {
            return try {
                val json = JSONObject(jsonStr.trim())
                val type = json.optString("type")
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                val requestId = parseRequestId(json)

                when (type) {
                    TYPE_HANDSHAKE -> HandshakeMsg(
                        protocolVersion = json.optInt("protocolVersion", NetworkConstants.PROTOCOL_VERSION),
                        role = json.optString("role", NetworkConstants.ROLE_CONTROLLER),
                        deviceName = json.optString("deviceName", "Controller"),
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_HANDSHAKE_ACK -> HandshakeAckMsg(
                        protocolVersion = json.optInt("protocolVersion", NetworkConstants.PROTOCOL_VERSION),
                        role = json.optString("role", NetworkConstants.ROLE_HOST),
                        status = json.optString("status", "OK"),
                        deviceName = json.optString("deviceName", "Ludo Host"),
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_PING -> PingMsg(
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_PONG -> PongMsg(
                        requestId = requestId,
                        originalTimestamp = json.optLong("originalTimestamp", timestamp),
                        timestamp = timestamp
                    )

                    TYPE_ACK -> AckMsg(
                        requestId = requestId,
                        status = json.optString("status", "OK"),
                        reason = json.optString("reason", ""),
                        timestamp = timestamp
                    )

                    TYPE_ERROR -> ErrorMsg(
                        code = json.optString("code", "UNKNOWN_ERROR"),
                        message = json.optString("message", "An error occurred"),
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_GET_CONFIG -> GetConfigMsg(
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_CONFIG -> ConfigMsg(
                        boxCount = json.optInt("boxCount", NetworkConstants.DEFAULT_BOX_COUNT),
                        turnId = json.optLong("turnId", 1L),
                        activeBoxId = json.optInt("activeBoxId", 1),
                        isGameStarted = json.optBoolean("isGameStarted", false),
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_STATE_SYNC -> StateSyncMsg(
                        boxCount = json.optInt("boxCount", NetworkConstants.DEFAULT_BOX_COUNT),
                        turnId = json.optLong("turnId", 1L),
                        activeBoxId = json.optInt("activeBoxId", 1),
                        isGameStarted = json.optBoolean("isGameStarted", false),
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_NUMBER_SELECTION, TYPE_CONTROLLER_COMMAND -> NumberSelectionMsg(
                        value = json.optInt("value", 1),
                        boxId = parseBoxId(json),
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_NUMBER_RESULT -> NumberResultMsg(
                        boxId = json.optInt("boxId", 1),
                        turnId = json.optLong("turnId", 1L),
                        value = json.optInt("value", 1),
                        source = json.optString("source", "LOCAL"),
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_GAME_EVENT, TYPE_TURN_UPDATE -> GameEventMsg(
                        event = json.optString("event", "TURN_UPDATE"),
                        turnId = json.optLong("turnId", 1L),
                        activeBoxId = json.optInt("activeBoxId", 1),
                        previousBoxId = json.optInt("previousBoxId", 0),
                        previousResult = if (json.has("previousResult")) json.optInt("previousResult") else null,
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_RESET_GAME -> GameEventMsg(
                        event = "RESET_GAME",
                        turnId = 1L,
                        activeBoxId = 1,
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_DISCONNECT -> DisconnectMsg(
                        reason = json.optString("reason", "User disconnected"),
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    TYPE_RECONNECT -> ReconnectMsg(
                        requestId = requestId,
                        timestamp = timestamp
                    )

                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class HandshakeMsg(
    val protocolVersion: Int = NetworkConstants.PROTOCOL_VERSION,
    val role: String = NetworkConstants.ROLE_CONTROLLER,
    val deviceName: String = "Controller",
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_HANDSHAKE

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("protocolVersion", protocolVersion)
        put("role", role)
        put("deviceName", deviceName)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class HandshakeAckMsg(
    val protocolVersion: Int = NetworkConstants.PROTOCOL_VERSION,
    val role: String = NetworkConstants.ROLE_HOST,
    val status: String = "OK",
    val deviceName: String = "Ludo Host",
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_HANDSHAKE_ACK

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("protocolVersion", protocolVersion)
        put("role", role)
        put("status", status)
        put("deviceName", deviceName)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class PingMsg(
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_PING

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class PongMsg(
    override val requestId: String = UUID.randomUUID().toString(),
    val originalTimestamp: Long = System.currentTimeMillis(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_PONG

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("requestId", requestId)
        put("originalTimestamp", originalTimestamp)
        put("timestamp", timestamp)
    }
}

data class AckMsg(
    override val requestId: String,
    val status: String = "OK",
    val reason: String = "",
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_ACK

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("requestId", requestId)
        put("status", status)
        if (reason.isNotBlank()) put("reason", reason)
        put("timestamp", timestamp)
    }
}

data class ErrorMsg(
    val code: String,
    val message: String,
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_ERROR

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("code", code)
        put("message", message)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class GetConfigMsg(
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_GET_CONFIG

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class ConfigMsg(
    val boxCount: Int,
    val turnId: Long,
    val activeBoxId: Int,
    val isGameStarted: Boolean,
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_CONFIG

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxCount", boxCount)
        put("turnId", turnId)
        put("activeBoxId", activeBoxId)
        put("isGameStarted", isGameStarted)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class StateSyncMsg(
    val boxCount: Int,
    val turnId: Long,
    val activeBoxId: Int,
    val isGameStarted: Boolean,
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_STATE_SYNC

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxCount", boxCount)
        put("turnId", turnId)
        put("activeBoxId", activeBoxId)
        put("isGameStarted", isGameStarted)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class NumberSelectionMsg(
    val value: Int,
    val boxId: Int? = null,
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_NUMBER_SELECTION

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("value", value)
        if (boxId != null) put("boxId", boxId)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class NumberResultMsg(
    val boxId: Int,
    val turnId: Long,
    val value: Int,
    val source: String = "LOCAL",
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_NUMBER_RESULT

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxId", boxId)
        put("turnId", turnId)
        put("value", value)
        put("source", source)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class GameEventMsg(
    val event: String,
    val turnId: Long,
    val activeBoxId: Int,
    val previousBoxId: Int = 0,
    val previousResult: Int? = null,
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_GAME_EVENT

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("event", event)
        put("turnId", turnId)
        put("activeBoxId", activeBoxId)
        put("previousBoxId", previousBoxId)
        if (previousResult != null) put("previousResult", previousResult)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class DisconnectMsg(
    val reason: String = "User disconnected",
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_DISCONNECT

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("reason", reason)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class ReconnectMsg(
    override val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : NetworkMessage() {
    override val type: String = TYPE_RECONNECT

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}
