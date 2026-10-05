package com.example.ui.components.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
 * Pure Canvas rendering of the canonical 15x15 Ludo board.
 * Renders all bases, common tracks, safe cells, home runways, and center finish triangles.
 */
@Composable
fun LudoBoardCanvas(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val boardWidth = size.width
        val cellSize = boardWidth / 15f

        // 1. Board background
        drawRect(
            color = LudoThemeColors.BoardBg,
            size = size
        )

        // 2. Draw 4 Corner Bases (6x6 cells each)
        drawCornerBase(
            color = LudoColor.RED,
            startCol = 0, startRow = 0,
            cellSize = cellSize
        )
        drawCornerBase(
            color = LudoColor.GREEN,
            startCol = 9, startRow = 0,
            cellSize = cellSize
        )
        drawCornerBase(
            color = LudoColor.YELLOW,
            startCol = 9, startRow = 9,
            cellSize = cellSize
        )
        drawCornerBase(
            color = LudoColor.BLUE,
            startCol = 0, startRow = 9,
            cellSize = cellSize
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

            drawTrackCell(
                coord = coord,
                cellSize = cellSize,
                isSafe = isSafe,
                startColor = startColor
            )
        }

        // 4. Draw 4 Colored Home Runways (5 cells each)
        LudoColor.values().forEach { color ->
            val lane = LudoBoardCoordinates.homeLanes[color] ?: return@forEach
            val laneColor = LudoThemeColors.getPrimaryColor(color)
            val darkColor = LudoThemeColors.getDarkColor(color)

            lane.forEachIndexed { stepIdx, coord ->
                val x = coord.col * cellSize
                val y = coord.row * cellSize
                val padding = cellSize * 0.08f

                drawRoundRect(
                    color = darkColor,
                    topLeft = Offset(x + padding, y + padding),
                    size = Size(cellSize - padding * 2, cellSize - padding * 2),
                    cornerRadius = CornerRadius(cellSize * 0.2f, cellSize * 0.2f)
                )

                drawRoundRect(
                    color = laneColor.copy(alpha = 0.85f),
                    topLeft = Offset(x + padding * 1.5f, y + padding * 1.5f),
                    size = Size(cellSize - padding * 3, cellSize - padding * 3),
                    cornerRadius = CornerRadius(cellSize * 0.15f, cellSize * 0.15f)
                )

                // White runway arrow/circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = cellSize * 0.14f,
                    center = Offset(x + cellSize / 2f, y + cellSize / 2f)
                )
            }
        }

        // 5. Draw Center 3x3 Home Triangle
        drawCenterHome(cellSize = cellSize)

        // 6. Draw outer board border
        drawRect(
            color = LudoThemeColors.TrackCellBorder,
            style = Stroke(width = cellSize * 0.1f),
            size = size
        )
    }
}

private fun DrawScope.drawCornerBase(
    color: LudoColor,
    startCol: Int,
    startRow: Int,
    cellSize: Float
) {
    val x = startCol * cellSize
    val y = startRow * cellSize
    val baseSize = 6 * cellSize
    val primaryColor = LudoThemeColors.getPrimaryColor(color)
    val darkColor = LudoThemeColors.getDarkColor(color)

    // Outer base container with rounded corner
    val margin = cellSize * 0.15f
    drawRoundRect(
        color = darkColor,
        topLeft = Offset(x + margin, y + margin),
        size = Size(baseSize - margin * 2, baseSize - margin * 2),
        cornerRadius = CornerRadius(cellSize * 0.6f, cellSize * 0.6f)
    )

    drawRoundRect(
        color = primaryColor.copy(alpha = 0.6f),
        topLeft = Offset(x + margin, y + margin),
        size = Size(baseSize - margin * 2, baseSize - margin * 2),
        cornerRadius = CornerRadius(cellSize * 0.6f, cellSize * 0.6f),
        style = Stroke(width = 2.5f)
    )

    // Inner white container
    val innerMargin = cellSize * 0.75f
    val innerSize = baseSize - innerMargin * 2
    drawRoundRect(
        color = Color(0xFF0F0C1E),
        topLeft = Offset(x + innerMargin, y + innerMargin),
        size = Size(innerSize, innerSize),
        cornerRadius = CornerRadius(cellSize * 0.45f, cellSize * 0.45f)
    )

    // 4 base circles for pawns
    val slots = LudoBoardCoordinates.baseSlots[color] ?: emptyList()
    slots.forEach { slot ->
        val cx = slot.col * cellSize + cellSize / 2f
        val cy = slot.row * cellSize + cellSize / 2f
        val radius = cellSize * 0.55f

        drawCircle(
            color = darkColor,
            radius = radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = primaryColor,
            radius = radius * 0.85f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.4f),
            radius = radius * 0.4f,
            center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawTrackCell(
    coord: BoardCoordinate,
    cellSize: Float,
    isSafe: Boolean,
    startColor: LudoColor?
) {
    val x = coord.col * cellSize
    val y = coord.row * cellSize
    val pad = cellSize * 0.06f
    val cellWidth = cellSize - pad * 2

    val cellBg = if (startColor != null) {
        LudoThemeColors.getDarkColor(startColor)
    } else {
        LudoThemeColors.TrackCellBg
    }

    drawRoundRect(
        color = cellBg,
        topLeft = Offset(x + pad, y + pad),
        size = Size(cellWidth, cellWidth),
        cornerRadius = CornerRadius(cellSize * 0.15f, cellSize * 0.15f)
    )

    val borderColor = if (startColor != null) {
        LudoThemeColors.getPrimaryColor(startColor).copy(alpha = 0.7f)
    } else {
        LudoThemeColors.TrackCellBorder
    }

    drawRoundRect(
        color = borderColor,
        topLeft = Offset(x + pad, y + pad),
        size = Size(cellWidth, cellWidth),
        cornerRadius = CornerRadius(cellSize * 0.15f, cellSize * 0.15f),
        style = Stroke(width = 1.2f)
    )

    // Draw Golden Star for safe cells
    if (isSafe) {
        val cx = x + cellSize / 2f
        val cy = y + cellSize / 2f
        val starRadius = cellSize * 0.32f
        drawStar(
            center = Offset(cx, cy),
            radius = starRadius,
            color = if (startColor != null) LudoThemeColors.getPrimaryColor(startColor) else LudoThemeColors.SafeStarColor
        )
    }
}

private fun DrawScope.drawCenterHome(cellSize: Float) {
    val cx = 7.5f * cellSize
    val cy = 7.5f * cellSize

    val left = 6f * cellSize
    val right = 9f * cellSize
    val top = 6f * cellSize
    val bottom = 9f * cellSize

    // Center background
    drawRect(
        color = LudoThemeColors.CenterHomeBg,
        topLeft = Offset(left, top),
        size = Size(3 * cellSize, 3 * cellSize)
    )

    // Red triangle (Left)
    val redPath = Path().apply {
        moveTo(left, top)
        lineTo(cx, cy)
        lineTo(left, bottom)
        close()
    }
    drawPath(redPath, LudoThemeColors.RedPrimary.copy(alpha = 0.85f), style = Fill)

    // Green triangle (Top)
    val greenPath = Path().apply {
        moveTo(left, top)
        lineTo(cx, cy)
        lineTo(right, top)
        close()
    }
    drawPath(greenPath, LudoThemeColors.GreenPrimary.copy(alpha = 0.85f), style = Fill)

    // Yellow triangle (Right)
    val yellowPath = Path().apply {
        moveTo(right, top)
        lineTo(cx, cy)
        lineTo(right, bottom)
        close()
    }
    drawPath(yellowPath, LudoThemeColors.YellowPrimary.copy(alpha = 0.85f), style = Fill)

    // Blue triangle (Bottom)
    val bluePath = Path().apply {
        moveTo(left, bottom)
        lineTo(cx, cy)
        lineTo(right, bottom)
        close()
    }
    drawPath(bluePath, LudoThemeColors.BluePrimary.copy(alpha = 0.85f), style = Fill)

    // Center divider lines
    val strokeColor = LudoThemeColors.TrackCellBorder
    drawLine(strokeColor, Offset(left, top), Offset(right, bottom), strokeWidth = 2f)
    drawLine(strokeColor, Offset(right, top), Offset(left, bottom), strokeWidth = 2f)

    // Center trophy star
    drawStar(
        center = Offset(cx, cy),
        radius = cellSize * 0.45f,
        color = Color(0xFFFFD700)
    )
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
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

        val x2 = center.x + Math.cos(rot).toFloat() * (radius * 0.45f)
        val y2 = center.y + Math.sin(rot).toFloat() * (radius * 0.45f)
        path.lineTo(x2, y2)
        rot += step
    }
    path.close()
    drawPath(path, color, style = Fill)
}
