package com.example.bluetooth.model

import com.example.bluetooth.BluetoothConstants
import org.json.JSONObject
import java.util.UUID

/**
 * Base sealed class for all structured Bluetooth protocol messages.
 * Every message serializes to and parses from a JSON object.
 */
sealed class BluetoothMessage {
    abstract val type: String
    abstract val timestamp: Long

    fun toJsonString(): String = toJsonObject().toString()

    abstract fun toJsonObject(): JSONObject

    companion object {
        const val TYPE_HANDSHAKE = "HANDSHAKE"
        const val TYPE_HANDSHAKE_ACK = "HANDSHAKE_ACK"
        const val TYPE_GAME_CONFIGURATION = "GAME_CONFIGURATION"
        const val TYPE_TURN_UPDATE = "TURN_UPDATE"
        const val TYPE_CONTROLLER_COMMAND = "CONTROLLER_COMMAND"
        const val TYPE_COMMAND_ACK = "COMMAND_ACK"
        const val TYPE_PROVIDE_NUMBER = "PROVIDE_NUMBER"
        const val TYPE_PROVIDE_NUMBER_ACK = "PROVIDE_NUMBER_ACK"
        const val TYPE_NUMBER_RESULT = "NUMBER_RESULT"
        const val TYPE_RESET_GAME = "RESET_GAME"

        // Legacy / fallback types for compatibility
        const val TYPE_CONFIG_UPDATE = "CONFIG_UPDATE"
        const val TYPE_REQUEST_RANDOM = "REQUEST_RANDOM"
        const val TYPE_RANDOM_RESULT = "RANDOM_RESULT"
        const val TYPE_RESET = "RESET"
        const val TYPE_ERROR = "ERROR"
        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"

        private fun parseBoxId(json: JSONObject): Int {
            if (json.has("boxId")) {
                val raw = json.opt("boxId")
                if (raw is Number) return raw.toInt()
                val str = raw?.toString()?.trim() ?: ""
                val digits = str.removePrefix("R").removePrefix("r").trim()
                return digits.toIntOrNull() ?: 1
            }
            return 1
        }

        private fun parseCommandId(json: JSONObject): String {
            return when {
                json.has("commandId") && json.optString("commandId").isNotBlank() -> json.optString("commandId")
                json.has("requestId") && json.optString("requestId").isNotBlank() -> json.optString("requestId")
                else -> UUID.randomUUID().toString()
            }
        }

        fun fromJson(jsonStr: String): BluetoothMessage? {
            return try {
                val json = JSONObject(jsonStr.trim())
                val type = json.optString("type")
                val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                when (type) {
                    TYPE_HANDSHAKE -> HandshakeMessage(
                        senderApp = json.optString("senderApp", BluetoothConstants.SENDER_APP_CONTROLLER),
                        version = json.optInt("version", BluetoothConstants.PROTOCOL_VERSION),
                        deviceName = json.optString("deviceName", "Unknown"),
                        boxCount = json.optInt("boxCount", BluetoothConstants.DEFAULT_BOX_COUNT),
                        isGameStarted = json.optBoolean("isGameStarted", false),
                        turnId = json.optLong("turnId", 1L),
                        activeBoxId = parseBoxId(json),
                        timestamp = timestamp
                    )

                    TYPE_HANDSHAKE_ACK -> HandshakeAckMessage(
                        senderApp = json.optString("senderApp", BluetoothConstants.SENDER_APP_CONTROLLER),
                        version = json.optInt("version", BluetoothConstants.PROTOCOL_VERSION),
                        deviceName = json.optString("deviceName", "Unknown"),
                        timestamp = timestamp
                    )

                    TYPE_GAME_CONFIGURATION -> GameConfigurationMessage(
                        boxCount = json.optInt("boxCount", BluetoothConstants.DEFAULT_BOX_COUNT),
                        turnId = json.optLong("turnId", 1L),
                        activeBoxId = parseBoxId(json),
                        isGameStarted = json.optBoolean("isGameStarted", true),
                        requestId = parseCommandId(json),
                        timestamp = timestamp
                    )

                    TYPE_TURN_UPDATE -> TurnUpdateMessage(
                        turnId = json.optLong("turnId", 1L),
                        activeBoxId = parseBoxId(json),
                        previousBoxId = json.optInt("previousBoxId", 0),
                        previousResult = if (json.has("previousResult")) json.optInt("previousResult") else null,
                        timestamp = timestamp
                    )

                    TYPE_CONTROLLER_COMMAND, TYPE_PROVIDE_NUMBER -> ControllerCommandMessage(
                        commandId = parseCommandId(json),
                        boxId = parseBoxId(json),
                        value = json.optInt("value", 1),
                        turnId = if (json.has("turnId")) json.optLong("turnId") else null,
                        timestamp = timestamp
                    )

                    TYPE_COMMAND_ACK, TYPE_PROVIDE_NUMBER_ACK -> CommandAckMessage(
                        commandId = parseCommandId(json),
                        boxId = parseBoxId(json),
                        accepted = json.optBoolean("accepted", true),
                        reason = json.optString("reason", ""),
                        timestamp = timestamp
                    )

                    TYPE_NUMBER_RESULT -> NumberResultMessage(
                        boxId = parseBoxId(json),
                        turnId = json.optLong("turnId", 1L),
                        value = json.optInt("value", 1),
                        source = json.optString("source", "LOCAL"),
                        requestId = parseCommandId(json),
                        commandId = if (json.has("commandId")) json.optString("commandId") else null,
                        timestamp = timestamp
                    )

                    TYPE_RESET_GAME -> ResetGameMessage(
                        boxCount = json.optInt("boxCount", BluetoothConstants.DEFAULT_BOX_COUNT),
                        timestamp = timestamp
                    )

                    // Legacy mappings
                    TYPE_CONFIG_UPDATE -> ConfigUpdateMessage(
                        boxCount = json.optInt("boxCount", BluetoothConstants.DEFAULT_BOX_COUNT),
                        requestId = parseCommandId(json),
                        timestamp = timestamp
                    )

                    TYPE_REQUEST_RANDOM -> RequestRandomMessage(
                        boxId = parseBoxId(json),
                        requestId = parseCommandId(json),
                        timestamp = timestamp
                    )

                    TYPE_RANDOM_RESULT -> RandomResultMessage(
                        boxId = parseBoxId(json),
                        value = json.optInt("value", 1),
                        source = json.optString("source", "LOCAL"),
                        requestId = parseCommandId(json),
                        timestamp = timestamp
                    )

                    TYPE_RESET -> ResetMessage(
                        boxCount = json.optInt("boxCount", BluetoothConstants.DEFAULT_BOX_COUNT),
                        requestId = parseCommandId(json),
                        timestamp = timestamp
                    )

                    TYPE_ERROR -> ErrorMessage(
                        code = json.optString("code", "UNKNOWN_ERROR"),
                        message = json.optString("message", "An error occurred"),
                        requestId = parseCommandId(json),
                        timestamp = timestamp
                    )

                    TYPE_PING -> PingMessage(timestamp = timestamp)
                    TYPE_PONG -> PongMessage(timestamp = timestamp)

                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Handshake sent when connection is established.
 */
data class HandshakeMessage(
    val senderApp: String = BluetoothConstants.SENDER_APP_HOST,
    val version: Int = BluetoothConstants.PROTOCOL_VERSION,
    val deviceName: String,
    val boxCount: Int,
    val isGameStarted: Boolean = false,
    val turnId: Long = 1L,
    val activeBoxId: Int = 1,
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_HANDSHAKE

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("senderApp", senderApp)
        put("version", version)
        put("deviceName", deviceName)
        put("boxCount", boxCount)
        put("isGameStarted", isGameStarted)
        put("turnId", turnId)
        put("activeBoxId", activeBoxId)
        put("timestamp", timestamp)
    }
}

/**
 * Handshake acknowledgement response.
 */
data class HandshakeAckMessage(
    val senderApp: String = BluetoothConstants.SENDER_APP_HOST,
    val version: Int = BluetoothConstants.PROTOCOL_VERSION,
    val deviceName: String,
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_HANDSHAKE_ACK

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("senderApp", senderApp)
        put("version", version)
        put("deviceName", deviceName)
        put("timestamp", timestamp)
    }
}

/**
 * Full game configuration broadcast from App 1 (Host) to App 2 when game starts
 * or when App 2 connects after game has started.
 */
data class GameConfigurationMessage(
    val boxCount: Int,
    val turnId: Long,
    val activeBoxId: Int,
    val isGameStarted: Boolean,
    val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_GAME_CONFIGURATION

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

/**
 * Broadcast when the clockwise turn advances to the next box.
 */
data class TurnUpdateMessage(
    val turnId: Long,
    val activeBoxId: Int,
    val previousBoxId: Int = 0,
    val previousResult: Int? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_TURN_UPDATE

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("turnId", turnId)
        put("activeBoxId", activeBoxId)
        put("previousBoxId", previousBoxId)
        if (previousResult != null) put("previousResult", previousResult)
        put("timestamp", timestamp)
    }
}

/**
 * Box-Specific Command sent by Controller (App 2).
 * Can be sent for ANY box at ANY time.
 */
data class ControllerCommandMessage(
    val commandId: String,
    val boxId: Int,
    val value: Int,
    val turnId: Long? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_CONTROLLER_COMMAND

    val requestId: String get() = commandId

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("commandId", commandId)
        put("requestId", commandId)
        put("boxId", boxId)
        put("value", value)
        if (turnId != null) put("turnId", turnId)
        put("timestamp", timestamp)
    }
}

/**
 * Backward compatibility alias for ProvideNumberMessage.
 */
typealias ProvideNumberMessage = ControllerCommandMessage

/**
 * Host (App 1) response acknowledging receipt of Controller's command.
 */
data class CommandAckMessage(
    val commandId: String,
    val boxId: Int,
    val accepted: Boolean,
    val reason: String = "",
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_COMMAND_ACK

    val requestId: String get() = commandId

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("commandId", commandId)
        put("requestId", commandId)
        put("boxId", boxId)
        put("accepted", accepted)
        if (reason.isNotBlank()) put("reason", reason)
        put("timestamp", timestamp)
    }
}

/**
 * Backward compatibility alias for ProvideNumberAckMessage.
 */
typealias ProvideNumberAckMessage = CommandAckMessage

/**
 * Broadcast by Host (App 1) when a box number is revealed on its authoritative turn.
 */
data class NumberResultMessage(
    val boxId: Int,
    val turnId: Long,
    val value: Int,
    val source: String = "LOCAL",
    val requestId: String = UUID.randomUUID().toString(),
    val commandId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_NUMBER_RESULT

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxId", boxId)
        put("turnId", turnId)
        put("value", value)
        put("source", source)
        put("requestId", requestId)
        if (commandId != null) put("commandId", commandId)
        put("timestamp", timestamp)
    }
}

/**
 * Reset game command.
 */
data class ResetGameMessage(
    val boxCount: Int = BluetoothConstants.DEFAULT_BOX_COUNT,
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_RESET_GAME

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxCount", boxCount)
        put("timestamp", timestamp)
    }
}

// ----------------- Legacy Messages for Backward Compatibility -----------------

data class ConfigUpdateMessage(
    val boxCount: Int,
    val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_CONFIG_UPDATE

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxCount", boxCount)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class RequestRandomMessage(
    val boxId: Int,
    val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_REQUEST_RANDOM

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxId", boxId)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class RandomResultMessage(
    val boxId: Int,
    val value: Int,
    val source: String = "LOCAL",
    val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_RANDOM_RESULT

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxId", boxId)
        put("value", value)
        put("source", source)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class ResetMessage(
    val boxCount: Int,
    val requestId: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_RESET

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("boxCount", boxCount)
        put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class ErrorMessage(
    val code: String,
    val message: String,
    val requestId: String = "",
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_ERROR

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("code", code)
        put("message", message)
        if (requestId.isNotBlank()) put("requestId", requestId)
        put("timestamp", timestamp)
    }
}

data class PingMessage(
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_PING

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("timestamp", timestamp)
    }
}

data class PongMessage(
    override val timestamp: Long = System.currentTimeMillis()
) : BluetoothMessage() {
    override val type: String = TYPE_PONG

    override fun toJsonObject(): JSONObject = JSONObject().apply {
        put("type", type)
        put("timestamp", timestamp)
    }
}
