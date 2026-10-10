package com.example.ui.components.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.game.ludo.model.BoardCoordinate
import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoColor

/**
 * Pure Canvas rendering of the classic reference 15x15 Ludo board.
 * Faithfully reproduces the attached classic Ludo board design:
 * - Green (top-left), Yellow (top-right), Red (bottom-left), Blue (bottom-right)
 * - 6x6 colored corner bases with crisp white square inner yards and four circular colored token pockets
 * - 52 unique common track cells with white backgrounds and crisp dark grid borders
 * - 4 color-specific home runways leading straight into the center
 * - Entry arrows pointing in the direction of path traversal
 * - Star outlines on the safe cells
 * - 4 triangular center zones forming the home base
 */
@Composable
fun LudoBoardCanvas(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val boardWidth = size.width
        val cellSize = boardWidth / 15f
        val gridStrokeWidth = (boardWidth / 480f).coerceAtLeast(1.2f)

        // 1. Board background (Clean pure white)
        drawRect(
            color = Color.White,
            size = size
        )

        // 2. Draw 4 Corner Home Quadrants (6x6 cells each):
        // Top-Left: Red (P1 base)
        drawClassicCornerBase(
            color = LudoColor.RED,
            startCol = 0, startRow = 0,
            cellSize = cellSize,
            gridStrokeWidth = gridStrokeWidth
        )
        // Top-Right: Green (P2 base)
        drawClassicCornerBase(
            color = LudoColor.GREEN,
            startCol = 9, startRow = 0,
            cellSize = cellSize,
            gridStrokeWidth = gridStrokeWidth
        )
        // Bottom-Right: Yellow (P3 base)
        drawClassicCornerBase(
            color = LudoColor.YELLOW,
            startCol = 9, startRow = 9,
            cellSize = cellSize,
            gridStrokeWidth = gridStrokeWidth
        )
        // Bottom-Left: Blue (P4 base)
        drawClassicCornerBase(
            color = LudoColor.BLUE,
            startCol = 0, startRow = 9,
            cellSize = cellSize,
            gridStrokeWidth = gridStrokeWidth
        )

        // 3. Draw 52 Common Track Cells
        LudoBoardCoordinates.commonTrack.forEachIndexed { trackIndex, coord ->
            val isSafe = LudoBoardCoordinates.isTrackIndexSafe(trackIndex)
            val isStart = trackIndex in setOf(0, 13, 26, 39)
            val startColor = when (trackIndex) {
                0 -> LudoColor.RED
                13 -> LudoColor.GREEN
                26 -> LudoColor.YELLOW
                39 -> LudoColor.BLUE
                else -> null
            }

            drawClassicTrackCell(
                coord = coord,
                cellSize = cellSize,
                gridStrokeWidth = gridStrokeWidth,
                isSafe = isSafe,
                startColor = startColor,
                isStartCell = isStart
            )
        }

        // 4. Draw 4 Colored Home Runways (5 cells each) leading into the center triangle
        LudoColor.values().forEach { color ->
            val lane = LudoBoardCoordinates.homeLanes[color] ?: return@forEach
            val laneColor = LudoThemeColors.getPrimaryColor(color)

            lane.forEach { coord ->
                val x = coord.col * cellSize
                val y = coord.row * cellSize

                // Filled colored home lane square
                drawRect(
                    color = laneColor,
                    topLeft = Offset(x, y),
                    size = Size(cellSize, cellSize)
                )

                // Grid line border
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x, y),
                    size = Size(cellSize, cellSize),
                    style = Stroke(width = gridStrokeWidth)
                )
            }
        }

        // 5. Draw 4 Entry Arrows on the track (Green pointing down, Yellow pointing left, Blue pointing up, Red pointing right)
        drawEntryArrows(cellSize = cellSize, gridStrokeWidth = gridStrokeWidth)

        // 6. Draw Center 3x3 Triangular Home Finish Area
        drawClassicCenterHome(cellSize = cellSize, gridStrokeWidth = gridStrokeWidth)

        // 7. Outer board border
        drawRect(
            color = Color.Black,
            topLeft = Offset.Zero,
            size = size,
            style = Stroke(width = gridStrokeWidth * 2.2f)
        )
    }
}

/**
 * Draws a classic 6x6 corner quadrant with solid colored background, white square inner yard,
 * and 4 colored circular token bases.
 */
private fun DrawScope.drawClassicCornerBase(
    color: LudoColor,
    startCol: Int,
    startRow: Int,
    cellSize: Float,
    gridStrokeWidth: Float
) {
    val x = startCol * cellSize
    val y = startRow * cellSize
    val quadrantSize = 6 * cellSize
    val primaryColor = LudoThemeColors.getPrimaryColor(color)

    // Solid colored 6x6 quadrant background
    drawRect(
        color = primaryColor,
        topLeft = Offset(x, y),
        size = Size(quadrantSize, quadrantSize)
    )

    // Outer quadrant border line
    drawRect(
        color = Color.Black,
        topLeft = Offset(x, y),
        size = Size(quadrantSize, quadrantSize),
        style = Stroke(width = gridStrokeWidth * 1.5f)
    )

    // Inner pure white square yard (leaves 1 cell border all around, spanning 4x4 cells)
    val yardMargin = cellSize * 1.05f
    val yardSize = quadrantSize - yardMargin * 2f
    drawRect(
        color = Color.White,
        topLeft = Offset(x + yardMargin, y + yardMargin),
        size = Size(yardSize, yardSize)
    )
    drawRect(
        color = Color.Black,
        topLeft = Offset(x + yardMargin, y + yardMargin),
        size = Size(yardSize, yardSize),
        style = Stroke(width = gridStrokeWidth)
    )

    // Four colored circles for tokens inside the white yard
    // Located at (col 1.7, 3.3) and (row 1.7, 3.3) within the quadrant
    val circleRadius = cellSize * 0.48f
    val centers = listOf(
        Offset(x + cellSize * 1.85f, y + cellSize * 1.85f),
        Offset(x + cellSize * 4.15f, y + cellSize * 1.85f),
        Offset(x + cellSize * 1.85f, y + cellSize * 4.15f),
        Offset(x + cellSize * 4.15f, y + cellSize * 4.15f)
    )

    centers.forEach { center ->
        // Solid colored circle
        drawCircle(
            color = primaryColor,
            radius = circleRadius,
            center = center
        )
        // Subtle crisp outline
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = circleRadius,
            center = center,
            style = Stroke(width = gridStrokeWidth * 0.8f)
        )
    }
}

/**
 * Draws a single common track cell on the 15x15 grid with classic white/color fill,
 * dark grid lines, and classic 5-point star on safe cells.
 */
private fun DrawScope.drawClassicTrackCell(
    coord: BoardCoordinate,
    cellSize: Float,
    gridStrokeWidth: Float,
    isSafe: Boolean,
    startColor: LudoColor?,
    isStartCell: Boolean
) {
    val x = coord.col * cellSize
    val y = coord.row * cellSize

    // Cell background color
    // In classic board: Start cell has the color of the player starting there!
    val cellBg = if (isStartCell && startColor != null) {
        LudoThemeColors.getPrimaryColor(startColor)
    } else {
        Color.White
    }

    drawRect(
        color = cellBg,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize)
    )

    // Black grid border
    drawRect(
        color = Color.Black,
        topLeft = Offset(x, y),
        size = Size(cellSize, cellSize),
        style = Stroke(width = gridStrokeWidth)
    )

    val cx = x + cellSize / 2f
    val cy = y + cellSize / 2f

    // Draw safe cell star outline if this is a safe cell
    // (Except start cell which is already strongly identified by its vibrant color background)
    if (isSafe && !isStartCell) {
        drawClassicStarOutline(
            center = Offset(cx, cy),
            radius = cellSize * 0.42f,
            strokeWidth = gridStrokeWidth * 1.2f,
            color = Color.Black
        )
    }
}

/**
 * Draws the 4 entry arrows into the home lanes from the board edges exactly as pictured in the reference image:
 * - Green (top middle): col 7, row 0 -> pointing down (Green arrow into Green home lane)
 * - Blue (bottom middle): col 7, row 14 -> pointing up (Blue arrow into Blue home lane)
 * - Red (left arm middle): col 0, row 7 -> pointing right (Red arrow into Red home lane)
 * - Yellow (right arm middle): col 14, row 7 -> pointing left (Yellow arrow into Yellow home lane)
 */
private fun DrawScope.drawEntryArrows(cellSize: Float, gridStrokeWidth: Float) {
    // Arrow 1: Top arm tip (col 7, row 0) pointing DOWN into Green home lane
    drawDirectionalArrow(
        center = Offset(7.5f * cellSize, 0.5f * cellSize),
        direction = ArrowDirection.DOWN,
        color = LudoThemeColors.GreenPrimary,
        cellSize = cellSize,
        strokeWidth = gridStrokeWidth * 1.5f
    )

    // Arrow 2: Bottom arm tip (col 7, row 14) pointing UP into Blue home lane
    drawDirectionalArrow(
        center = Offset(7.5f * cellSize, 14.5f * cellSize),
        direction = ArrowDirection.UP,
        color = LudoThemeColors.BluePrimary,
        cellSize = cellSize,
        strokeWidth = gridStrokeWidth * 1.5f
    )

    // Arrow 3: Left arm tip (col 0, row 7) pointing RIGHT into Red home lane
    drawDirectionalArrow(
        center = Offset(0.5f * cellSize, 7.5f * cellSize),
        direction = ArrowDirection.RIGHT,
        color = LudoThemeColors.RedPrimary,
        cellSize = cellSize,
        strokeWidth = gridStrokeWidth * 1.5f
    )

    // Arrow 4: Right arm tip (col 14, row 7) pointing LEFT into Yellow home lane
    drawDirectionalArrow(
        center = Offset(14.5f * cellSize, 7.5f * cellSize),
        direction = ArrowDirection.LEFT,
        color = LudoThemeColors.YellowPrimary,
        cellSize = cellSize,
        strokeWidth = gridStrokeWidth * 1.5f
    )
}

private enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }

private fun DrawScope.drawDirectionalArrow(
    center: Offset,
    direction: ArrowDirection,
    color: Color,
    cellSize: Float,
    strokeWidth: Float
) {
    val length = cellSize * 0.35f
    val head = cellSize * 0.16f
    val path = Path()

    when (direction) {
        ArrowDirection.DOWN -> {
            path.moveTo(center.x, center.y - length)
            path.lineTo(center.x, center.y + length)
            path.moveTo(center.x - head, center.y + length - head)
            path.lineTo(center.x, center.y + length)
            path.lineTo(center.x + head, center.y + length - head)
        }
        ArrowDirection.UP -> {
            path.moveTo(center.x, center.y + length)
            path.lineTo(center.x, center.y - length)
            path.moveTo(center.x - head, center.y - length + head)
            path.lineTo(center.x, center.y - length)
            path.lineTo(center.x + head, center.y - length + head)
        }
        ArrowDirection.RIGHT -> {
            path.moveTo(center.x - length, center.y)
            path.lineTo(center.x + length, center.y)
            path.moveTo(center.x + length - head, center.y - head)
            path.lineTo(center.x + length, center.y)
            path.lineTo(center.x + length - head, center.y + head)
        }
        ArrowDirection.LEFT -> {
            path.moveTo(center.x + length, center.y)
            path.lineTo(center.x - length, center.y)
            path.moveTo(center.x - length + head, center.y - head)
            path.lineTo(center.x - length, center.y)
            path.lineTo(center.x - length + head, center.y + head)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Draws the 3x3 center triangular home area with the four classic colored triangles.
 */
private fun DrawScope.drawClassicCenterHome(cellSize: Float, gridStrokeWidth: Float) {
    val cx = 7.5f * cellSize
    val cy = 7.5f * cellSize

    val left = 6f * cellSize
    val right = 9f * cellSize
    val top = 6f * cellSize
    val bottom = 9f * cellSize

    // Center square background
    drawRect(
        color = Color.White,
        topLeft = Offset(left, top),
        size = Size(3 * cellSize, 3 * cellSize)
    )

    // Left triangle: Red (leading from red runway on left)
    val leftPath = Path().apply {
        moveTo(left, top)
        lineTo(cx, cy)
        lineTo(left, bottom)
        close()
    }
    drawPath(leftPath, color = LudoThemeColors.RedPrimary, style = Fill)

    // Top triangle: Green (leading from green runway on top)
    val topPath = Path().apply {
        moveTo(left, top)
        lineTo(cx, cy)
        lineTo(right, top)
        close()
    }
    drawPath(topPath, color = LudoThemeColors.GreenPrimary, style = Fill)

    // Right triangle: Yellow (leading from yellow runway on right)
    val rightPath = Path().apply {
        moveTo(right, top)
        lineTo(cx, cy)
        lineTo(right, bottom)
        close()
    }
    drawPath(rightPath, color = LudoThemeColors.YellowPrimary, style = Fill)

    // Bottom triangle: Blue (leading from blue runway on bottom)
    val bottomPath = Path().apply {
        moveTo(left, bottom)
        lineTo(cx, cy)
        lineTo(right, bottom)
        close()
    }
    drawPath(bottomPath, color = LudoThemeColors.BluePrimary, style = Fill)

    // Diagonal partition lines
    drawLine(Color.Black, Offset(left, top), Offset(right, bottom), strokeWidth = gridStrokeWidth * 1.2f)
    drawLine(Color.Black, Offset(right, top), Offset(left, bottom), strokeWidth = gridStrokeWidth * 1.2f)

    // Border around center 3x3
    drawRect(
        color = Color.Black,
        topLeft = Offset(left, top),
        size = Size(3 * cellSize, 3 * cellSize),
        style = Stroke(width = gridStrokeWidth * 1.5f)
    )
}

/**
 * Draws a 5-pointed star outline as shown on the reference board's safe cells.
 */
private fun DrawScope.drawClassicStarOutline(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color
) {
    val path = Path()
    val spikes = 5
    val step = Math.PI / spikes
    var rot = Math.PI / 2 * 3

    path.moveTo(center.x, (center.y - radius))
    for (i in 0 until spikes) {
        val x1 = center.x + Math.cos(rot).toFloat() * radius
        val y1 = center.y + Math.sin(rot).toFloat() * radius
        path.lineTo(x1, y1)
        rot += step

        val x2 = center.x + Math.cos(rot).toFloat() * (radius * 0.42f)
        val y2 = center.y + Math.sin(rot).toFloat() * (radius * 0.42f)
        path.lineTo(x2, y2)
        rot += step
    }
    path.close()

    drawPath(path, color = color, style = Stroke(width = strokeWidth))
}
