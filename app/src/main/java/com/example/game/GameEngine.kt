package com.example.game

import com.example.game.model.BoxState
import com.example.game.model.RollResult
import com.example.game.model.RollSource
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean Game Engine interface.
 * Current implementation: [RandomNumberGameEngine].
 * Future implementation: [LudoGameEngine].
 *
 * The Ludo App is the ONLY application that controls the actual game turn.
 * The Controller can send a number for ANY player/box at ANY time.
 * App 1 stores these incoming commands as BOX-SPECIFIC PENDING COMMANDS.
 */
interface GameEngine {
    val engineType: String

    val isGameStarted: StateFlow<Boolean>

    val selectedBoxCount: StateFlow<Int>

    val activeBoxCount: StateFlow<Int>

    val currentTurnId: StateFlow<Long>

    val activeBoxId: StateFlow<Int>

    val boxesState: StateFlow<Map<Int, BoxState>>

    fun selectBoxCount(count: Int): Boolean

    fun startGame(): Boolean

    /**
     * Handles tapping a game box.
     * Only the currently active turn box (with green border) will process the tap!
     * If that box has a pending controller value, reveals and consumes it.
     * Otherwise, generates a local random integer 1–6.
     */
    fun tapBox(boxId: Int): RollResult?

    /**
     * Stores a box-specific pending command from Controller.
     * Can be received for ANY box at ANY time.
     *
     * @param commandId Unique ID for deduplication and exactly-once consumption
     * @param boxId Target box (1..activeBoxCount)
     * @param value Integer strictly 1..6
     * @return true if accepted/stored, false if rejected (e.g. invalid value, already consumed)
     */
    fun queueControllerCommand(commandId: String, boxId: Int, value: Int): Boolean

    /**
     * Compatibility alias for queueControllerCommand.
     */
    fun receiveApp2Number(boxId: Int, turnId: Long, value: Int, requestId: String): Boolean

    fun resetToSetup()
}
