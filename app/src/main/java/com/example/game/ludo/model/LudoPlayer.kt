package com.example.game.ludo.model

/**
 * Immutable representation of a player in a Ludo game.
 *
 * @param playerId Unique player ID (1..4).
 * @param color Assigned canonical board color.
 * @param name Display label (e.g. "Player 1", "Red").
 * @param tokens List of exactly 4 tokens belonging to this player.
 * @param isFinished True when all 4 tokens have reached FINISHED state.
 * @param finishRank Placement order (1 for 1st place, 2 for 2nd place, etc.), or null if not yet finished.
 */
data class LudoPlayer(
    val playerId: Int,
    val color: LudoColor,
    val name: String = "Player $playerId",
    val tokens: List<LudoToken> = (0..3).map { LudoToken(tokenId = it, playerId = playerId) },
    val isFinished: Boolean = false,
    val finishRank: Int? = null
) {
    init {
        require(tokens.size == 4) { "Every Ludo player must have exactly 4 tokens, found ${tokens.size}" }
    }

    val finishedTokenCount: Int
        get() = tokens.count { it.isFinished }

    val hasWon: Boolean
        get() = finishedTokenCount == 4
}
