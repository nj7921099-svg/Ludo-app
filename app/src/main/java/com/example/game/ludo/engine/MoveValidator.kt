package com.example.game.ludo.engine

import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.TokenState

/**
 * Pure deterministic validator for legal token moves in Ludo.
 */
object MoveValidator {

    /**
     * Calculates the set of token IDs (0..3) that can legally move for the given player with the given dice roll.
     *
     * Rules:
     * - IN_BASE: Legal ONLY if dice == 6
     * - ON_BOARD / IN_HOME_LANE: Legal if stepCount + dice <= 57 (exact finish, no overshooting)
     * - FINISHED: Never legal
     */
    fun calculateLegalTokens(player: LudoPlayer, diceValue: Int): Set<Int> {
        require(diceValue in 1..6) { "Dice value must be in 1..6, got $diceValue" }

        val legalTokens = mutableSetOf<Int>()

        for (token in player.tokens) {
            when (token.state) {
                TokenState.IN_BASE -> {
                    if (diceValue == 6) {
                        legalTokens.add(token.tokenId)
                    }
                }
                TokenState.ON_BOARD, TokenState.IN_HOME_LANE -> {
                    if (token.stepCount + diceValue <= 57) {
                        legalTokens.add(token.tokenId)
                    }
                }
                TokenState.FINISHED -> {
                    // Cannot move finished tokens
                }
            }
        }

        return legalTokens
    }

    /**
     * Calculates the legal token IDs for the active turn in [gameState].
     *
     * In INDIVIDUAL mode (or when active player has not finished all 4 tokens in TEAM_UP mode):
     * Evaluates active player's own tokens.
     *
     * In TEAM_UP mode under Partner Assistance:
     * When the active player has already finished all 4 of their own tokens, but their teammate
     * has unfinished tokens, the active player assists their partner.
     * Evaluates the teammate's tokens with the active player's dice roll.
     */
    fun calculateLegalTokens(gameState: LudoGameState, diceValue: Int): Set<Int> {
        val activePlayer = gameState.activePlayer ?: return emptySet()

        if (gameState.gameMode == LudoGameMode.TEAM_UP && activePlayer.isFinished && activePlayer.teamId != null) {
            val teammate = gameState.players.find { it.playerId != activePlayer.playerId && it.teamId == activePlayer.teamId }
            if (teammate != null && !teammate.isFinished) {
                return calculateLegalTokens(teammate, diceValue)
            }
        }

        return calculateLegalTokens(activePlayer, diceValue)
    }
}
