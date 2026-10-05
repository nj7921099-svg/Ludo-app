package com.example.game.ludo.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Thread-safe registry for box/player-specific pending controller dice commands.
 *
 * Responsibilities:
 * 1. Stores pending controller dice numbers by canonical player ID.
 * 2. Multiple players can have pending numbers simultaneously (e.g. P4 -> 6, P2 -> 3).
 * 3. A pending number belongs permanently to its assigned player and NEVER migrates.
 * 4. Deduplicates incoming commands and enforces exactly-once consumption.
 * 5. Consumes a command ONLY after it has actually been used as that player's dice result.
 */
class PendingNumberStore {

    private val lock = Any()

    // Map of playerId -> PendingDiceCommand
    private val _pendingCommands = MutableStateFlow<Map<Int, PendingDiceCommand>>(emptyMap())
    val pendingCommands: StateFlow<Map<Int, PendingDiceCommand>> = _pendingCommands.asStateFlow()

    // Consumed command IDs to prevent re-use
    private val consumedCommandIds = mutableSetOf<String>()

    /**
     * Queues a dice command received from Controller.
     *
     * @param commandId Unique request ID from Controller.
     * @param playerId Canonical target player ID (1..6).
     * @param number Dice integer strictly 1..6.
     * @param rawBoxId Controller wire string (e.g. "B4").
     * @return true if stored or duplicate acknowledged, false if rejected (invalid value or already consumed).
     */
    fun queueCommand(
        commandId: String,
        playerId: Int,
        number: Int,
        rawBoxId: String? = null
    ): Boolean = synchronized(lock) {
        if (number !in 1..6) {
            return false
        }

        // If this commandId was ALREADY consumed, reject it
        if (consumedCommandIds.contains(commandId)) {
            return false
        }

        val currentMap = _pendingCommands.value
        val existing = currentMap[playerId]
        // Idempotent duplicate check: already pending with same commandId
        if (existing != null && existing.commandId == commandId) {
            return true
        }

        val command = PendingDiceCommand(
            commandId = commandId,
            playerId = playerId,
            number = number,
            rawBoxId = rawBoxId,
            timestamp = System.currentTimeMillis()
        )

        _pendingCommands.update { current ->
            current + (playerId to command)
        }

        return true
    }

    /**
     * Peeks at the pending command for a specific player without consuming it.
     */
    fun getPendingCommand(playerId: Int): PendingDiceCommand? = synchronized(lock) {
        return _pendingCommands.value[playerId]
    }

    /**
     * Consumes and removes the pending command for the specified player.
     * Called ONLY after the dice result has been accepted by the Ludo engine.
     *
     * @param playerId Target player whose pending command is being consumed.
     * @return The consumed [PendingDiceCommand], or null if none was pending.
     */
    fun consumePendingCommand(playerId: Int): PendingDiceCommand? = synchronized(lock) {
        val command = _pendingCommands.value[playerId] ?: return null

        consumedCommandIds.add(command.commandId)

        _pendingCommands.update { current ->
            current - playerId
        }

        return command
    }

    /**
     * Checks if a commandId has already been consumed.
     */
    fun isCommandConsumed(commandId: String): Boolean = synchronized(lock) {
        return consumedCommandIds.contains(commandId)
    }

    /**
     * Clears all pending and consumed commands (e.g. on full game reset).
     */
    fun clear() = synchronized(lock) {
        _pendingCommands.value = emptyMap()
        consumedCommandIds.clear()
    }
}
