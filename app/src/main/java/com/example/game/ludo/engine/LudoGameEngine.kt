package com.example.game.ludo.engine

import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoMoveResult
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure, deterministic, testable core Ludo game engine.
 *
 * Fully decoupled from UI, network protocols, sounds, and animations.
 * Operates purely on immutable states and verified game commands.
 */
class LudoGameEngine {

    private val secureRandom = SecureRandom()
    private val _gameState = MutableStateFlow(LudoGameState())
    val gameState: StateFlow<LudoGameState> = _gameState.asStateFlow()

    /**
     * Initializes a new Ludo game for 2, 3, or 4 players.
     *
     * Colors assigned:
     * - 2 players: RED (1) and YELLOW (2) (canonical opposite colors across the board)
     * - 3 players: RED (1), GREEN (2), YELLOW (3)
     * - 4 players: RED (1), GREEN (2), YELLOW (3), BLUE (4)
     *
     * @param playerCount Number of players (2..4).
     * @return true if initialized successfully, false if unsupported count.
     */
    @Synchronized
    fun initGame(playerCount: Int): Boolean {
        if (playerCount !in 2..4) {
            return false
        }

        val colors = when (playerCount) {
            2 -> listOf(LudoColor.RED, LudoColor.YELLOW)
            3 -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW)
            4 -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
            else -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
        }

        val players = colors.mapIndexed { index, color ->
            val playerId = index + 1
            LudoPlayer(
                playerId = playerId,
                color = color,
                name = "${color.displayName} (P$playerId)",
                tokens = (0..3).map { tokenId ->
                    LudoToken(tokenId = tokenId, playerId = playerId, state = TokenState.IN_BASE, stepCount = 0)
                }
            )
        }

        _gameState.value = LudoGameState(
            isGameStarted = true,
            playerCount = playerCount,
            players = players,
            currentPlayerId = 1,
            diceValue = null,
            turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL,
            legalTokenIds = emptySet(),
            consecutiveSixCount = 0,
            winners = emptyList(),
            lastMoveResult = null
        )

        return true
    }

    /**
     * Resets the Ludo game state back to initial unstarted setup mode.
     */
    @Synchronized
    fun resetToSetup() {
        _gameState.value = LudoGameState()
    }

    /**
     * Feeds an authoritative dice roll result into the engine.
     *
     * Consecutive Six Rule:
     * - 1st six -> consecutiveSixCount = 1, extra turn granted after moving.
     * - 2nd six -> consecutiveSixCount = 2, extra turn granted after moving.
     * - 3rd consecutive roll (when consecutiveSixCount >= 2):
     *     A. If result = 1..5: Accepted normally, consecutiveSixCount resets to 0.
     *     B. If result = 6: Third-six rule is applied! A 3rd consecutive 6 cannot appear as a
     *        normal playable result. The engine safely obtains a 1..5 result in the background
     *        for the SAME player without transferring the turn prematurely.
     *
     * @param diceValue Integer strictly 1..6.
     * @param thirdRollFallback Optional fallback strictly 1..5 to use if a 3rd consecutive 6 is rolled (for deterministic testing).
     * @return true if roll was accepted and processed, false if not waiting for roll or invalid value.
     */
    @Synchronized
    fun onDiceRolled(diceValue: Int, thirdRollFallback: Int? = null): Boolean {
        val current = _gameState.value
        if (!current.isGameStarted || current.isGameOver) return false
        if (current.turnPhase != TurnPhase.WAITING_FOR_DICE_ROLL) return false
        if (diceValue !in 1..6) return false

        val activePlayer = current.activePlayer ?: return false

        // Apply third-six rule if player has already rolled two consecutive 6s
        val effectiveDice = if (current.consecutiveSixCount >= 2 && diceValue == 6) {
            // Third consecutive 6: safely obtain a valid 1..5 in the background for the SAME player
            thirdRollFallback?.takeIf { it in 1..5 } ?: (secureRandom.nextInt(5) + 1)
        } else {
            diceValue
        }

        if (effectiveDice == 6) {
            // Only 1st or 2nd consecutive six can reach here
            val newSixCount = current.consecutiveSixCount + 1

            // Calculate legal moves for 1st or 2nd six
            val legalTokens = MoveValidator.calculateLegalTokens(activePlayer, 6)
            if (legalTokens.isEmpty()) {
                // No legal moves even on a 6: turn passes
                val nextPlayerId = getNextActivePlayerId(current.currentPlayerId, current.players)
                _gameState.value = current.copy(
                    diceValue = 6,
                    consecutiveSixCount = 0,
                    currentPlayerId = nextPlayerId,
                    turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL,
                    legalTokenIds = emptySet()
                )
                return true
            }

            _gameState.value = current.copy(
                diceValue = 6,
                consecutiveSixCount = newSixCount,
                turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
                legalTokenIds = legalTokens
            )
            return true
        }

        // Non-6 roll (1..5): either normal turn or 3rd consecutive roll result.
        // Resets consecutive six count to 0.
        val legalTokens = MoveValidator.calculateLegalTokens(activePlayer, effectiveDice)
        if (legalTokens.isEmpty()) {
            // No legal moves: turn passes immediately to next player
            val nextPlayerId = getNextActivePlayerId(current.currentPlayerId, current.players)
            _gameState.value = current.copy(
                diceValue = effectiveDice,
                consecutiveSixCount = 0,
                currentPlayerId = nextPlayerId,
                turnPhase = TurnPhase.WAITING_FOR_DICE_ROLL,
                legalTokenIds = emptySet()
            )
            return true
        }

        _gameState.value = current.copy(
            diceValue = effectiveDice,
            consecutiveSixCount = 0,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION,
            legalTokenIds = legalTokens
        )
        return true
    }

    /**
     * Executes the movement of a legal token for the active player.
     *
     * @param tokenId Index of the token to move (0..3).
     * @return [LudoMoveResult] if move was valid and executed, null otherwise.
     */
    @Synchronized
    fun moveToken(tokenId: Int): LudoMoveResult? {
        val current = _gameState.value
        if (!current.isGameStarted || current.isGameOver) return null
        if (current.turnPhase != TurnPhase.WAITING_FOR_TOKEN_SELECTION) return null
        if (tokenId !in current.legalTokenIds) return null

        val activePlayer = current.activePlayer ?: return null
        val token = activePlayer.tokens.find { it.tokenId == tokenId } ?: return null
        val dice = current.diceValue ?: return null

        // 1. Calculate new step and traversed path
        val oldStep = token.stepCount
        val newStep: Int
        val traversed: List<Int>
        val newState: TokenState

        if (token.state == TokenState.IN_BASE) {
            // Moving out of base on 6 onto start square (step 1)
            newStep = 1
            traversed = listOf(1)
            newState = TokenState.ON_BOARD
        } else {
            newStep = token.stepCount + dice
            require(newStep <= 57) { "Move exceeds finish step 57: $newStep" }
            traversed = (oldStep + 1..newStep).toList()
            newState = when {
                newStep == 57 -> TokenState.FINISHED
                newStep >= 52 -> TokenState.IN_HOME_LANE
                else -> TokenState.ON_BOARD
            }
        }

        // 2. Resolve captures on target square
        val capturedTokens = CaptureResolver.resolveCaptures(
            movingPlayerId = activePlayer.playerId,
            movingPlayerColor = activePlayer.color,
            targetStep = newStep,
            allPlayers = current.players
        )

        // 3. Update moving player's tokens
        val updatedMovingTokens = activePlayer.tokens.map { t ->
            if (t.tokenId == tokenId) {
                t.copy(state = newState, stepCount = newStep)
            } else {
                t
            }
        }

        // 4. Update all players (handling any captured opponent tokens reset to base)
        val capturedLookup = capturedTokens.associateBy { Pair(it.playerId, it.tokenId) }

        val updatedPlayers = current.players.map { player ->
            if (player.playerId == activePlayer.playerId) {
                player.copy(tokens = updatedMovingTokens)
            } else {
                val updatedOpponentTokens = player.tokens.map { oppToken ->
                    if (capturedLookup.containsKey(Pair(oppToken.playerId, oppToken.tokenId))) {
                        oppToken.copy(state = TokenState.IN_BASE, stepCount = 0)
                    } else {
                        oppToken
                    }
                }
                player.copy(tokens = updatedOpponentTokens)
            }
        }

        // 5. Determine winning state for moving player
        val movingPlayerAfterMove = updatedPlayers.first { it.playerId == activePlayer.playerId }
        val playerJustFinished = !activePlayer.isFinished && movingPlayerAfterMove.hasWon

        var newWinners = current.winners
        val finalPlayersList = if (playerJustFinished) {
            val rank = newWinners.size + 1
            newWinners = newWinners + activePlayer.playerId
            updatedPlayers.map { p ->
                if (p.playerId == activePlayer.playerId) {
                    p.copy(isFinished = true, finishRank = rank)
                } else {
                    p
                }
            }
        } else {
            updatedPlayers
        }

        // Check if game is over (all or all but 1 player finished)
        val unfinishedCount = finalPlayersList.count { !it.isFinished }
        val isGameOver = (unfinishedCount <= 1 && current.playerCount > 1) || (unfinishedCount == 0)

        // If game is over and 1 player remains, assign last rank
        val fullyRankedPlayers = if (isGameOver && unfinishedCount == 1) {
            val lastPlayer = finalPlayersList.first { !it.isFinished }
            val finalWinners = newWinners + lastPlayer.playerId
            newWinners = finalWinners
            finalPlayersList.map { p ->
                if (p.playerId == lastPlayer.playerId) {
                    p.copy(isFinished = true, finishRank = finalWinners.size)
                } else {
                    p
                }
            }
        } else {
            finalPlayersList
        }

        // 6. Determine extra turn conditions
        // Precedence: CAPTURED_OPPONENT > FINISHED_TOKEN > ROLLED_SIX
        val hasCaptured = capturedTokens.isNotEmpty()
        val hasFinishedToken = newState == TokenState.FINISHED
        val hasRolledSix = dice == 6

        val extraTurnGranted: Boolean
        val extraTurnReason: ExtraTurnReason?

        if (isGameOver || playerJustFinished) {
            // A player who just finished their last token does not take an extra turn
            extraTurnGranted = false
            extraTurnReason = null
        } else if (hasCaptured) {
            extraTurnGranted = true
            extraTurnReason = ExtraTurnReason.CAPTURED_OPPONENT
        } else if (hasFinishedToken) {
            extraTurnGranted = true
            extraTurnReason = ExtraTurnReason.FINISHED_TOKEN
        } else if (hasRolledSix && current.consecutiveSixCount < 3) {
            extraTurnGranted = true
            extraTurnReason = ExtraTurnReason.ROLLED_SIX
        } else {
            extraTurnGranted = false
            extraTurnReason = null
        }

        // 7. Determine next active player
        val nextPlayerId = if (isGameOver) {
            current.currentPlayerId
        } else if (extraTurnGranted) {
            current.currentPlayerId
        } else {
            getNextActivePlayerId(current.currentPlayerId, fullyRankedPlayers)
        }

        val moveResult = LudoMoveResult(
            playerId = activePlayer.playerId,
            tokenId = tokenId,
            diceValue = dice,
            fromStep = oldStep,
            toStep = newStep,
            traversedSteps = traversed,
            capturedTokens = capturedTokens,
            enteredHome = oldStep < 52 && newStep >= 52,
            finishedToken = hasFinishedToken,
            extraTurnGranted = extraTurnGranted,
            extraTurnReason = extraTurnReason,
            isGameOver = isGameOver,
            nextPlayerId = nextPlayerId
        )

        // 8. Update game state
        _gameState.value = current.copy(
            players = fullyRankedPlayers,
            currentPlayerId = nextPlayerId,
            diceValue = null,
            turnPhase = if (isGameOver) TurnPhase.GAME_OVER else TurnPhase.WAITING_FOR_DICE_ROLL,
            legalTokenIds = emptySet(),
            consecutiveSixCount = if (extraTurnGranted && hasRolledSix) current.consecutiveSixCount else 0,
            winners = newWinners,
            lastMoveResult = moveResult
        )

        return moveResult
    }

    /**
     * Finds the next player in clockwise order who has NOT finished the game.
     */
    private fun getNextActivePlayerId(currentId: Int, players: List<LudoPlayer>): Int {
        val total = players.size
        for (i in 1..total) {
            val candidateId = ((currentId - 1 + i) % total) + 1
            val player = players.find { it.playerId == candidateId }
            if (player != null && !player.isFinished) {
                return candidateId
            }
        }
        return currentId
    }
}
