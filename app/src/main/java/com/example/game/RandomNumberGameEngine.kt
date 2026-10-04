package com.example.game

import com.example.bluetooth.BluetoothConstants
import com.example.game.model.BoxState
import com.example.game.model.BoxTurnState
import com.example.game.model.PendingControllerCommand
import com.example.game.model.RollResult
import com.example.game.model.RollSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom
import java.util.UUID

/**
 * Implementation of [GameEngine] for App 1 (Ludo Host).
 *
 * Rules:
 * - App 1 (Ludo Game) is the SOLE authority over the turn.
 * - Controller can send a number for ANY box at ANY time.
 * - Stored in a box-specific pending queue (e.g. R4->6 and R2->3 coexist independently).
 * - A pending value NEVER migrates to another box.
 * - When a box's turn arrives and user taps:
 *     - If this exact box has a pending controller value: reveals and consumes it.
 *     - Else: generates local random 1–6.
 * - Exactly-once consumption: consumed commandIds are never reused.
 * - Duplicate command protection: duplicate commandIds are acknowledged without corrupting state.
 */
class RandomNumberGameEngine(
    initialBoxCount: Int = BluetoothConstants.DEFAULT_BOX_COUNT
) : GameEngine {

    override val engineType: String = "AuthoritativeTurnLudoRNG_v3"

    private val secureRandom = SecureRandom()

    private val _isGameStarted = MutableStateFlow(false)
    override val isGameStarted: StateFlow<Boolean> = _isGameStarted.asStateFlow()

    private val _selectedBoxCount = MutableStateFlow(
        initialBoxCount.coerceIn(BluetoothConstants.MIN_BOXES, BluetoothConstants.MAX_BOXES)
    )
    override val selectedBoxCount: StateFlow<Int> = _selectedBoxCount.asStateFlow()

    private val _activeBoxCount = MutableStateFlow(initialBoxCount)
    override val activeBoxCount: StateFlow<Int> = _activeBoxCount.asStateFlow()

    private val _currentTurnId = MutableStateFlow(1L)
    override val currentTurnId: StateFlow<Long> = _currentTurnId.asStateFlow()

    private val _activeBoxId = MutableStateFlow(1)
    override val activeBoxId: StateFlow<Int> = _activeBoxId.asStateFlow()

    private val _boxesState = MutableStateFlow<Map<Int, BoxState>>(emptyMap())
    override val boxesState: StateFlow<Map<Int, BoxState>> = _boxesState.asStateFlow()

    // Box-specific pending commands queue (Key: boxId 1..6)
    // Multiple pending commands can coexist independently (e.g. R4 -> 6, R2 -> 3)
    private val pendingCommandsByBox = mutableMapOf<Int, PendingControllerCommand>()

    // Track consumed command IDs so they are never executed again
    private val consumedCommandIds = mutableSetOf<String>()

    override fun selectBoxCount(count: Int): Boolean {
        if (_isGameStarted.value) return false
        if (count !in BluetoothConstants.MIN_BOXES..BluetoothConstants.MAX_BOXES) return false
        _selectedBoxCount.value = count
        return true
    }

    override fun startGame(): Boolean {
        val count = _selectedBoxCount.value
        _activeBoxCount.value = count
        _currentTurnId.value = 1L
        _activeBoxId.value = 1
        consumedCommandIds.clear()

        // Create boxes: R1 is active turn, R2..Rn are waiting
        val initialMap = mutableMapOf<Int, BoxState>()
        for (i in 1..count) {
            initialMap[i] = BoxState(
                boxId = i,
                currentValue = null,
                pendingCommand = pendingCommandsByBox[i],
                isActiveTurn = (i == 1),
                turnState = if (i == 1) BoxTurnState.ACTIVE_WAITING_TAP else BoxTurnState.IDLE_UNROLLED
            )
        }
        _boxesState.value = initialMap
        _isGameStarted.value = true
        return true
    }

    /**
     * Stores a box-specific pending command from the Controller.
     * Can be received for ANY box at ANY time.
     */
    @Synchronized
    override fun queueControllerCommand(commandId: String, boxId: Int, value: Int): Boolean {
        // Validate value: integer 1 to 6 only
        if (value !in BluetoothConstants.MIN_RANDOM_VALUE..BluetoothConstants.MAX_RANDOM_VALUE) {
            return false
        }

        // Validate boxId is within valid range (1..MAX_BOXES)
        val maxAllowed = if (_isGameStarted.value) _activeBoxCount.value else _selectedBoxCount.value
        if (boxId !in 1..maxAllowed) {
            return false
        }

        // If this commandId was ALREADY consumed, reject it
        if (consumedCommandIds.contains(commandId)) {
            return false
        }

        // Duplicate check: if already pending with identical commandId for this box, acknowledge safely
        val existing = pendingCommandsByBox[boxId]
        if (existing != null && existing.commandId == commandId) {
            return true
        }

        // Store specifically for boxId without affecting ANY other box!
        val newCommand = PendingControllerCommand(
            commandId = commandId,
            boxId = boxId,
            value = value,
            timestamp = System.currentTimeMillis()
        )
        pendingCommandsByBox[boxId] = newCommand

        // Update box state in UI if game is active
        _boxesState.update { current ->
            val updated = current.toMutableMap()
            val existingBox = updated[boxId] ?: BoxState(boxId = boxId)
            updated[boxId] = existingBox.copy(
                pendingCommand = newCommand
            )
            updated
        }

        return true
    }

    override fun receiveApp2Number(boxId: Int, turnId: Long, value: Int, requestId: String): Boolean {
        return queueControllerCommand(requestId, boxId, value)
    }

    /**
     * Handles user tapping a game box.
     * The Ludo game turn is AUTHORITATIVE: Only the active box can process a tap!
     */
    @Synchronized
    override fun tapBox(boxId: Int): RollResult? {
        if (!_isGameStarted.value) return null

        val currentActive = _activeBoxId.value
        // CRITICAL: Only the currently active box can process a tap!
        if (boxId != currentActive) {
            return null
        }

        val turnId = _currentTurnId.value
        val currentBoxes = _boxesState.value
        val box = currentBoxes[boxId] ?: return null

        // Check if THIS EXACT BOX has a pending controller command
        val pending = pendingCommandsByBox[boxId]

        val (revealedValue, rollSource, commandId) = if (pending != null) {
            // Use pending controller value
            val valFromController = pending.value
            val cmdId = pending.commandId

            // Exactly-once consumption: Mark commandId as consumed!
            consumedCommandIds.add(cmdId)
            pendingCommandsByBox.remove(boxId)

            Triple(valFromController, RollSource.REMOTE, cmdId)
        } else {
            // Local fallback: generate random integer 1 to 6
            val randomVal = secureRandom.nextInt(BluetoothConstants.MAX_RANDOM_VALUE) + 1
            Triple(randomVal, RollSource.LOCAL, null)
        }

        val now = System.currentTimeMillis()
        val totalBoxes = _activeBoxCount.value
        val nextActive = if (currentActive >= totalBoxes) 1 else currentActive + 1
        val nextTurnId = turnId + 1

        _boxesState.update { current ->
            val updated = current.toMutableMap()

            // Update current box: revealed and no longer active turn
            updated[boxId] = box.copy(
                currentValue = revealedValue,
                pendingCommand = null,
                lastSource = rollSource,
                lastTimestamp = now,
                isActiveTurn = false,
                turnState = BoxTurnState.REVEALED
            )

            // Update next box: gets active turn and green glowing border
            val nextBox = updated[nextActive] ?: BoxState(boxId = nextActive)
            updated[nextActive] = nextBox.copy(
                isActiveTurn = true,
                turnState = BoxTurnState.ACTIVE_WAITING_TAP
            )

            updated
        }

        _activeBoxId.value = nextActive
        _currentTurnId.value = nextTurnId

        return RollResult(
            boxId = boxId,
            turnId = turnId,
            value = revealedValue,
            source = rollSource,
            requestId = commandId ?: UUID.randomUUID().toString(),
            commandId = commandId,
            timestamp = now
        )
    }

    override fun resetToSetup() {
        _isGameStarted.value = false
        _currentTurnId.value = 1L
        _activeBoxId.value = 1
        _boxesState.value = emptyMap()
        pendingCommandsByBox.clear()
        consumedCommandIds.clear()
    }
}
