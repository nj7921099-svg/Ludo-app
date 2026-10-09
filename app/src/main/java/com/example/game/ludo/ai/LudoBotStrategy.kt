package com.example.game.ludo.ai

import com.example.game.ludo.engine.CaptureResolver
import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase

/**
 * Pure, deterministic AI strategy for selecting optimal legal token moves in Ludo.
 *
 * Requirements & Constraints:
 * - Operates purely on immutable [LudoGameState] snapshots.
 * - Selects exclusively from authoritative [LudoGameState.legalTokenIds].
 * - Never mutates game state, changes active player, rolls dice, or directly executes moves.
 * - Adheres strictly to game rules, safe cells, teammate immunity, and Team-Up Partner Assistance.
 *
 * Deterministic Heuristic Priorities:
 * 1. Finish token: Reaches center home finish (step 57).
 * 2. Capture opponent: Land on common track square capturing one or more opponent tokens.
 * 3. Leave base: Move out of base on roll of 6 to start cell (step 1).
 * 4. Safe destination: Land on safe cell (start square, star square, or protected home lane/finish).
 * 5. Useful forward progress: Token closest to finish (greatest target step count).
 * 6. Deterministic tie-breaker: Smallest tokenId (0..3).
 */
object LudoBotStrategy {

    /**
     * Internal evaluation metrics container for comparing candidate token moves.
     */
    internal data class TokenMoveEvaluation(
        val tokenId: Int,
        val isFinishing: Boolean,
        val captureCount: Int,
        val isLeavingBase: Boolean,
        val isSafeDestination: Boolean,
        val targetStep: Int
    ) : Comparable<TokenMoveEvaluation> {
        override fun compareTo(other: TokenMoveEvaluation): Int {
            // Priority 1: Finish token (reaches center home finish, step 57)
            if (this.isFinishing != other.isFinishing) {
                return this.isFinishing.compareTo(other.isFinishing)
            }

            // Priority 2: Capture opponent (hostile capture on common track)
            if (this.captureCount != other.captureCount) {
                return this.captureCount.compareTo(other.captureCount)
            }

            // Priority 3: Leave base (step 0 -> 1 on roll of 6)
            if (this.isLeavingBase != other.isLeavingBase) {
                return this.isLeavingBase.compareTo(other.isLeavingBase)
            }

            // Priority 4: Safe destination (landing on star, start, or home lane)
            if (this.isSafeDestination != other.isSafeDestination) {
                return this.isSafeDestination.compareTo(other.isSafeDestination)
            }

            // Priority 5: Useful forward progress (furthest along towards home)
            if (this.targetStep != other.targetStep) {
                return this.targetStep.compareTo(other.targetStep)
            }

            // Priority 6: Deterministic tie-breaker (lower tokenId preferred: 0 over 1, etc.)
            return other.tokenId.compareTo(this.tokenId)
        }
    }

    /**
     * Evaluates legal candidates and selects the optimal token ID for the active turn in [gameState].
     *
     * @param gameState Current authoritative game state snapshot.
     * @return Selected legal token ID (0..3), or null if no legal moves exist or state is invalid.
     */
    fun selectToken(gameState: LudoGameState): Int? {
        if (!gameState.isGameStarted || gameState.isGameOver) return null
        if (gameState.turnPhase != TurnPhase.WAITING_FOR_TOKEN_SELECTION) return null
        val diceValue = gameState.diceValue ?: return null
        val activePlayer = gameState.activePlayer ?: return null
        val legalTokenIds = gameState.legalTokenIds
        if (legalTokenIds.isEmpty()) return null

        // Determine effective token owner:
        // In TEAM_UP mode, if activePlayer has already finished all 4 tokens, they assist their partner.
        val isPartnerAssistance = (gameState.gameMode == LudoGameMode.TEAM_UP &&
                activePlayer.isFinished &&
                activePlayer.teamId != null)

        val tokenOwnerPlayer = if (isPartnerAssistance) {
            gameState.players.find { it.playerId != activePlayer.playerId && it.teamId == activePlayer.teamId } ?: return null
        } else {
            activePlayer
        }

        // Only evaluate candidates that are present in the authoritative legalTokenIds set
        val candidateTokens = legalTokenIds.mapNotNull { candidateId ->
            tokenOwnerPlayer.tokens.find { it.tokenId == candidateId }
        }

        if (candidateTokens.isEmpty()) return null

        val evaluations = candidateTokens.map { token ->
            val targetStep = if (token.state == TokenState.IN_BASE) 1 else token.stepCount + diceValue
            val isFinishing = (targetStep == 57)
            val isLeavingBase = (token.state == TokenState.IN_BASE)

            // Resolve captures using tokenOwnerPlayer's color track and coordinates
            val capturedTokens = CaptureResolver.resolveCaptures(
                movingPlayerId = tokenOwnerPlayer.playerId,
                movingPlayerColor = tokenOwnerPlayer.color,
                targetStep = targetStep,
                allPlayers = gameState.players,
                gameMode = gameState.gameMode
            )
            val captureCount = capturedTokens.size

            // Safe destination: steps 52..57 are home runway/center (immune to opponents),
            // or common track star/start squares (steps 1..51)
            val isSafeDestination = if (targetStep >= 52) {
                true
            } else {
                val trackIndex = LudoBoardCoordinates.getGlobalTrackIndex(tokenOwnerPlayer.color, targetStep)
                trackIndex != null && LudoBoardCoordinates.isTrackIndexSafe(trackIndex)
            }

            TokenMoveEvaluation(
                tokenId = token.tokenId,
                isFinishing = isFinishing,
                captureCount = captureCount,
                isLeavingBase = isLeavingBase,
                isSafeDestination = isSafeDestination,
                targetStep = targetStep
            )
        }

        return evaluations.maxOrNull()?.tokenId
    }

    /**
     * Alias for [selectToken] for expressive readability.
     */
    fun findBestToken(gameState: LudoGameState): Int? = selectToken(gameState)
}
