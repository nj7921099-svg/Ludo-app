package com.example.game.ludo.model

/**
 * Game modes supported by the Ludo engine.
 */
enum class LudoGameMode {
    /**
     * Standard free-for-all mode where every player competes individually.
     */
    INDIVIDUAL,

    /**
     * 2v2 cooperative mode where teammates (P1+P3 vs P2+P4) do not capture each other,
     * and victory is achieved when all 8 tokens of a team are finished.
     */
    TEAM_UP
}
