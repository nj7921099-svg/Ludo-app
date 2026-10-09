package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.GameEngine
import com.example.game.RandomNumberGameEngine
import com.example.game.model.BoxState
import com.example.game.model.RollSource
import com.example.game.ludo.ai.LudoBotStrategy
import com.example.game.ludo.engine.DiceActivationResult
import com.example.game.ludo.engine.LudoDiceBridge
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.engine.PendingDiceCommand
import com.example.game.ludo.engine.PendingNumberStore
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoMoveResult
import com.example.game.ludo.model.TurnPhase
import com.example.game.ludo.persistence.LudoStatePersistence
import com.example.game.ludo.stats.LudoStatisticsManager
import com.example.game.ludo.stats.model.LudoStatisticsData
import com.example.game.ludo.stats.persistence.LudoStatisticsPersistence
import com.example.network.NetworkConstants
import com.example.network.model.AckMsg
import com.example.network.model.BoxIdParser
import com.example.network.model.ConfigMsg
import com.example.network.model.GameEventMsg
import com.example.network.model.GetConfigMsg
import com.example.network.model.GetLudoStateMsg
import com.example.network.model.HandshakeMsg
import com.example.network.model.HostConnectionState
import com.example.network.model.LudoStateSyncMsg
import com.example.network.model.NetworkConnectionState
import com.example.network.model.NetworkMessage
import com.example.network.model.NumberResultMsg
import com.example.network.model.NumberSelectionMsg
import com.example.network.model.SendResult
import com.example.network.model.StateSyncMsg
import com.example.network.service.WifiHostServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

enum class BotOpponentMode {
    ALL_HUMAN,
    VS_BOTS,
    CUSTOM
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
    val isInputLocked: Boolean = false,
    val ludoGameState: LudoGameState = LudoGameState(),
    val pendingNumbers: Map<Int, Int> = emptyMap(),
    val selectedGameMode: LudoGameMode = LudoGameMode.INDIVIDUAL,
    val botPlayerIds: Set<Int> = emptySet(),
    val botOpponentMode: BotOpponentMode = BotOpponentMode.ALL_HUMAN
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val gameEngine: GameEngine = RandomNumberGameEngine(NetworkConstants.DEFAULT_BOX_COUNT)

    // Ludo Core Engine & Pending Number Store (Phase 3 Integration)
    val ludoGameEngine: LudoGameEngine = LudoGameEngine()
    val pendingNumberStore: PendingNumberStore = PendingNumberStore()
    val ludoDiceBridge: LudoDiceBridge = LudoDiceBridge(ludoGameEngine, pendingNumberStore)

    // Dedicated Wi-Fi Host TCP server & NSD advertiser
    val wifiHostServer: WifiHostServer = WifiHostServer(application.applicationContext, viewModelScope)

    // Dedicated State Persistence & Recovery manager (Phase 7)
    val statePersistence: LudoStatePersistence = LudoStatePersistence(application.applicationContext)

    // Dedicated Statistics & Match History subsystem (Phase 9)
    val statisticsPersistence: LudoStatisticsPersistence = LudoStatisticsPersistence(application.applicationContext)
    val statisticsManager: LudoStatisticsManager = LudoStatisticsManager(statisticsPersistence)
    val statisticsData: StateFlow<LudoStatisticsData> = statisticsManager.statisticsData

    private val _currentTab = MutableStateFlow(AppTab.GAME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Pre-game selected game mode state (Phase 8B)
    private val _selectedGameMode = MutableStateFlow(LudoGameMode.INDIVIDUAL)
    val selectedGameMode: StateFlow<LudoGameMode> = _selectedGameMode.asStateFlow()

    // Bot Player Configuration & Automation (Phase 10)
    private val _botPlayerIds = MutableStateFlow<Set<Int>>(emptySet())
    val botPlayerIds: StateFlow<Set<Int>> = _botPlayerIds.asStateFlow()

    private val _botOpponentMode = MutableStateFlow(BotOpponentMode.ALL_HUMAN)
    val botOpponentMode: StateFlow<BotOpponentMode> = _botOpponentMode.asStateFlow()

    var botActionDelayMs: Long = 500L

    private data class BotActionKey(
        val playerId: Int,
        val turnPhase: TurnPhase,
        val diceValue: Int?,
        val consecutiveSixCount: Int
    )

    private var botActionJob: Job? = null
    private var currentActionKey: BotActionKey? = null
    private var isBotActionInProgress = false

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
        ludoGameEngine.gameState,
        pendingNumberStore.pendingCommands,
        wifiHostServer.connectionState,
        _currentTab,
        _uiAux,
        _selectedGameMode,
        _botPlayerIds,
        _botOpponentMode
    ) { args: Array<Any?> ->
        val snapshot = args[0] as GameSnapshot
        val boxesMap = args[1] as Map<Int, BoxState>
        val ludoState = args[2] as LudoGameState
        val pendingMap = args[3] as Map<Int, PendingDiceCommand>
        val connState = args[4] as NetworkConnectionState
        val tab = args[5] as AppTab
        val aux = args[6] as UiAuxState
        val mode = args[7] as LudoGameMode
        val botIds = args[8] as Set<Int>
        val opponentMode = args[9] as BotOpponentMode

        val sortedBoxes = if (snapshot.isStarted) {
            (1..snapshot.activeCount).map { id ->
                boxesMap[id] ?: BoxState(boxId = id)
            }
        } else {
            emptyList()
        }

        val pendingNumbersMap = pendingMap.mapValues { it.value.number }

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
            isInputLocked = aux.isInputLocked,
            ludoGameState = ludoState,
            pendingNumbers = pendingNumbersMap,
            selectedGameMode = mode,
            botPlayerIds = botIds,
            botOpponentMode = opponentMode
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
                    broadcastLudoStateSync()
                }
            }
        }

        // Observe Ludo game state changes for safe bot-turn coordination (Phase 10)
        viewModelScope.launch {
            ludoGameEngine.gameState.collect { state ->
                handleLudoStateForBot(state)
            }
        }

        // State Persistence & Recovery (Phase 7)
        // Attempt to load and restore any valid persisted LudoGameState snapshot
        try {
            val savedSnapshot = statePersistence.loadState()
            if (savedSnapshot != null && savedSnapshot.isGameStarted) {
                ludoGameEngine.restoreState(savedSnapshot)
                val restoreSummary = when (savedSnapshot.turnPhase) {
                    TurnPhase.WAITING_FOR_DICE_ROLL -> "Restored game: Player ${savedSnapshot.currentPlayerId}'s turn (tap to roll)."
                    TurnPhase.WAITING_FOR_TOKEN_SELECTION -> "Restored game: Player ${savedSnapshot.currentPlayerId} rolled ${savedSnapshot.diceValue}. Tap a highlighted token."
                    TurnPhase.GAME_OVER -> "🏆 Match Finished! Winner: Player ${savedSnapshot.winners.firstOrNull() ?: 1}."
                    else -> "Game restored for Player ${savedSnapshot.currentPlayerId}."
                }
                _uiAux.update { it.copy(lastRollSummary = restoreSummary) }
                wifiHostServer.appendLog("✓ Restored saved match: P${savedSnapshot.currentPlayerId}'s turn (phase: ${savedSnapshot.turnPhase}, players: ${savedSnapshot.playerCount})")
            }
        } catch (e: Exception) {
            wifiHostServer.appendLog("Failed to restore saved game state: ${e.message}")
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun showProtocolInfo(show: Boolean) {
        _uiAux.update { it.copy(isProtocolInfoVisible = show) }
    }

    fun sendCurrentGameConfiguration() {
        if (!wifiHostServer.connectionState.value.isFullyConnected) return

        val ludoState = ludoGameEngine.gameState.value
        val isStarted = ludoState.isGameStarted || gameEngine.isGameStarted.value
        val boxCount = if (ludoState.isGameStarted) {
            ludoState.playerCount
        } else if (gameEngine.isGameStarted.value) {
            gameEngine.activeBoxCount.value
        } else {
            gameEngine.selectedBoxCount.value
        }
        val activeBox = if (ludoState.isGameStarted) ludoState.currentPlayerId else gameEngine.activeBoxId.value

        val msg = ConfigMsg(
            boxCount = boxCount,
            turnId = gameEngine.currentTurnId.value,
            activeBoxId = activeBox,
            isGameStarted = isStarted,
            ludoState = ludoState
        )
        wifiHostServer.sendMessage(msg)
    }

    fun broadcastLudoStateSync(requestId: String? = null) {
        if (!wifiHostServer.connectionState.value.isFullyConnected) return

        val ludoState = ludoGameEngine.gameState.value
        val isStarted = ludoState.isGameStarted || gameEngine.isGameStarted.value
        val boxCount = if (ludoState.isGameStarted) {
            ludoState.playerCount
        } else if (gameEngine.isGameStarted.value) {
            gameEngine.activeBoxCount.value
        } else {
            gameEngine.selectedBoxCount.value
        }
        val activeBox = if (ludoState.isGameStarted) ludoState.currentPlayerId else gameEngine.activeBoxId.value

        val msg = StateSyncMsg(
            boxCount = boxCount,
            turnId = gameEngine.currentTurnId.value,
            activeBoxId = activeBox,
            isGameStarted = isStarted,
            ludoState = ludoState,
            requestId = requestId ?: UUID.randomUUID().toString()
        )
        wifiHostServer.sendMessage(msg)
        wifiHostServer.appendLog("✓ STATE_SYNC broadcast sent (P$activeBox, phase: ${ludoState.turnPhase}, started: $isStarted)")
    }

    private suspend fun handleIncomingNetworkMessage(message: NetworkMessage) {
        when (message) {
            is NumberSelectionMsg -> {
                handleNumberSelection(message)
            }

            is GetConfigMsg -> {
                sendCurrentGameConfiguration()
                broadcastLudoStateSync()
            }

            is StateSyncMsg -> {
                sendCurrentGameConfiguration()
                broadcastLudoStateSync(message.requestId)
            }

            is GetLudoStateMsg -> {
                broadcastLudoStateSync(message.requestId)
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

        // 4. Process number-selection logic (stores as pending for target box in both engines)
        val accepted = gameEngine.queueControllerCommand(
            commandId = message.requestId,
            boxId = targetBoxId,
            value = message.value
        )

        // Phase 3 bridge: queue into PendingNumberStore for LudoGameEngine
        pendingNumberStore.queueCommand(
            commandId = message.requestId,
            playerId = targetBoxId,
            number = message.value,
            rawBoxId = message.rawBoxId
        )

        val targetLabel = "R$targetBoxId"

        // 5. Send ACK for the SAME requestId confirming receipt and pending storage
        val ack = AckMsg(
            requestId = message.requestId,
            status = if (accepted) "OK" else "ERROR",
            reason = if (accepted) "Stored pending for $targetLabel" else "Command rejected (duplicate or consumed)"
        )
        val ackResult = wifiHostServer.sendPacket(ack)
        if (ackResult is SendResult.Success) {
            wifiHostServer.appendLog("✓ ACK sent for requestId ${message.requestId} (target: $targetLabel, pending value: ${message.value})")
        }

        // CRITICAL FIX: DO NOT send NUMBER_RESULT here!
        // The command is stored as pending permanently for targetBoxId.
        // NUMBER_RESULT will ONLY be generated when that box's actual turn arrives
        // and the user taps that box in tapBox().
        if (accepted) {
            _uiAux.update { it.copy(lastRollSummary = "Controller queued ${message.value} for $targetLabel as pending!") }
            wifiHostServer.appendLog("Command for $targetLabel (${message.rawBoxId ?: targetLabel} -> ${message.value}) marked pending. Not revealed until $targetLabel's turn.")
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
        if (_selectedGameMode.value == LudoGameMode.TEAM_UP && count != 4) {
            // Team-Up requires strictly 4 players; ignore invalid counts safely
            return
        }
        if (gameEngine.selectBoxCount(count)) {
            // Prune or sync bot configuration for new player count
            when (_botOpponentMode.value) {
                BotOpponentMode.ALL_HUMAN -> {
                    _botPlayerIds.value = emptySet()
                }
                BotOpponentMode.VS_BOTS -> {
                    _botPlayerIds.value = (2..count).toSet()
                }
                BotOpponentMode.CUSTOM -> {
                    _botPlayerIds.update { current -> current.filter { it in 1..count }.toSet() }
                }
            }
            val modeLabel = if (_selectedGameMode.value == LudoGameMode.TEAM_UP) "Team-Up 2v2" else "Individual"
            _uiAux.update { it.copy(lastRollSummary = "$count players selected ($modeLabel). Press START GAME.") }
            sendCurrentGameConfiguration()
            broadcastLudoStateSync()
        }
    }

    /**
     * Updates the pre-game selected game mode.
     * When [LudoGameMode.TEAM_UP] is selected, automatically sets selected player count to 4.
     * When [LudoGameMode.INDIVIDUAL] is selected, preserves current count if 2..4 or defaults safely to 4.
     */
    fun selectGameMode(mode: LudoGameMode) {
        if (_selectedGameMode.value == mode) return

        _selectedGameMode.value = mode
        if (mode == LudoGameMode.TEAM_UP) {
            gameEngine.selectBoxCount(4)
            when (_botOpponentMode.value) {
                BotOpponentMode.ALL_HUMAN -> _botPlayerIds.value = emptySet()
                BotOpponentMode.VS_BOTS -> _botPlayerIds.value = (2..4).toSet()
                BotOpponentMode.CUSTOM -> _botPlayerIds.update { current -> current.filter { it in 1..4 }.toSet() }
            }
            _uiAux.update { it.copy(lastRollSummary = "Team-Up 2v2 selected (4 players: Red & Yellow vs Green & Blue). Press START GAME.") }
        } else {
            val currentCount = gameEngine.selectedBoxCount.value
            val targetCount = if (currentCount in 2..4) currentCount else 4
            gameEngine.selectBoxCount(targetCount)
            when (_botOpponentMode.value) {
                BotOpponentMode.ALL_HUMAN -> _botPlayerIds.value = emptySet()
                BotOpponentMode.VS_BOTS -> _botPlayerIds.value = (2..targetCount).toSet()
                BotOpponentMode.CUSTOM -> _botPlayerIds.update { current -> current.filter { it in 1..targetCount }.toSet() }
            }
            _uiAux.update { it.copy(lastRollSummary = "Individual mode selected ($targetCount players). Press START GAME.") }
        }
        sendCurrentGameConfiguration()
        broadcastLudoStateSync()
    }

    fun startGame() {
        cancelPendingBotAction()
        val mode = _selectedGameMode.value
        val requestedCount = if (mode == LudoGameMode.TEAM_UP) 4 else gameEngine.selectedBoxCount.value

        // Validate before proceeding
        if (mode == LudoGameMode.TEAM_UP && requestedCount != 4) {
            _uiAux.update { it.copy(lastRollSummary = "Cannot start Team-Up without exactly 4 players.") }
            return
        }

        // Initialize engine with selected mode
        val engineInitSuccess = ludoGameEngine.initGame(
            playerCount = requestedCount.coerceIn(2, 4),
            gameMode = mode
        )

        if (!engineInitSuccess) {
            _uiAux.update { it.copy(lastRollSummary = "Failed to start game: invalid mode or player count.") }
            return
        }

        if (gameEngine.startGame()) {
            val count = gameEngine.activeBoxCount.value
            val modeDesc = if (mode == LudoGameMode.TEAM_UP) "Team-Up 2v2" else "Individual"
            _uiAux.update { it.copy(lastRollSummary = "Game started ($modeDesc, $count boxes)! R1's turn (tap to roll).") }
            wifiHostServer.appendLog("Game started ($modeDesc) with $count boxes. Active turn: R1")

            // Initialize active statistics tracking session (Phase 9)
            statisticsManager.startNewSession(ludoGameEngine.gameState.value)

            persistCurrentLudoState()
            sendCurrentGameConfiguration()
            broadcastLudoStateSync()
        }
    }

    /**
     * Activates the dice for the currently active player using the Phase 3 Ludo bridge.
     * Uses PendingNumberStore if a controller command was sent for this player, otherwise local random.
     */
    fun activateLudoDice(): DiceActivationResult? {
        if (_uiAux.value.isInputLocked) return null
        val result = ludoDiceBridge.onDiceActivated()
        if (result != null) {
            val currentState = ludoGameEngine.gameState.value
            val sourceLabel = if (result.source == RollSource.REMOTE) "Controller" else "Local"

            if (currentState.currentPlayerId != result.playerId) {
                // No legal moves or 3rd consecutive six: turn passed to next player
                _uiAux.update {
                    it.copy(
                        lastRollSummary = "Player ${result.playerId} rolled ${result.diceValue} ($sourceLabel) with no legal moves. Turn passed to Player ${currentState.currentPlayerId}."
                    )
                }
            } else {
                _uiAux.update {
                    it.copy(
                        lastRollSummary = "Player ${result.playerId} rolled ${result.diceValue} ($sourceLabel). Tap a highlighted token."
                    )
                }
            }
            wifiHostServer.appendLog("Dice roll: Player ${result.playerId} rolled ${result.diceValue} via $sourceLabel")

            // Record authoritative dice activation statistics (Phase 9)
            statisticsManager.onDiceRolled(
                playerId = result.playerId,
                diceValue = result.diceValue,
                acceptedByEngine = result.acceptedByEngine
            )

            // Broadcast NUMBER_RESULT to Controller client
            if (wifiHostServer.connectionState.value.isFullyConnected) {
                val resultMsg = NumberResultMsg(
                    boxId = result.playerId,
                    turnId = gameEngine.currentTurnId.value,
                    value = result.diceValue,
                    source = result.source.name,
                    rawBoxId = result.rawBoxId ?: BoxIdParser.toControllerBoxId(result.playerId),
                    requestId = result.commandId ?: UUID.randomUUID().toString()
                )
                wifiHostServer.sendMessage(resultMsg)
                wifiHostServer.appendLog("✓ NUMBER_RESULT sent for Player ${result.playerId} (value: ${result.diceValue}, source: ${result.source})")

                // If turn passed because of no legal moves, also broadcast TURN_UPDATE
                if (currentState.currentPlayerId != result.playerId) {
                    val eventMsg = GameEventMsg(
                        event = "TURN_UPDATE",
                        turnId = gameEngine.currentTurnId.value,
                        activeBoxId = currentState.currentPlayerId,
                        previousBoxId = result.playerId,
                        previousResult = result.diceValue
                    )
                    wifiHostServer.sendMessage(eventMsg)
                }

                // Broadcast authoritative Ludo state sync
                broadcastLudoStateSync()
            }

            // Authoritative state change persisted
            persistCurrentLudoState()
        }
        return result
    }

    /**
     * Executes the move for a highlighted legal token on the Ludo board.
     * Prevents duplicate/stale taps via isInputLocked lock.
     */
    fun moveLudoToken(tokenId: Int): LudoMoveResult? {
        if (_uiAux.value.isInputLocked) return null

        // Lock input to prevent duplicate or conflicting taps while processing
        _uiAux.update { it.copy(isInputLocked = true) }

        val result = try {
            ludoGameEngine.moveToken(tokenId)
        } finally {
            _uiAux.update { it.copy(isInputLocked = false) }
        }

        if (result != null) {
            val summary = when {
                result.isGameOver -> "🏆 Match Finished! Player ${result.playerId} wins!"
                result.extraTurnGranted -> "Player ${result.playerId} moved token $tokenId to step ${result.toStep} and earned an EXTRA TURN (${result.extraTurnReason})! Roll again."
                else -> "Player ${result.playerId} moved token $tokenId to step ${result.toStep}. Next turn: Player ${result.nextPlayerId}."
            }
            _uiAux.update { it.copy(lastRollSummary = summary) }
            wifiHostServer.appendLog("Token moved: Player ${result.playerId} token $tokenId -> step ${result.toStep}. Next: Player ${result.nextPlayerId}")

            // Record authoritative token move statistics (Phase 9)
            // Identify effective token owner to ensure partner assistance finishes are credited correctly
            val currentState = ludoGameEngine.gameState.value
            val isPartnerAssistance = (currentState.gameMode == LudoGameMode.TEAM_UP) &&
                    (currentState.players.find { it.playerId == result.playerId }?.isFinished == true)
            val effectiveTokenOwnerId = if (isPartnerAssistance) {
                val activeP = currentState.players.find { it.playerId == result.playerId }
                currentState.players.find { it.playerId != result.playerId && it.teamId == activeP?.teamId }?.playerId ?: result.playerId
            } else {
                result.playerId
            }
            statisticsManager.onTokenMoved(result, effectiveTokenOwnerId)

            // If move concluded the match, finalize and commit statistics atomically
            if (result.isGameOver) {
                statisticsManager.finalizeMatchIfGameOver(currentState)
            }

            // Also broadcast TURN_UPDATE
            if (wifiHostServer.connectionState.value.isFullyConnected) {
                val eventMsg = GameEventMsg(
                    event = "TURN_UPDATE",
                    turnId = gameEngine.currentTurnId.value,
                    activeBoxId = result.nextPlayerId,
                    previousBoxId = result.playerId,
                    previousResult = result.diceValue
                )
                wifiHostServer.sendMessage(eventMsg)

                // Broadcast authoritative Ludo state sync
                broadcastLudoStateSync()
            }

            // Authoritative state change persisted (token moved, captured, finished, extra turn, or game over)
            persistCurrentLudoState()
        }
        return result
    }

    fun restartLudoRematch() {
        cancelPendingBotAction()
        val currentLudoState = ludoGameEngine.gameState.value
        val count = currentLudoState.playerCount
        val mode = currentLudoState.gameMode

        // Ensure any completed match in the previous session was finalized
        if (currentLudoState.isGameOver) {
            statisticsManager.finalizeMatchIfGameOver(currentLudoState)
        }

        ludoGameEngine.initGame(playerCount = count, gameMode = mode)
        pendingNumberStore.clear()

        // Start a fresh, separate statistics session with a new matchId (Phase 9)
        statisticsManager.startNewSession(ludoGameEngine.gameState.value)

        val modeLabel = if (mode == LudoGameMode.TEAM_UP) "Team-Up 2v2" else "Individual"
        _uiAux.update { it.copy(lastRollSummary = "New match started ($modeLabel)! Player 1's turn (tap dice to roll).") }
        persistCurrentLudoState()
        sendCurrentGameConfiguration()
        broadcastLudoStateSync()
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

            // Broadcast NUMBER_RESULT to Controller client ONLY upon actual turn reveal
            if (wifiHostServer.connectionState.value.isFullyConnected) {
                val resultMsg = NumberResultMsg(
                    boxId = result.boxId,
                    turnId = result.turnId,
                    value = result.value,
                    source = result.source.name,
                    rawBoxId = BoxIdParser.toControllerBoxId(result.boxId),
                    requestId = result.commandId ?: result.requestId
                )
                wifiHostServer.sendMessage(resultMsg)
                wifiHostServer.appendLog("✓ NUMBER_RESULT sent upon R${result.boxId} turn reveal (value: ${result.value}, source: ${result.source}, requestId: ${resultMsg.requestId})")

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
        cancelPendingBotAction()
        gameEngine.resetToSetup()
        ludoGameEngine.resetToSetup()
        pendingNumberStore.clear()
        clearPersistedLudoState()

        // Discard active uncompleted match statistics session (Phase 9)
        statisticsManager.discardActiveSession()

        // If TEAM_UP is currently selected, ensure player count is synchronized to 4
        if (_selectedGameMode.value == LudoGameMode.TEAM_UP) {
            gameEngine.selectBoxCount(4)
        }

        // Validate and preserve bot configuration safely for setup count
        val count = if (_selectedGameMode.value == LudoGameMode.TEAM_UP) 4 else gameEngine.selectedBoxCount.value
        when (_botOpponentMode.value) {
            BotOpponentMode.ALL_HUMAN -> _botPlayerIds.value = emptySet()
            BotOpponentMode.VS_BOTS -> _botPlayerIds.value = (2..count).toSet()
            BotOpponentMode.CUSTOM -> _botPlayerIds.update { current -> current.filter { it in 1..count }.toSet() }
        }

        _uiAux.update { it.copy(lastRollSummary = "Game reset to Setup. Select player count and start new game.") }
        wifiHostServer.appendLog("Ludo game reset to Setup mode")

        if (wifiHostServer.connectionState.value.isFullyConnected) {
            val eventMsg = GameEventMsg(
                event = "RESET_GAME",
                turnId = 1L,
                activeBoxId = 1
            )
            wifiHostServer.sendMessage(eventMsg)
            sendCurrentGameConfiguration()
            broadcastLudoStateSync()
        }
    }

    // =========================================================================
    // Bot Player Control & Safe Execution Coordination (Phase 10)
    // =========================================================================

    /**
     * Updates the bot opponent preset mode:
     * - [BotOpponentMode.ALL_HUMAN]: all players human, botPlayerIds is empty.
     * - [BotOpponentMode.VS_BOTS]: P1 is human, all other active players (P2..Pn) are bots.
     * - [BotOpponentMode.CUSTOM]: custom per-slot toggling (e.g. for Team-Up 2v2).
     */
    fun selectBotOpponentMode(mode: BotOpponentMode) {
        _botOpponentMode.value = mode
        val activeCount = if (_selectedGameMode.value == LudoGameMode.TEAM_UP) 4 else gameEngine.selectedBoxCount.value
        when (mode) {
            BotOpponentMode.ALL_HUMAN -> {
                setBotPlayers(emptySet())
            }
            BotOpponentMode.VS_BOTS -> {
                setBotPlayers((2..activeCount).toSet())
            }
            BotOpponentMode.CUSTOM -> {
                val valid = _botPlayerIds.value.filter { it in 1..activeCount }.toSet()
                setBotPlayers(valid)
            }
        }
    }

    /**
     * Updates the set of player IDs configured as automated bots.
     * Triggers bot action evaluation if the current turn owner is a bot.
     * Automatically synchronizes [botOpponentMode] based on the provided IDs.
     */
    fun setBotPlayers(playerIds: Set<Int>) {
        val activeCount = if (_selectedGameMode.value == LudoGameMode.TEAM_UP) 4 else gameEngine.selectedBoxCount.value
        val sanitized = playerIds.filter { it in 1..activeCount }.toSet()
        _botPlayerIds.value = sanitized
        _botOpponentMode.value = when {
            sanitized.isEmpty() -> BotOpponentMode.ALL_HUMAN
            sanitized == (2..activeCount).toSet() -> BotOpponentMode.VS_BOTS
            else -> BotOpponentMode.CUSTOM
        }
        checkAndTriggerBotTurn()
    }

    /**
     * Toggles the bot state for a specific player ID (1..activeCount).
     */
    fun toggleBotPlayer(playerId: Int) {
        val activeCount = if (_selectedGameMode.value == LudoGameMode.TEAM_UP) 4 else gameEngine.selectedBoxCount.value
        if (playerId !in 1..activeCount) return

        val newSet = if (_botPlayerIds.value.contains(playerId)) {
            _botPlayerIds.value - playerId
        } else {
            _botPlayerIds.value + playerId
        }
        setBotPlayers(newSet)
    }

    /**
     * Returns true if the player ID is currently configured as a bot.
     */
    fun isBotPlayer(playerId: Int): Boolean {
        return _botPlayerIds.value.contains(playerId)
    }

    private fun checkAndTriggerBotTurn() {
        handleLudoStateForBot(ludoGameEngine.gameState.value)
    }

    private fun cancelPendingBotAction() {
        botActionJob?.cancel()
        botActionJob = null
        currentActionKey = null
    }

    private fun handleLudoStateForBot(state: LudoGameState) {
        if (!state.isGameStarted || state.isGameOver || !isBotPlayer(state.currentPlayerId)) {
            cancelPendingBotAction()
            return
        }

        if (state.turnPhase != TurnPhase.WAITING_FOR_DICE_ROLL &&
            state.turnPhase != TurnPhase.WAITING_FOR_TOKEN_SELECTION
        ) {
            cancelPendingBotAction()
            return
        }

        val newKey = BotActionKey(
            playerId = state.currentPlayerId,
            turnPhase = state.turnPhase,
            diceValue = state.diceValue,
            consecutiveSixCount = state.consecutiveSixCount
        )

        // Deduplication: prevent duplicate launch if already scheduled or in-flight for this exact action key
        if (newKey == currentActionKey && (botActionJob?.isActive == true || isBotActionInProgress)) {
            return
        }

        currentActionKey = newKey
        botActionJob?.cancel()
        botActionJob = viewModelScope.launch {
            try {
                if (botActionDelayMs > 0) {
                    delay(botActionDelayMs)
                }

                // Await transient input lock if active (up to 1000ms safety timeout)
                var waitCount = 0
                while (_uiAux.value.isInputLocked && waitCount < 20) {
                    delay(50)
                    waitCount++
                }
                if (_uiAux.value.isInputLocked) {
                    // Safe cancellation / abort if input lock remains stuck
                    return@launch
                }

                // Post-delay validation: ensure state has not mutated or become stale
                val latestState = ludoGameEngine.gameState.value
                if (!latestState.isGameStarted || latestState.isGameOver) return@launch
                if (latestState.currentPlayerId != newKey.playerId) return@launch
                if (latestState.turnPhase != newKey.turnPhase) return@launch
                if (!isBotPlayer(latestState.currentPlayerId)) return@launch

                when (newKey.turnPhase) {
                    TurnPhase.WAITING_FOR_DICE_ROLL -> {
                        isBotActionInProgress = true
                        try {
                            activateLudoDice()
                        } finally {
                            isBotActionInProgress = false
                        }
                    }

                    TurnPhase.WAITING_FOR_TOKEN_SELECTION -> {
                        if (latestState.diceValue != newKey.diceValue) return@launch
                        val selectedTokenId = LudoBotStrategy.selectToken(latestState)
                        if (selectedTokenId != null && latestState.legalTokenIds.contains(selectedTokenId)) {
                            isBotActionInProgress = true
                            try {
                                moveLudoToken(selectedTokenId)
                            } finally {
                                isBotActionInProgress = false
                            }
                        }
                    }

                    else -> {}
                }
            } catch (e: CancellationException) {
                // Normal coroutine cancellation on turn transition, reset, or rematch
            } finally {
                if (currentActionKey == newKey) {
                    currentActionKey = null
                }
            }
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

    /**
     * Clears all lifetime statistics and match history records.
     * Delegates atomically to [statisticsManager.clearAllStatistics].
     * Does NOT affect active matches, gameplay state, networking, or state persistence.
     */
    fun clearAllStatistics() {
        statisticsManager.clearAllStatistics()
    }

    /**
     * Asynchronously persists the current authoritative [LudoGameState] to disk on Dispatchers.IO.
     * Never interrupts gameplay or drops UI frames if I/O fails.
     */
    fun persistCurrentLudoState() {
        val state = ludoGameEngine.gameState.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                statePersistence.saveState(state)
            } catch (e: Exception) {
                // Failure safety: never crash gameplay
            }
        }
    }

    /**
     * Asynchronously clears the persisted snapshot on Dispatchers.IO.
     */
    fun clearPersistedLudoState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                statePersistence.clearState()
            } catch (e: Exception) {
                // Ignore cleanup error safely
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiHostServer.cleanup()
    }
}
