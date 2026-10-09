package com.example.game.ludo.engine

import com.example.game.ludo.model.ExtraTurnReason
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoMoveResult
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoTeamId
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
     * Initializes a new Ludo game.
     *
     * In INDIVIDUAL mode:
     * - 2 players: RED (1) and YELLOW (2) (canonical opposite colors across the board)
     * - 3 players: RED (1), GREEN (2), YELLOW (3)
     * - 4 players: RED (1), GREEN (2), YELLOW (3), BLUE (4)
     *
     * In TEAM_UP mode:
     * - Requires strictly playerCount == 4.
     * - Player 1 (RED) and Player 3 (YELLOW) are assigned to TEAM_1.
     * - Player 2 (GREEN) and Player 4 (BLUE) are assigned to TEAM_2.
     * - Any other player count is rejected safely and returns false.
     *
     * @param playerCount Number of players (2..4 for INDIVIDUAL, strictly 4 for TEAM_UP).
     * @param gameMode Game mode (INDIVIDUAL or TEAM_UP). Defaults to INDIVIDUAL.
     * @return true if initialized successfully, false if unsupported count or invalid mode combination.
     */
    @Synchronized
    fun initGame(playerCount: Int, gameMode: LudoGameMode = LudoGameMode.INDIVIDUAL): Boolean {
        if (gameMode == LudoGameMode.TEAM_UP) {
            if (playerCount != 4) {
                return false
            }
        } else {
            if (playerCount !in 2..4) {
                return false
            }
        }

        val colors = when (playerCount) {
            2 -> listOf(LudoColor.RED, LudoColor.YELLOW)
            3 -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW)
            4 -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
            else -> listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
        }

        val players = colors.mapIndexed { index, color ->
            val playerId = index + 1
            val teamId = if (gameMode == LudoGameMode.TEAM_UP) {
                when (playerId) {
                    1, 3 -> LudoTeamId.TEAM_1
                    2, 4 -> LudoTeamId.TEAM_2
                    else -> null
                }
            } else {
                null
            }
            val teamSuffix = if (teamId != null) " [${teamId.name}]" else ""
            LudoPlayer(
                playerId = playerId,
                color = color,
                name = "${color.displayName} (P$playerId)$teamSuffix",
                tokens = (0..3).map { tokenId ->
                    LudoToken(tokenId = tokenId, playerId = playerId, state = TokenState.IN_BASE, stepCount = 0)
                },
                teamId = teamId
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
            lastMoveResult = null,
            gameMode = gameMode,
            winningTeamId = null
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
     * Authoritatively restores a previously persisted [LudoGameState].
     * Recalculates legal moves if currently in WAITING_FOR_TOKEN_SELECTION phase so the game can resume seamlessly.
     *
     * @param restoredState The validated game state to restore.
     */
    @Synchronized
    fun restoreState(restoredState: LudoGameState) {
        val effectiveLegalMoves = if (restoredState.turnPhase == TurnPhase.WAITING_FOR_TOKEN_SELECTION && restoredState.diceValue != null) {
            MoveValidator.calculateLegalTokens(restoredState, restoredState.diceValue)
        } else {
            emptySet()
        }

        _gameState.value = restoredState.copy(
            legalTokenIds = effectiveLegalMoves,
            lastMoveResult = null
        )
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

            // Calculate legal moves for 1st or 2nd six (including Partner Assistance if active player finished)
            val legalTokens = MoveValidator.calculateLegalTokens(current, 6)
            if (legalTokens.isEmpty()) {
                // No legal moves even on a 6: turn passes
                val nextPlayerId = getNextActivePlayerId(current.currentPlayerId, current.players, current.gameMode)
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

        // Dice is 1..5: reset consecutiveSixCount and calculate legal moves
        val legalTokens = MoveValidator.calculateLegalTokens(current, effectiveDice)
        if (legalTokens.isEmpty()) {
            // No legal moves: turn passes directly to next player
            val nextPlayerId = getNextActivePlayerId(current.currentPlayerId, current.players, current.gameMode)
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
     * In TEAM_UP Partner Assistance:
     * If [activePlayer.isFinished] is true, the effective token belongs to the unfinished teammate,
     * while [currentPlayerId] remains the active turn owner.
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
        val dice = current.diceValue ?: return null

        // Determine effective token owner:
        // In TEAM_UP mode, if activePlayer has already finished all 4 tokens, they assist their partner.
        val isPartnerAssistance = (current.gameMode == LudoGameMode.TEAM_UP &&
                activePlayer.isFinished &&
                activePlayer.teamId != null)

        val tokenOwnerPlayer = if (isPartnerAssistance) {
            current.players.find { it.playerId != activePlayer.playerId && it.teamId == activePlayer.teamId } ?: return null
        } else {
            activePlayer
        }

        val token = tokenOwnerPlayer.tokens.find { it.tokenId == tokenId } ?: return null

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

        // 2. Resolve captures on target square (incorporating gameMode friendly-fire protection)
        // Moving token moves on the tokenOwnerPlayer's color track!
        val capturedTokens = CaptureResolver.resolveCaptures(
            movingPlayerId = tokenOwnerPlayer.playerId,
            movingPlayerColor = tokenOwnerPlayer.color,
            targetStep = newStep,
            allPlayers = current.players,
            gameMode = current.gameMode
        )

        // 3. Update token owner's tokens
        val updatedOwnerTokens = tokenOwnerPlayer.tokens.map { t ->
            if (t.tokenId == tokenId) {
                t.copy(state = newState, stepCount = newStep)
            } else {
                t
            }
        }

        // 4. Update all players (handling token update and any captured opponent tokens reset to base)
        val capturedLookup = capturedTokens.associateBy { Pair(it.playerId, it.tokenId) }

        val updatedPlayers = current.players.map { player ->
            if (player.playerId == tokenOwnerPlayer.playerId) {
                player.copy(tokens = updatedOwnerTokens)
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

        // 5. Determine winning state for token owner player and team
        val tokenOwnerAfterMove = updatedPlayers.first { it.playerId == tokenOwnerPlayer.playerId }
        val tokenOwnerJustFinished = !tokenOwnerPlayer.isFinished && tokenOwnerAfterMove.hasWon

        var newWinners = current.winners
        val finalPlayersList = if (tokenOwnerJustFinished) {
            val rank = newWinners.size + 1
            newWinners = newWinners + tokenOwnerPlayer.playerId
            updatedPlayers.map { p ->
                if (p.playerId == tokenOwnerPlayer.playerId) {
                    p.copy(isFinished = true, finishRank = rank)
                } else {
                    p
                }
            }
        } else {
            updatedPlayers
        }

        // Determine Game Over & Winning Team
        val isGameOver: Boolean
        var winningTeam: LudoTeamId? = current.winningTeamId
        val fullyRankedPlayers: List<LudoPlayer>

        if (current.gameMode == LudoGameMode.TEAM_UP) {
            val team1Players = finalPlayersList.filter { it.teamId == LudoTeamId.TEAM_1 }
            val team2Players = finalPlayersList.filter { it.teamId == LudoTeamId.TEAM_2 }

            val isTeam1Won = team1Players.isNotEmpty() && team1Players.all { it.isFinished }
            val isTeam2Won = team2Players.isNotEmpty() && team2Players.all { it.isFinished }

            if (isTeam1Won) {
                isGameOver = true
                winningTeam = LudoTeamId.TEAM_1
            } else if (isTeam2Won) {
                isGameOver = true
                winningTeam = LudoTeamId.TEAM_2
            } else {
                isGameOver = false
            }

            fullyRankedPlayers = finalPlayersList
        } else {
            // INDIVIDUAL mode logic
            val unfinishedCount = finalPlayersList.count { !it.isFinished }
            isGameOver = (unfinishedCount <= 1 && current.playerCount > 1) || (unfinishedCount == 0)

            // If game is over and 1 player remains, assign last rank
            fullyRankedPlayers = if (isGameOver && unfinishedCount == 1) {
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
        }

        // 6. Determine extra turn conditions
        // Extra turn belongs to the active turn owner (currentPlayerId).
        // Precedence: CAPTURED_OPPONENT > FINISHED_TOKEN > ROLLED_SIX
        val hasCaptured = capturedTokens.isNotEmpty()
        val hasFinishedToken = newState == TokenState.FINISHED
        val hasRolledSix = dice == 6

        // Notice: If active player was already finished (Partner Assistance), they do NOT stop playing
        // when a partner token finishes. Only if the ENTIRE MATCH ends (isGameOver) do extra turns stop.
        // In Individual mode (or normal mode), a player who just finished their 4th token does not get an extra turn.
        val activePlayerJustFinished = !isPartnerAssistance && tokenOwnerJustFinished

        val extraTurnGranted: Boolean
        val extraTurnReason: ExtraTurnReason?

        if (isGameOver || activePlayerJustFinished) {
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
            getNextActivePlayerId(current.currentPlayerId, fullyRankedPlayers, current.gameMode)
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
            lastMoveResult = moveResult,
            winningTeamId = winningTeam
        )

        return moveResult
    }

    /**
     * Finds the next player in clockwise order.
     *
     * In INDIVIDUAL mode: skips players where [LudoPlayer.isFinished] is true.
     *
     * In TEAM_UP mode: a finished player is NOT skipped as long as their teammate
     * still has unfinished tokens (Partner Assistance). Only if both players on the team
     * are finished is a player skipped.
     */
    private fun getNextActivePlayerId(
        currentId: Int,
        players: List<LudoPlayer>,
        gameMode: LudoGameMode
    ): Int {
        val total = players.size
        for (i in 1..total) {
            val candidateId = ((currentId - 1 + i) % total) + 1
            val player = players.find { it.playerId == candidateId } ?: continue

            if (gameMode == LudoGameMode.TEAM_UP) {
                // In Team-Up, player is active if they are not finished OR their teammate is not finished
                val teammate = players.find { it.playerId != candidateId && it.teamId == player.teamId }
                val isTeamFinished = player.isFinished && (teammate == null || teammate.isFinished)
                if (!isTeamFinished) {
                    return candidateId
                }
            } else {
                // Individual mode: player must not be finished
                if (!player.isFinished) {
                    return candidateId
                }
            }
        }
        return currentId
    }
}
