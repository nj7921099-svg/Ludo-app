package com.example.game.ludo.engine

import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState

/**
 * Pure deterministic resolver for token collision and capture.
 */
object CaptureResolver {

    /**
     * Checks if moving to [targetStep] causes a capture of any opponent tokens.
     *
     * Rules:
     * - Only tokens on the common track (steps 1..51) can capture or be captured.
     * - Safe cells (start squares and star squares) NEVER allow capture; tokens safely stack.
     * - Opponents' home lanes and bases cannot be entered by other players.
     * - If destination is NOT safe and contains one or more opponent tokens, those tokens are captured.
     *
     * @param movingPlayerId Player performing the move.
     * @param movingPlayerColor Canonical color of the moving player.
     * @param targetStep Destination step (0..57) of the moving token.
     * @param allPlayers All players currently in the game.
     * @return List of opponent tokens that are captured (empty if none or if safe cell).
     */
    fun resolveCaptures(
        movingPlayerId: Int,
        movingPlayerColor: LudoColor,
        targetStep: Int,
        allPlayers: List<LudoPlayer>
    ): List<LudoToken> {
        // Captures only occur on common track (steps 1..51)
        if (targetStep !in 1..51) {
            return emptyList()
        }

        val targetTrackIndex = LudoBoardCoordinates.getGlobalTrackIndex(movingPlayerColor, targetStep)
            ?: return emptyList()

        // If destination is a safe cell, no capture occurs!
        if (LudoBoardCoordinates.isTrackIndexSafe(targetTrackIndex)) {
            return emptyList()
        }

        val captured = mutableListOf<LudoToken>()

        for (opponent in allPlayers) {
            // Cannot capture own tokens
            if (opponent.playerId == movingPlayerId) continue

            for (token in opponent.tokens) {
                if (token.state == TokenState.ON_BOARD && token.stepCount in 1..51) {
                    val opponentTrackIndex = LudoBoardCoordinates.getGlobalTrackIndex(opponent.color, token.stepCount)
                    if (opponentTrackIndex == targetTrackIndex) {
                        captured.add(token)
                    }
                }
            }
        }

        return captured
    }
}
