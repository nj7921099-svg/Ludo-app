package com.example.game.ludo.engine

import com.example.game.ludo.model.TurnPhase
import com.example.game.model.RollSource
import java.security.SecureRandom

/**
 * Result of activating the dice for the active Ludo player.
 *
 * @param playerId Active player who rolled the dice.
 * @param diceValue Authoritative dice value 1..6.
 * @param source [RollSource.REMOTE] if fulfilled by Controller, or [RollSource.LOCAL] if local fallback.
 * @param commandId Original requestId from Controller, or null if local.
 * @param rawBoxId Original wire string from Controller (e.g. "B4"), or null.
 * @param acceptedByEngine True if LudoGameEngine accepted and processed the roll.
 */
data class DiceActivationResult(
    val playerId: Int,
    val diceValue: Int,
    val source: RollSource,
    val commandId: String? = null,
    val rawBoxId: String? = null,
    val acceptedByEngine: Boolean
)

/**
 * Deterministic bridge connecting external controller input (via [PendingNumberStore]),
 * local random fallback, and the authoritative [LudoGameEngine].
 *
 * Rules:
 * - The LudoGameEngine remains the sole turn authority.
 * - The Controller NEVER controls turns.
 * - Controller numbers 1..6 are stored in PendingNumberStore per player ID.
 * - When dice is activated for the active player:
 *     1. Checks PendingNumberStore for that exact player.
 *     2. If pending exists: uses pending value (REMOTE).
 *     3. Otherwise: generates local random 1..6 (LOCAL).
 *     4. Hands dice result to LudoGameEngine.
 *     5. Only after successful engine acceptance: consumes the pending command exactly once.
 */
class LudoDiceBridge(
    val gameEngine: LudoGameEngine,
    val pendingStore: PendingNumberStore = PendingNumberStore()
) {
    private val secureRandom = SecureRandom()

    /**
     * Activates the dice for the currently active Ludo player.
     *
     * @return [DiceActivationResult] if dice was rolled, null if game not started or not waiting for roll.
     */
    @Synchronized
    fun onDiceActivated(): DiceActivationResult? {
        val state = gameEngine.gameState.value
        if (!state.isGameStarted || state.isGameOver) return null
        if (state.turnPhase != TurnPhase.WAITING_FOR_DICE_ROLL) return null

        val currentPlayerId = state.currentPlayerId
        val pending = pendingStore.getPendingCommand(currentPlayerId)

        val diceValue: Int
        val source: RollSource
        val commandId: String?
        val rawBoxId: String?

        if (pending != null) {
            diceValue = pending.number
            source = RollSource.REMOTE
            commandId = pending.commandId
            rawBoxId = pending.rawBoxId
        } else {
            diceValue = secureRandom.nextInt(6) + 1
            source = RollSource.LOCAL
            commandId = null
            rawBoxId = null
        }

        val accepted = gameEngine.onDiceRolled(diceValue)
        if (accepted && pending != null) {
            // ONLY after engine acceptance: consume the pending command!
            pendingStore.consumePendingCommand(currentPlayerId)
        }

        return DiceActivationResult(
            playerId = currentPlayerId,
            diceValue = diceValue,
            source = source,
            commandId = commandId,
            rawBoxId = rawBoxId,
            acceptedByEngine = accepted
        )
    }

    /**
     * Helper to feed external Controller numbers into the PendingNumberStore.
     */
    fun queueControllerCommand(
        commandId: String,
        playerId: Int,
        number: Int,
        rawBoxId: String? = null
    ): Boolean {
        // Only accept for valid player IDs in the current game
        val currentPlayers = gameEngine.gameState.value.playerCount
        if (playerId !in 1..currentPlayers) {
            return false
        }
        return pendingStore.queueCommand(commandId, playerId, number, rawBoxId)
    }
}
