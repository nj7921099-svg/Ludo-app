package com.example.game.ludo.engine

import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoTeamId
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
     * - In TEAM_UP mode, friendly fire is prohibited: tokens belonging to a teammate (same teamId)
     *   cannot be captured and instead safely share the cell.
     *
     * @param movingPlayerId Player performing the move.
     * @param movingPlayerColor Canonical color of the moving player.
     * @param targetStep Destination step (0..57) of the moving token.
     * @param allPlayers All players currently in the game.
     * @param gameMode Game mode (INDIVIDUAL or TEAM_UP).
     * @return List of opponent tokens that are captured (empty if none or if safe cell / friendly).
     */
    fun resolveCaptures(
        movingPlayerId: Int,
        movingPlayerColor: LudoColor,
        targetStep: Int,
        allPlayers: List<LudoPlayer>,
        gameMode: LudoGameMode = LudoGameMode.INDIVIDUAL
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

        val movingPlayer = allPlayers.find { it.playerId == movingPlayerId }
        val captured = mutableListOf<LudoToken>()

        for (otherPlayer in allPlayers) {
            // Cannot capture own tokens
            if (otherPlayer.playerId == movingPlayerId) continue

            // In TEAM_UP mode, do not capture teammate's tokens (friendly fire prohibited)
            if (gameMode == LudoGameMode.TEAM_UP &&
                movingPlayer?.teamId != null &&
                otherPlayer.teamId == movingPlayer.teamId
            ) {
                continue
            }

            for (token in otherPlayer.tokens) {
                if (token.state == TokenState.ON_BOARD && token.stepCount in 1..51) {
                    val otherTrackIndex = LudoBoardCoordinates.getGlobalTrackIndex(otherPlayer.color, token.stepCount)
                    if (otherTrackIndex == targetTrackIndex) {
                        captured.add(token)
                    }
                }
            }
        }

        return captured
    }
}
