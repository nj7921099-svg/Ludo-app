package com.example.game.ludo.model

/**
 * High-level phase of the active turn in a Ludo game.
 */
enum class TurnPhase {
    /** Waiting for the active player to roll or activate the dice */
    WAITING_FOR_DICE_ROLL,

    /** Dice has rolled, waiting for player to tap a legal token */
    WAITING_FOR_TOKEN_SELECTION,

    /** Move has completed and turn transition is being processed */
    TURN_RESOLVED,

    /** Game is concluded with winners ranked */
    GAME_OVER
}
