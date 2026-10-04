package com.example.game.model

/**
 * Indicates whether a number generation was triggered locally on App 1 (Host)
 * or requested remotely by App 2 (Controller) over Bluetooth.
 */
enum class RollSource {
    LOCAL,
    REMOTE
}

/**
 * Visual and operational state of an individual game box.
 */
enum class BoxTurnState {
    IDLE_UNROLLED,      // Not the current turn, unrolled
    ACTIVE_WAITING_TAP, // Current active turn with GREEN glowing border, waiting for user tap
    REVEALING,          // Tapped, short animation revealing the number
    REVEALED            // Finished turn with visible revealed integer (1–6)
}

/**
 * Box-specific pending command received from Controller (App 2).
 * The Controller can send this for ANY box at ANY time.
 * Stored specifically for target boxId until that box's authoritative Ludo turn arrives.
 */
data class PendingControllerCommand(
    val commandId: String,
    val boxId: Int,
    val value: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class RollResult(
    val boxId: Int,
    val turnId: Long,
    val value: Int,
    val source: RollSource,
    val requestId: String,
    val commandId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class BoxState(
    val boxId: Int,
    val currentValue: Int? = null,
    val pendingCommand: PendingControllerCommand? = null,
    val lastSource: RollSource? = null,
    val lastTimestamp: Long = 0L,
    val isActiveTurn: Boolean = false,
    val turnState: BoxTurnState = BoxTurnState.IDLE_UNROLLED
) {
    val label: String get() = "R$boxId"
    val isRolled: Boolean get() = currentValue != null
    val hasPendingCommand: Boolean get() = pendingCommand != null
    val hasPendingApp2Value: Boolean get() = pendingCommand != null
    val pendingApp2Value: Int? get() = pendingCommand?.value
    val pendingApp2RequestId: String? get() = pendingCommand?.commandId
}
