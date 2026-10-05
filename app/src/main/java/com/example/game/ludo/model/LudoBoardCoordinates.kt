package com.example.game.ludo.model

/**
 * 2D grid coordinates on the logical 15x15 Ludo board.
 * @param col Column index from left (0) to right (14).
 * @param row Row index from top (0) to bottom (14).
 */
data class BoardCoordinate(val col: Int, val row: Int) {
    init {
        require(col in 0..14) { "col must be in 0..14, got $col" }
        require(row in 0..14) { "row must be in 0..14, got $row" }
    }
}

/**
 * Authoritative, mathematically verified 15x15 Ludo board path and coordinate topology.
 *
 * All coordinates are logical grid indexes (0..14).
 * This completely decouples game mechanics from screen pixels and resolution.
 */
object LudoBoardCoordinates {

    /**
     * The 52 unique cells forming the common circular track around the 15x15 board.
     * Index 0 corresponds to Red's start position (col=1, row=6).
     */
    val commonTrack: List<BoardCoordinate> = listOf(
        // Left arm top row (0..4)
        BoardCoordinate(col = 1, row = 6),  // 0: Red Start [SAFE]
        BoardCoordinate(col = 2, row = 6),  // 1
        BoardCoordinate(col = 3, row = 6),  // 2
        BoardCoordinate(col = 4, row = 6),  // 3
        BoardCoordinate(col = 5, row = 6),  // 4

        // Top arm left column (5..10)
        BoardCoordinate(col = 6, row = 5),  // 5
        BoardCoordinate(col = 6, row = 4),  // 6
        BoardCoordinate(col = 6, row = 3),  // 7
        BoardCoordinate(col = 6, row = 2),  // 8: Star [SAFE]
        BoardCoordinate(col = 6, row = 1),  // 9
        BoardCoordinate(col = 6, row = 0),  // 10

        // Top arm tip turn (11)
        BoardCoordinate(col = 7, row = 0),  // 11

        // Top arm right column (12..17)
        BoardCoordinate(col = 8, row = 0),  // 12
        BoardCoordinate(col = 8, row = 1),  // 13: Green Start [SAFE]
        BoardCoordinate(col = 8, row = 2),  // 14
        BoardCoordinate(col = 8, row = 3),  // 15
        BoardCoordinate(col = 8, row = 4),  // 16
        BoardCoordinate(col = 8, row = 5),  // 17

        // Right arm top row (18..23)
        BoardCoordinate(col = 9, row = 6),   // 18
        BoardCoordinate(col = 10, row = 6),  // 19
        BoardCoordinate(col = 11, row = 6),  // 20
        BoardCoordinate(col = 12, row = 6),  // 21: Star [SAFE]
        BoardCoordinate(col = 13, row = 6),  // 22
        BoardCoordinate(col = 14, row = 6),  // 23

        // Right arm tip turn (24)
        BoardCoordinate(col = 14, row = 7),  // 24

        // Right arm bottom row (25..30)
        BoardCoordinate(col = 14, row = 8),  // 25
        BoardCoordinate(col = 13, row = 8),  // 26: Yellow Start [SAFE]
        BoardCoordinate(col = 12, row = 8),  // 27
        BoardCoordinate(col = 11, row = 8),  // 28
        BoardCoordinate(col = 10, row = 8),  // 29
        BoardCoordinate(col = 9, row = 8),   // 30

        // Bottom arm right column (31..36)
        BoardCoordinate(col = 8, row = 9),   // 31
        BoardCoordinate(col = 8, row = 10),  // 32
        BoardCoordinate(col = 8, row = 11),  // 33
        BoardCoordinate(col = 8, row = 12),  // 34: Star [SAFE]
        BoardCoordinate(col = 8, row = 13),  // 35
        BoardCoordinate(col = 8, row = 14),  // 36

        // Bottom arm tip turn (37)
        BoardCoordinate(col = 7, row = 14),  // 37

        // Bottom arm left column (38..43)
        BoardCoordinate(col = 6, row = 14),  // 38
        BoardCoordinate(col = 6, row = 13),  // 39: Blue Start [SAFE]
        BoardCoordinate(col = 6, row = 12),  // 40
        BoardCoordinate(col = 6, row = 11),  // 41
        BoardCoordinate(col = 6, row = 10),  // 42
        BoardCoordinate(col = 6, row = 9),   // 43

        // Left arm bottom row (44..49)
        BoardCoordinate(col = 5, row = 8),   // 44
        BoardCoordinate(col = 4, row = 8),   // 45
        BoardCoordinate(col = 3, row = 8),   // 46
        BoardCoordinate(col = 2, row = 8),   // 47: Star [SAFE]
        BoardCoordinate(col = 1, row = 8),   // 48
        BoardCoordinate(col = 0, row = 8),   // 49

        // Left arm tip turn (50..51)
        BoardCoordinate(col = 0, row = 7),   // 50
        BoardCoordinate(col = 0, row = 6)    // 51: Entry to Red Home Lane
    )

    /**
     * Indices on the common track (0..51) that are SAFE cells.
     * Contains 4 start cells (0, 13, 26, 39) and 4 star cells (8, 21, 34, 47).
     */
    val safeTrackIndices: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    /**
     * Start index on the common track for each color.
     */
    fun getStartTrackIndex(color: LudoColor): Int = when (color) {
        LudoColor.RED -> 0
        LudoColor.GREEN -> 13
        LudoColor.YELLOW -> 26
        LudoColor.BLUE -> 39
    }

    /**
     * Home runway lanes (5 tiles each, steps 52..56) leading to the center triangle.
     */
    val homeLanes: Map<LudoColor, List<BoardCoordinate>> = mapOf(
        LudoColor.RED to listOf(
            BoardCoordinate(col = 1, row = 7),
            BoardCoordinate(col = 2, row = 7),
            BoardCoordinate(col = 3, row = 7),
            BoardCoordinate(col = 4, row = 7),
            BoardCoordinate(col = 5, row = 7)
        ),
        LudoColor.GREEN to listOf(
            BoardCoordinate(col = 7, row = 1),
            BoardCoordinate(col = 7, row = 2),
            BoardCoordinate(col = 7, row = 3),
            BoardCoordinate(col = 7, row = 4),
            BoardCoordinate(col = 7, row = 5)
        ),
        LudoColor.YELLOW to listOf(
            BoardCoordinate(col = 13, row = 7),
            BoardCoordinate(col = 12, row = 7),
            BoardCoordinate(col = 11, row = 7),
            BoardCoordinate(col = 10, row = 7),
            BoardCoordinate(col = 9, row = 7)
        ),
        LudoColor.BLUE to listOf(
            BoardCoordinate(col = 7, row = 13),
            BoardCoordinate(col = 7, row = 12),
            BoardCoordinate(col = 7, row = 11),
            BoardCoordinate(col = 7, row = 10),
            BoardCoordinate(col = 7, row = 9)
        )
    )

    /**
     * Center Home finish triangle coordinate for each color (step 57).
     */
    val homeCenters: Map<LudoColor, BoardCoordinate> = mapOf(
        LudoColor.RED to BoardCoordinate(col = 6, row = 7),
        LudoColor.GREEN to BoardCoordinate(col = 7, row = 6),
        LudoColor.YELLOW to BoardCoordinate(col = 8, row = 7),
        LudoColor.BLUE to BoardCoordinate(col = 7, row = 8)
    )

    /**
     * Four base circle coordinates for each color (step 0).
     */
    val baseSlots: Map<LudoColor, List<BoardCoordinate>> = mapOf(
        LudoColor.RED to listOf(
            BoardCoordinate(col = 1, row = 1),
            BoardCoordinate(col = 3, row = 1),
            BoardCoordinate(col = 1, row = 3),
            BoardCoordinate(col = 3, row = 3)
        ),
        LudoColor.GREEN to listOf(
            BoardCoordinate(col = 11, row = 1),
            BoardCoordinate(col = 13, row = 1),
            BoardCoordinate(col = 11, row = 3),
            BoardCoordinate(col = 13, row = 3)
        ),
        LudoColor.YELLOW to listOf(
            BoardCoordinate(col = 11, row = 11),
            BoardCoordinate(col = 13, row = 11),
            BoardCoordinate(col = 11, row = 13),
            BoardCoordinate(col = 13, row = 13)
        ),
        LudoColor.BLUE to listOf(
            BoardCoordinate(col = 1, row = 11),
            BoardCoordinate(col = 3, row = 11),
            BoardCoordinate(col = 1, row = 13),
            BoardCoordinate(col = 3, row = 13)
        )
    )

    /**
     * Resolves the 15x15 grid coordinate for a token at a given step.
     */
    fun getCoordinateForToken(color: LudoColor, tokenId: Int, stepCount: Int): BoardCoordinate {
        return when (stepCount) {
            0 -> {
                val slots = baseSlots[color] ?: error("Unknown base for $color")
                slots[tokenId.coerceIn(0, 3)]
            }
            in 1..51 -> {
                val start = getStartTrackIndex(color)
                val trackIndex = (start + stepCount - 1) % commonTrack.size
                commonTrack[trackIndex]
            }
            in 52..56 -> {
                val lane = homeLanes[color] ?: error("Unknown lane for $color")
                lane[stepCount - 52]
            }
            57 -> {
                homeCenters[color] ?: BoardCoordinate(7, 7)
            }
            else -> error("Invalid stepCount $stepCount")
        }
    }

    /**
     * Returns true if the common track index is a safe square.
     */
    fun isTrackIndexSafe(trackIndex: Int): Boolean {
        return safeTrackIndices.contains(trackIndex)
    }

    /**
     * Returns global common track index for a token on the common track (steps 1..51).
     */
    fun getGlobalTrackIndex(color: LudoColor, stepCount: Int): Int? {
        if (stepCount !in 1..51) return null
        val start = getStartTrackIndex(color)
        return (start + stepCount - 1) % commonTrack.size
    }
}
