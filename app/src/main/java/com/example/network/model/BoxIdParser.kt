package com.example.network.model

import com.example.network.NetworkConstants
import org.json.JSONObject

/**
 * Result of centralized box ID parsing and normalization.
 */
sealed class BoxIdParseResult {
    /** The message genuinely did not contain any boxId field */
    object NotSpecified : BoxIdParseResult()

    /** Successfully normalized to integer 1..6 */
    data class Valid(val boxNumber: Int, val raw: String) : BoxIdParseResult()

    /** An explicit boxId was provided, but could not be parsed or is out of range */
    data class Invalid(val raw: String, val reason: String) : BoxIdParseResult()
}

/**
 * Centralized, authoritative Box ID Normalizer for Ludo Host & Controller communication.
 *
 * Supported formats:
 * - Controller format: "B1", "B2", "B3", "B4", "B5", "B6" (case-insensitive) -> 1..6
 * - Host / Ludo format: "R1", "R2", "R3", "R4", "R5", "R6" (case-insensitive) -> 1..6
 * - Numeric string format: "1", "2", "3", "4", "5", "6" -> 1..6
 * - Number format: 1, 2, 3, 4, 5, 6 -> 1..6
 */
object BoxIdParser {

    fun parse(raw: Any?): BoxIdParseResult {
        if (raw == null || raw == JSONObject.NULL) {
            return BoxIdParseResult.NotSpecified
        }

        val rawStr = when (raw) {
            is Number -> raw.toInt().toString()
            else -> raw.toString().trim()
        }

        if (rawStr.isBlank()) {
            return BoxIdParseResult.NotSpecified
        }

        val clean = rawStr.uppercase()

        val num = when {
            clean.startsWith("B") -> clean.removePrefix("B").toIntOrNull()
            clean.startsWith("R") -> clean.removePrefix("R").toIntOrNull()
            else -> clean.toIntOrNull()
        }

        if (num == null) {
            return BoxIdParseResult.Invalid(rawStr, "Cannot parse box identifier '$rawStr'. Expected B1-B6 or R1-R6.")
        }

        if (num !in 1..NetworkConstants.MAX_BOXES) {
            return BoxIdParseResult.Invalid(
                rawStr,
                "Box identifier '$rawStr' (box $num) is out of allowed range (1..${NetworkConstants.MAX_BOXES})"
            )
        }

        return BoxIdParseResult.Valid(num, rawStr)
    }

    /**
     * Converts an integer box number (1..6) to its corresponding Controller representation ("B1".."B6").
     */
    fun toControllerBoxId(boxNumber: Int): String = "B$boxNumber"

    /**
     * Converts an integer box number (1..6) to its corresponding Ludo Host representation ("R1".."R6").
     */
    fun toHostBoxId(boxNumber: Int): String = "R$boxNumber"
}
