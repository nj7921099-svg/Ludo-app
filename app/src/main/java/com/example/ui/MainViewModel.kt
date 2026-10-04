package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.GameEngine
import com.example.game.RandomNumberGameEngine
import com.example.game.model.BoxState
import com.example.game.model.RollSource
import com.example.network.NetworkConstants
import com.example.network.model.AckMsg
import com.example.network.model.BoxIdParser
import com.example.network.model.ConfigMsg
import com.example.network.model.GameEventMsg
import com.example.network.model.GetConfigMsg
import com.example.network.model.HandshakeMsg
import com.example.network.model.HostConnectionState
import com.example.network.model.NetworkConnectionState
import com.example.network.model.NetworkMessage
import com.example.network.model.NumberResultMsg
import com.example.network.model.NumberSelectionMsg
import com.example.network.model.SendResult
import com.example.network.model.StateSyncMsg
import com.example.network.service.WifiHostServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab {
    GAME,
    CONNECT
}

data class MainUiState(
    val isGameStarted: Boolean = false,
    val selectedBoxCount: Int = NetworkConstants.DEFAULT_BOX_COUNT,
    val activeBoxCount: Int = NetworkConstants.DEFAULT_BOX_COUNT,
    val currentTurnId: Long = 1L,
    val activeBoxId: Int = 1,
    val boxes: List<BoxState> = emptyList(),
    val connectionState: NetworkConnectionState = NetworkConnectionState(),
    val currentTab: AppTab = AppTab.GAME,
    val isProtocolInfoVisible: Boolean = false,
    val lastRollSummary: String? = null,
    val isInputLocked: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val gameEngine: GameEngine = RandomNumberGameEngine(NetworkConstants.DEFAULT_BOX_COUNT)

    // Dedicated Wi-Fi Host TCP server & NSD advertiser
    val wifiHostServer: WifiHostServer = WifiHostServer(application.applicationContext, viewModelScope)

    private val _currentTab = MutableStateFlow(AppTab.GAME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private data class UiAuxState(
        val lastRollSummary: String? = "Select player boxes and press START GAME",
        val isProtocolInfoVisible: Boolean = false,
        val isInputLocked: Boolean = false
    )

    private val _uiAux = MutableStateFlow(UiAuxState())

    // Snapshot of game state
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
        wifiHostServer.connectionState,
        _currentTab,
        _uiAux
    ) { snapshot, boxesMap, connState, tab, aux ->
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
            currentTab = tab,
            isProtocolInfoVisible = aux.isProtocolInfoVisible,
            lastRollSummary = aux.lastRollSummary,
            isInputLocked = aux.isInputLocked
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    val logs: StateFlow<List<String>> = wifiHostServer.logs

    init {
        // Observe incoming messages from Controller client over TCP
        viewModelScope.launch {
            wifiHostServer.incomingMessages.collect { message ->
                handleIncomingNetworkMessage(message)
            }
        }

        // When connection transitions to CONNECTED, broadcast initial configuration to Controller
        viewModelScope.launch {
            wifiHostServer.connectionState.collect { connState ->
                if (connState.state == HostConnectionState.CONNECTED) {
                    sendCurrentGameConfiguration()
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun showProtocolInfo(show: Boolean) {
        _uiAux.update { it.copy(isProtocolInfoVisible = show) }
    }

    private fun sendCurrentGameConfiguration() {
        if (!wifiHostServer.connectionState.value.isFullyConnected) return

        val msg = ConfigMsg(
            boxCount = if (gameEngine.isGameStarted.value) gameEngine.activeBoxCount.value else gameEngine.selectedBoxCount.value,
            turnId = gameEngine.currentTurnId.value,
            activeBoxId = gameEngine.activeBoxId.value,
            isGameStarted = gameEngine.isGameStarted.value
        )
        wifiHostServer.sendMessage(msg)
    }

    private suspend fun handleIncomingNetworkMessage(message: NetworkMessage) {
        when (message) {
            is NumberSelectionMsg -> {
                handleNumberSelection(message)
            }

            is GetConfigMsg -> {
                sendCurrentGameConfiguration()
            }

            is StateSyncMsg -> {
                sendCurrentGameConfiguration()
            }

            is GameEventMsg -> {
                if (message.event == "RESET_GAME") {
                    resetToSetup()
                }
            }

            else -> {}
        }
    }

    /**
     * LUDO HOST NUMBER HANDLING:
     * When NUMBER_SELECTION is received:
     * 1. Validate value 1..6
     * 2. Normalize and validate boxId (NO SILENT FALLBACK on invalid boxId!)
     * 3. Validate target box is within active game range
     * 4. Process existing number-selection logic
     * 5. Send ACK for the SAME requestId
     * 6. Send NUMBER_RESULT for the SAME requestId
     */
    private suspend fun handleNumberSelection(message: NumberSelectionMsg) {
        // 1. Validate value 1..6
        if (message.value !in NetworkConstants.MIN_RANDOM_VALUE..NetworkConstants.MAX_RANDOM_VALUE) {
            val reason = "Value ${message.value} out of allowed range (${NetworkConstants.MIN_RANDOM_VALUE}..${NetworkConstants.MAX_RANDOM_VALUE})"
            wifiHostServer.appendLog("Rejected NUMBER_SELECTION: $reason")
            wifiHostServer.sendPacket(
                AckMsg(
                    requestId = message.requestId,
                    status = "ERROR",
                    reason = reason
                )
            )
            return
        }

        // 2. Validate boxId: NO SILENT FALLBACK on invalid or unrecognized boxId!
        if (message.isExplicitBoxIdProvided && !message.isBoxIdValid) {
            val reason = message.boxIdErrorReason ?: "Invalid or unsupported boxId '${message.rawBoxId}'. Expected B1-B6 or R1-R6."
            wifiHostServer.appendLog("Rejected NUMBER_SELECTION: $reason")
            wifiHostServer.sendPacket(
                AckMsg(
                    requestId = message.requestId,
                    status = "ERROR",
                    reason = reason
                )
            )
            return
        }

        // 3. Determine and validate target box
        val maxAllowed = if (gameEngine.isGameStarted.value) gameEngine.activeBoxCount.value else gameEngine.selectedBoxCount.value
        val targetBoxId = if (message.isExplicitBoxIdProvided) {
            val specified = message.boxId!!
            if (specified !in 1..maxAllowed) {
                val label = message.rawBoxId ?: "B$specified"
                val reason = "Box $label is out of current active game range (1..$maxAllowed)"
                wifiHostServer.appendLog("Rejected NUMBER_SELECTION: $reason")
                wifiHostServer.sendPacket(
                    AckMsg(
                        requestId = message.requestId,
                        status = "ERROR",
                        reason = reason
                    )
                )
                return
            }
            specified
        } else {
            // Only use activeBoxId when the protocol message genuinely does NOT specify a boxId!
            gameEngine.activeBoxId.value
        }

        // 4. Process the existing number-selection logic
        val accepted = gameEngine.queueControllerCommand(
            commandId = message.requestId,
            boxId = targetBoxId,
            value = message.value
        )

        val targetLabel = "R$targetBoxId"

        // 5. Send ACK for the SAME requestId
        val ack = AckMsg(
            requestId = message.requestId,
            status = if (accepted) "OK" else "ERROR",
            reason = if (accepted) "Stored pending for $targetLabel" else "Command rejected (duplicate or consumed)"
        )
        val ackResult = wifiHostServer.sendPacket(ack)
        if (ackResult is SendResult.Success) {
            wifiHostServer.appendLog("✓ ACK sent for requestId ${message.requestId} (target: $targetLabel)")
        }

        // 6. Send NUMBER_RESULT for the SAME requestId
        if (accepted) {
            val resultMsg = NumberResultMsg(
                boxId = targetBoxId,
                turnId = gameEngine.currentTurnId.value,
                value = message.value,
                source = "REMOTE",
                rawBoxId = message.rawBoxId ?: BoxIdParser.toControllerBoxId(targetBoxId),
                requestId = message.requestId
            )
            val resResult = wifiHostServer.sendPacket(resultMsg)
            if (resResult is SendResult.Success) {
                wifiHostServer.appendLog("✓ NUMBER_RESULT sent for requestId ${message.requestId} (target: $targetLabel, value: ${message.value})")
            }
            _uiAux.update { it.copy(lastRollSummary = "Controller queued ${message.value} for $targetLabel!") }
        }
    }

    /**
     * Executes real end-to-end NUMBER_SELECTION -> ACK -> NUMBER_RESULT test with 5s timeout.
     */
    fun runNumberCommunicationTest(value: Int) {
        viewModelScope.launch {
            wifiHostServer.executeNumberTest(value, gameEngine.activeBoxId.value)
        }
    }

    /**
     * Compatibility alias for number test button.
     */
    fun simulateNumberTest(value: Int) {
        runNumberCommunicationTest(value)
    }

    fun selectBoxCount(count: Int) {
        if (gameEngine.selectBoxCount(count)) {
            _uiAux.update { it.copy(lastRollSummary = "$count players selected. Press START GAME.") }
            sendCurrentGameConfiguration()
        }
    }

    fun startGame() {
        if (gameEngine.startGame()) {
            val count = gameEngine.activeBoxCount.value
            _uiAux.update { it.copy(lastRollSummary = "Game started with $count boxes! R1's turn (tap to roll).") }
            wifiHostServer.appendLog("Game started with $count boxes. Active turn: R1")
            sendCurrentGameConfiguration()
        }
    }

    fun tapBox(boxId: Int) {
        if (_uiAux.value.isInputLocked) return
        if (!gameEngine.isGameStarted.value) return

        val currentActive = gameEngine.activeBoxId.value
        if (boxId != currentActive) return

        _uiAux.update { it.copy(isInputLocked = true) }

        val result = gameEngine.tapBox(boxId)
        if (result != null) {
            val sourceLabel = if (result.source == RollSource.REMOTE) "Controller" else "Local"
            _uiAux.update {
                it.copy(
                    lastRollSummary = "R${result.boxId} revealed: ${result.value} ($sourceLabel). Turn moved to R${gameEngine.activeBoxId.value}."
                )
            }
            wifiHostServer.appendLog("Turn ${result.turnId}: R${result.boxId} revealed ${result.value} via $sourceLabel. Next turn: R${gameEngine.activeBoxId.value}")

            // Broadcast NUMBER_RESULT to Controller client
            if (wifiHostServer.connectionState.value.isFullyConnected) {
                val resultMsg = NumberResultMsg(
                    boxId = result.boxId,
                    turnId = result.turnId,
                    value = result.value,
                    source = result.source.name,
                    requestId = result.requestId
                )
                wifiHostServer.sendMessage(resultMsg)

                // Also send TURN_UPDATE event
                val eventMsg = GameEventMsg(
                    event = "TURN_UPDATE",
                    turnId = gameEngine.currentTurnId.value,
                    activeBoxId = gameEngine.activeBoxId.value,
                    previousBoxId = result.boxId,
                    previousResult = result.value
                )
                wifiHostServer.sendMessage(eventMsg)
            }
        }

        viewModelScope.launch {
            delay(250)
            _uiAux.update { it.copy(isInputLocked = false) }
        }
    }

    fun resetToSetup() {
        gameEngine.resetToSetup()
        _uiAux.update { it.copy(lastRollSummary = "Game reset to Setup. Select player count and start new game.") }
        wifiHostServer.appendLog("Ludo game reset to Setup mode")

        if (wifiHostServer.connectionState.value.isFullyConnected) {
            val eventMsg = GameEventMsg(
                event = "RESET_GAME",
                turnId = 1L,
                activeBoxId = 1
            )
            wifiHostServer.sendMessage(eventMsg)
        }
    }

    fun testConnection() {
        wifiHostServer.testConnection()
    }

    fun restartHostServer() {
        wifiHostServer.restartServer()
    }

    fun disconnectClient() {
        wifiHostServer.disconnectClient()
    }

    fun clearLogs() {
        wifiHostServer.clearLogs()
    }

    override fun onCleared() {
        super.onCleared()
        wifiHostServer.cleanup()
    }
}
