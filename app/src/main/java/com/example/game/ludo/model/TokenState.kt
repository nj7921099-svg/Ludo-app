package com.example.game.ludo.model

/**
 * State of an individual Ludo token.
 */
enum class TokenState {
    /** Token is inside the player's home base (stepCount = 0) */
    IN_BASE,

    /** Token is traversing the 52-tile common circuit (stepCount in 1..51) */
    ON_BOARD,

    /** Token has entered its player-specific colored home runway (stepCount in 52..56) */
    IN_HOME_LANE,

    /** Token has reached the center Home triangle with an exact roll (stepCount = 57) */
    FINISHED
}
