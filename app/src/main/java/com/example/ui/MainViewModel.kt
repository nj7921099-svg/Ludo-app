package com.example.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothConstants
import com.example.bluetooth.model.BluetoothConnectionState
import com.example.bluetooth.model.BluetoothMessage
import com.example.bluetooth.model.BluetoothStatusType
import com.example.bluetooth.model.CommandAckMessage
import com.example.bluetooth.model.ConfigUpdateMessage
import com.example.bluetooth.model.ControllerCommandMessage
import com.example.bluetooth.model.DiscoveredBluetoothDevice
import com.example.bluetooth.model.ErrorMessage
import com.example.bluetooth.model.GameConfigurationMessage
import com.example.bluetooth.model.HandshakeAckMessage
import com.example.bluetooth.model.HandshakeMessage
import com.example.bluetooth.model.NumberResultMessage
import com.example.bluetooth.model.ProvideNumberAckMessage
import com.example.bluetooth.model.ProvideNumberMessage
import com.example.bluetooth.model.RandomResultMessage
import com.example.bluetooth.model.RequestRandomMessage
import com.example.bluetooth.model.ResetGameMessage
import com.example.bluetooth.model.ResetMessage
import com.example.bluetooth.model.TurnUpdateMessage
import com.example.bluetooth.service.BluetoothConnectionManager
import com.example.game.GameEngine
import com.example.game.RandomNumberGameEngine
import com.example.game.model.BoxState
import com.example.game.model.RollSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MainUiState(
    val isGameStarted: Boolean = false,
    val selectedBoxCount: Int = BluetoothConstants.DEFAULT_BOX_COUNT,
    val activeBoxCount: Int = BluetoothConstants.DEFAULT_BOX_COUNT,
    val currentTurnId: Long = 1L,
    val activeBoxId: Int = 1,
    val boxes: List<BoxState> = emptyList(),
    val connectionState: BluetoothConnectionState = BluetoothConnectionState(),
    val isConnectionCardExpanded: Boolean = true,
    val isScanDialogVisible: Boolean = false,
    val isProtocolInfoVisible: Boolean = false,
    val isLogsVisible: Boolean = false,
    val lastRollSummary: String? = null,
    val isInputLocked: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Clean Architecture: GameEngine is decoupled from Bluetooth and UI
    val gameEngine: GameEngine = RandomNumberGameEngine(BluetoothConstants.DEFAULT_BOX_COUNT)

    // Bluetooth layer
    val bluetoothManager: BluetoothConnectionManager =
        BluetoothConnectionManager(application.applicationContext, viewModelScope)

    private data class UiExtraState(
        val isConnectionCardExpanded: Boolean = true,
        val isScanDialogVisible: Boolean = false,
        val isProtocolInfoVisible: Boolean = false,
        val isLogsVisible: Boolean = false,
        val lastRollSummary: String? = "Select player boxes and press START GAME",
        val isInputLocked: Boolean = false
    )

    private val _uiExtra = MutableStateFlow(UiExtraState())

    // Combine GameEngine state + Bluetooth state + UI flags
    private data class GameSnapshot(
        val isStarted: Boolean,
        val selectedCount: Int,
        val activeCount: Int,
        val turnId: Long,
        val activeBox: Int
    )

    private val gameSnapshotFlow = combine(
        gameEngine.isGameStarted,
        gameEngine.selectedBoxCount,
        gameEngine.activeBoxCount,
        gameEngine.currentTurnId,
        gameEngine.activeBoxId
    ) { started, selected, active, turnId, activeBox ->
        GameSnapshot(started, selected, active, turnId, activeBox)
    }

    val uiState: StateFlow<MainUiState> = combine(
        gameSnapshotFlow,
        gameEngine.boxesState,
        bluetoothManager.connectionState,
        _uiExtra
    ) { snapshot, boxesMap, connState, extra ->
        val sortedBoxes = if (snapshot.isStarted) {
            (1..snapshot.activeCount).map { id ->
                boxesMap[id] ?: BoxState(boxId = id)
            }
        } else {
            emptyList()
        }

        MainUiState(
            isGameStarted = snapshot.isStarted,
            selectedBoxCount = snapshot.selectedCount,
            activeBoxCount = snapshot.activeCount,
            currentTurnId = snapshot.turnId,
            activeBoxId = snapshot.activeBox,
            boxes = sortedBoxes,
            connectionState = connState,
            isConnectionCardExpanded = extra.isConnectionCardExpanded,
            isScanDialogVisible = extra.isScanDialogVisible,
            isProtocolInfoVisible = extra.isProtocolInfoVisible,
            isLogsVisible = extra.isLogsVisible,
            lastRollSummary = extra.lastRollSummary,
            isInputLocked = extra.isInputLocked
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    val discoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = bluetoothManager.discoveredDevices
    val logs: StateFlow<List<String>> = bluetoothManager.logs

    init {
        // Observe incoming Bluetooth messages from App 2
        viewModelScope.launch {
            bluetoothManager.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }

        // Send handshake and current config when a new connection is established
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connState ->
                if (connState.status == BluetoothStatusType.CONNECTED) {
                    val myDeviceName = Build.MODEL ?: "Host Phone"
                    // Send Handshake
                    val handshake = HandshakeMessage(
                        senderApp = BluetoothConstants.SENDER_APP_HOST,
                        version = BluetoothConstants.PROTOCOL_VERSION,
                        deviceName = myDeviceName,
                        boxCount = gameEngine.selectedBoxCount.value,
                        isGameStarted = gameEngine.isGameStarted.value,
                        turnId = gameEngine.currentTurnId.value,
                        activeBoxId = gameEngine.activeBoxId.value
                    )
                    bluetoothManager.sendMessage(handshake)

                    // Send current game configuration immediately
                    sendCurrentGameConfiguration()
                }
            }
        }
    }

    private fun sendCurrentGameConfiguration() {
        if (bluetoothManager.connectionState.value.status != BluetoothStatusType.CONNECTED) return

        val message = GameConfigurationMessage(
            boxCount = if (gameEngine.isGameStarted.value) gameEngine.activeBoxCount.value else gameEngine.selectedBoxCount.value,
            turnId = gameEngine.currentTurnId.value,
            activeBoxId = gameEngine.activeBoxId.value,
            isGameStarted = gameEngine.isGameStarted.value
        )
        bluetoothManager.sendMessage(message)
    }

    private fun handleIncomingMessage(message: BluetoothMessage) {
        when (message) {
            is HandshakeMessage -> {
                bluetoothManager.appendLog("Handshake from ${message.deviceName}")
                val myDeviceName = Build.MODEL ?: "Host Phone"
                bluetoothManager.sendMessage(
                    HandshakeAckMessage(
                        senderApp = BluetoothConstants.SENDER_APP_HOST,
                        version = BluetoothConstants.PROTOCOL_VERSION,
                        deviceName = myDeviceName
                    )
                )
                sendCurrentGameConfiguration()
            }

            is HandshakeAckMessage -> {
                bluetoothManager.appendLog("Handshake ACK from ${message.deviceName}")
                sendCurrentGameConfiguration()
            }

            is ControllerCommandMessage -> {
                handleControllerCommand(message)
            }

            is RequestRandomMessage -> {
                // Legacy support for RequestRandomMessage
                bluetoothManager.appendLog("Legacy REQUEST_RANDOM for R${message.boxId}")
                if (gameEngine.isGameStarted.value && message.boxId == gameEngine.activeBoxId.value) {
                    tapBox(message.boxId)
                }
            }

            is ResetGameMessage, is ResetMessage -> {
                bluetoothManager.appendLog("Reset message received from App 2")
                resetToSetup()
            }

            is ErrorMessage -> {
                bluetoothManager.appendLog("App 2 error: [${message.code}] ${message.message}")
            }

            else -> {}
        }
    }

    /**
     * App 2 Controller sent a number for ANY box at ANY time.
     * Stored specifically as a BOX-SPECIFIC PENDING COMMAND.
     * Does NOT touch the currently active box unless the command was specifically targeted for it!
     */
    private fun handleControllerCommand(message: ControllerCommandMessage) {
        val accepted = gameEngine.queueControllerCommand(
            commandId = message.commandId,
            boxId = message.boxId,
            value = message.value
        )

        // Always send ACK back to Controller
        bluetoothManager.sendMessage(
            CommandAckMessage(
                commandId = message.commandId,
                boxId = message.boxId,
                accepted = accepted,
                reason = if (accepted) "Stored specifically for R${message.boxId}" else "Command rejected (invalid value or already consumed)"
            )
        )

        if (accepted) {
            bluetoothManager.appendLog("✓ Stored controller command #${message.commandId} (value=${message.value}) specifically for R${message.boxId}")
            _uiExtra.update {
                it.copy(
                    lastRollSummary = "Controller queued value ${message.value} specifically for R${message.boxId}!"
                )
            }
        } else {
            bluetoothManager.appendLog("Rejected controller command #${message.commandId} for R${message.boxId}")
        }
    }

    /**
     * Select box count in SETUP mode (2..6).
     */
    fun selectBoxCount(count: Int) {
        if (gameEngine.selectBoxCount(count)) {
            _uiExtra.update {
                it.copy(
                    lastRollSummary = "$count players selected. Press START GAME."
                )
            }
        }
    }

    /**
     * User taps "START GAME".
     */
    fun startGame() {
        if (gameEngine.startGame()) {
            val count = gameEngine.activeBoxCount.value
            _uiExtra.update {
                it.copy(
                    lastRollSummary = "Game started with $count boxes! R1's turn (tap to roll)."
                )
            }
            bluetoothManager.appendLog("Game started with $count boxes. Active turn: R1")

            // Broadcast game configuration to App 2
            sendCurrentGameConfiguration()
        }
    }

    /**
     * User taps a box on App 1.
     * The Ludo Game is the ONLY authority over turns:
     * Only the currently active box (with green glowing border) responds!
     */
    fun tapBox(boxId: Int) {
        if (_uiExtra.value.isInputLocked) return
        if (!gameEngine.isGameStarted.value) return

        val currentActive = gameEngine.activeBoxId.value
        if (boxId != currentActive) {
            // Tapping inactive box does nothing
            return
        }

        // Lock input momentarily during reveal animation
        _uiExtra.update { it.copy(isInputLocked = true) }

        val result = gameEngine.tapBox(boxId)
        if (result != null) {
            val sourceLabel = if (result.source == RollSource.REMOTE) "Controller (Remote)" else "Host (Local)"
            _uiExtra.update {
                it.copy(
                    lastRollSummary = "R${result.boxId} revealed: ${result.value} ($sourceLabel). Turn moved to R${gameEngine.activeBoxId.value}."
                )
            }
            bluetoothManager.appendLog("Turn ${result.turnId}: R${result.boxId} revealed ${result.value} via $sourceLabel. Next turn: R${gameEngine.activeBoxId.value}")

            // Broadcast result to App 2
            if (bluetoothManager.connectionState.value.status == BluetoothStatusType.CONNECTED) {
                val resultMsg = NumberResultMessage(
                    boxId = result.boxId,
                    turnId = result.turnId,
                    value = result.value,
                    source = result.source.name,
                    requestId = result.requestId,
                    commandId = result.commandId
                )
                bluetoothManager.sendMessage(resultMsg)

                // Also notify App 2 of the new active turn!
                val turnUpdateMsg = TurnUpdateMessage(
                    turnId = gameEngine.currentTurnId.value,
                    activeBoxId = gameEngine.activeBoxId.value,
                    previousBoxId = result.boxId,
                    previousResult = result.value
                )
                bluetoothManager.sendMessage(turnUpdateMsg)
            }
        }

        // Unlock after short animation delay
        viewModelScope.launch {
            delay(300)
            _uiExtra.update { it.copy(isInputLocked = false) }
        }
    }

    /**
     * Resets game back to SETUP mode.
     */
    fun resetToSetup() {
        gameEngine.resetToSetup()
        _uiExtra.update {
            it.copy(
                lastRollSummary = "Game reset to Setup. Select player count and start new game.",
                isInputLocked = false
            )
        }
        bluetoothManager.appendLog("Host reset game to Setup mode")

        if (bluetoothManager.connectionState.value.status == BluetoothStatusType.CONNECTED) {
            val message = ResetGameMessage(boxCount = gameEngine.selectedBoxCount.value)
            bluetoothManager.sendMessage(message)
        }
    }

    fun toggleConnectionCard() {
        _uiExtra.update { it.copy(isConnectionCardExpanded = !it.isConnectionCardExpanded) }
    }

    fun showScanDialog(show: Boolean) {
        _uiExtra.update { it.copy(isScanDialogVisible = show) }
        if (show) {
            bluetoothManager.refreshPairedDevices()
            bluetoothManager.startScan()
        } else {
            bluetoothManager.stopScan()
        }
    }

    fun showProtocolInfo(show: Boolean) {
        _uiExtra.update { it.copy(isProtocolInfoVisible = show) }
    }

    fun showLogs(show: Boolean) {
        _uiExtra.update { it.copy(isLogsVisible = show) }
    }

    fun connectToDevice(address: String) {
        bluetoothManager.connectToDevice(address)
        _uiExtra.update { it.copy(isScanDialogVisible = false) }
    }

    fun disconnect() {
        bluetoothManager.disconnectCurrent(closeServer = false)
        bluetoothManager.startServerListener()
    }

    fun retryPermissionsOrBluetooth() {
        bluetoothManager.checkAndInitialize()
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.cleanup()
    }
}
