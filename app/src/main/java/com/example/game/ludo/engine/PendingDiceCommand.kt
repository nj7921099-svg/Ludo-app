package com.example.game.ludo.engine

/**
 * Box/player specific pending dice command received from Controller over Wi-Fi/TCP.
 *
 * @param commandId Unique request ID for deduplication and exactly-once consumption.
 * @param playerId Target canonical player index (1..4, or 1..6).
 * @param number Authoritative dice number strictly 1..6.
 * @param rawBoxId Original wire representation from Controller (e.g. "B4", "B2").
 * @param timestamp Time when command was received and stored.
 */
data class PendingDiceCommand(
    val commandId: String,
    val playerId: Int,
    val number: Int,
    val rawBoxId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
