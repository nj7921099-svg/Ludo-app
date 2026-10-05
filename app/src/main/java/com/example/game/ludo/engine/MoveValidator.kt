package com.example.game.ludo.engine

import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.TokenState

/**
 * Pure deterministic validator for legal token moves in Ludo.
 */
object MoveValidator {

    /**
     * Calculates the set of token IDs (0..3) that can legally move for the active player with the given dice roll.
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
     * Overload taking the current LudoGameState and dice value.
     */
    fun calculateLegalTokens(gameState: LudoGameState, diceValue: Int): Set<Int> {
        val activePlayer = gameState.activePlayer ?: return emptySet()
        return calculateLegalTokens(activePlayer, diceValue)
    }
}
