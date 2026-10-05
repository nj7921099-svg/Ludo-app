package com.example.game.ludo.model

/**
 * Immutable representation of a Ludo token.
 *
 * @param tokenId Token index within the player's set (0..3).
 * @param playerId Owner player index (1..playerCount).
 * @param state Current lifecycle state (IN_BASE, ON_BOARD, IN_HOME_LANE, FINISHED).
 * @param stepCount Progression distance:
 *        0 = IN_BASE
 *        1..51 = ON_BOARD (common track)
 *        52..56 = IN_HOME_LANE (player-specific home runway)
 *        57 = FINISHED (reached center home)
 */
data class LudoToken(
    val tokenId: Int,
    val playerId: Int,
    val state: TokenState = TokenState.IN_BASE,
    val stepCount: Int = 0
) {
    init {
        require(tokenId in 0..3) { "tokenId must be 0..3, got $tokenId" }
        require(stepCount in 0..57) { "stepCount must be 0..57, got $stepCount" }
    }

    val isFinished: Boolean
        get() = state == TokenState.FINISHED || stepCount == 57

    val isInBase: Boolean
        get() = state == TokenState.IN_BASE || stepCount == 0

    val isOnTrack: Boolean
        get() = state == TokenState.ON_BOARD && stepCount in 1..51

    val isInHomeLane: Boolean
        get() = state == TokenState.IN_HOME_LANE && stepCount in 52..56
}
