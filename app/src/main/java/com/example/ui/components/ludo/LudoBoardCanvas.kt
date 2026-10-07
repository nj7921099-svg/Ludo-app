package com.example.ui.components.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import com.example.game.ludo.model.BoardCoordinate
import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoColor

/**
 * Pure Canvas rendering of the canonical 15x15 Ludo board.
 * Renders all bases, common tracks, safe cells with actual xpstar.png assets, home runways, and center finish triangles.
 */
@Composable
fun LudoBoardCanvas(
    modifier: Modifier = Modifier
) {
    val starBitmap = ImageBitmap.imageResource(id = R.drawable.xpstar)

    Canvas(modifier = modifier.fillMaxSize()) {
        val boardWidth = size.width
        val cellSize = boardWidth / 15f

        // 1. Board background with subtle radial depth
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
                startColor = startColor,
                isStartCell = isStart,
                starBitmap = starBitmap
            )
        }

        // 4. Draw 4 Colored Home Runways (5 cells each)
        LudoColor.values().forEach { color ->
            val lane = LudoBoardCoordinates.homeLanes[color] ?: return@forEach
            val laneColor = LudoThemeColors.getPrimaryColor(color)
            val darkColor = LudoThemeColors.getDarkColor(color)
            val lightColor = LudoThemeColors.getLightColor(color)

            lane.forEachIndexed { stepIdx, coord ->
                val x = coord.col * cellSize
                val y = coord.row * cellSize
                val padding = cellSize * 0.07f

                // Outer tile border
                drawRoundRect(
                    color = darkColor,
                    topLeft = Offset(x + padding, y + padding),
                    size = Size(cellSize - padding * 2, cellSize - padding * 2),
                    cornerRadius = CornerRadius(cellSize * 0.22f, cellSize * 0.22f)
                )

                // Colored runway fill
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(laneColor, laneColor.copy(alpha = 0.82f)),
                        start = Offset(x, y),
                        end = Offset(x + cellSize, y + cellSize)
                    ),
                    topLeft = Offset(x + padding * 1.4f, y + padding * 1.4f),
                    size = Size(cellSize - padding * 2.8f, cellSize - padding * 2.8f),
                    cornerRadius = CornerRadius(cellSize * 0.18f, cellSize * 0.18f)
                )

                // Directional runway arrow pointing inward
                drawRunwayArrow(
                    color = color,
                    center = Offset(x + cellSize / 2f, y + cellSize / 2f),
                    cellSize = cellSize
                )
            }
        }

        // 5. Draw Center 3x3 Home Triangle with Finish Podium
        drawCenterHome(cellSize = cellSize)

        // 6. Outer board border frame
        drawRoundRect(
            color = LudoThemeColors.TrackCellBorder,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cellSize * 0.4f, cellSize * 0.4f),
            style = Stroke(width = cellSize * 0.08f)
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
    val lightColor = LudoThemeColors.getLightColor(color)

    // Outer base container with rounded corner
    val margin = cellSize * 0.14f
    drawRoundRect(
        color = darkColor,
        topLeft = Offset(x + margin, y + margin),
        size = Size(baseSize - margin * 2, baseSize - margin * 2),
        cornerRadius = CornerRadius(cellSize * 0.55f, cellSize * 0.55f)
    )

    // Vibrant border outline
    drawRoundRect(
        color = primaryColor.copy(alpha = 0.75f),
        topLeft = Offset(x + margin, y + margin),
        size = Size(baseSize - margin * 2, baseSize - margin * 2),
        cornerRadius = CornerRadius(cellSize * 0.55f, cellSize * 0.55f),
        style = Stroke(width = 3.0f)
    )

    // Inner dark container
    val innerMargin = cellSize * 0.72f
    val innerSize = baseSize - innerMargin * 2
    drawRoundRect(
        color = Color(0xFF0D0A1C),
        topLeft = Offset(x + innerMargin, y + innerMargin),
        size = Size(innerSize, innerSize),
        cornerRadius = CornerRadius(cellSize * 0.42f, cellSize * 0.42f)
    )

    drawRoundRect(
        color = primaryColor.copy(alpha = 0.35f),
        topLeft = Offset(x + innerMargin, y + innerMargin),
        size = Size(innerSize, innerSize),
        cornerRadius = CornerRadius(cellSize * 0.42f, cellSize * 0.42f),
        style = Stroke(width = 1.5f)
    )

    // 4 base circles for pawns (slots)
    val slots = LudoBoardCoordinates.baseSlots[color] ?: emptyList()
    slots.forEach { slot ->
        val cx = slot.col * cellSize + cellSize / 2f
        val cy = slot.row * cellSize + cellSize / 2f
        val radius = cellSize * 0.58f

        // Ambient shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = radius * 1.05f,
            center = Offset(cx, cy + 2f)
        )

        // Outer base slot ring
        drawCircle(
            color = darkColor,
            radius = radius,
            center = Offset(cx, cy)
        )

        // Inner glowing pod ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor, darkColor),
                center = Offset(cx - radius * 0.2f, cy - radius * 0.2f),
                radius = radius
            ),
            radius = radius * 0.88f,
            center = Offset(cx, cy)
        )

        // Metallic inner rim
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = radius * 0.42f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )

        // Specular dot
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            radius = radius * 0.16f,
            center = Offset(cx - radius * 0.22f, cy - radius * 0.22f)
        )
    }
}

private fun DrawScope.drawTrackCell(
    coord: BoardCoordinate,
    cellSize: Float,
    isSafe: Boolean,
    startColor: LudoColor?,
    isStartCell: Boolean,
    starBitmap: ImageBitmap
) {
    val x = coord.col * cellSize
    val y = coord.row * cellSize
    val pad = cellSize * 0.055f
    val cellWidth = cellSize - pad * 2

    val cellBg = if (startColor != null) {
        LudoThemeColors.getDarkColor(startColor)
    } else {
        LudoThemeColors.TrackCellBg
    }

    // Cell base rectangle
    drawRoundRect(
        color = cellBg,
        topLeft = Offset(x + pad, y + pad),
        size = Size(cellWidth, cellWidth),
        cornerRadius = CornerRadius(cellSize * 0.18f, cellSize * 0.18f)
    )

    val borderColor = if (startColor != null) {
        LudoThemeColors.getPrimaryColor(startColor).copy(alpha = 0.85f)
    } else {
        LudoThemeColors.TrackCellBorder
    }

    drawRoundRect(
        color = borderColor,
        topLeft = Offset(x + pad, y + pad),
        size = Size(cellWidth, cellWidth),
        cornerRadius = CornerRadius(cellSize * 0.18f, cellSize * 0.18f),
        style = Stroke(width = if (startColor != null) 1.8f else 1.2f)
    )

    val cx = x + cellSize / 2f
    val cy = y + cellSize / 2f

    // Draw Golden Star for safe cells using actual xpstar.png asset
    if (isSafe) {
        val starSize = (cellSize * 0.72f).toInt()
        drawImage(
            image = starBitmap,
            dstOffset = IntOffset((cx - starSize / 2f).toInt(), (cy - starSize / 2f).toInt()),
            dstSize = IntSize(starSize, starSize)
        )
    }

    // Start arrow on entry tiles
    if (isStartCell && startColor != null) {
        drawStartArrow(
            color = startColor,
            center = Offset(cx, cy),
            cellSize = cellSize
        )
    }
}

private fun DrawScope.drawRunwayArrow(color: LudoColor, center: Offset, cellSize: Float) {
    val arrowSize = cellSize * 0.22f
    val path = Path()

    when (color) {
        LudoColor.RED -> {
            // Point right
            path.moveTo(center.x - arrowSize * 0.6f, center.y - arrowSize * 0.8f)
            path.lineTo(center.x + arrowSize * 0.6f, center.y)
            path.lineTo(center.x - arrowSize * 0.6f, center.y + arrowSize * 0.8f)
        }
        LudoColor.GREEN -> {
            // Point down
            path.moveTo(center.x - arrowSize * 0.8f, center.y - arrowSize * 0.6f)
            path.lineTo(center.x, center.y + arrowSize * 0.6f)
            path.lineTo(center.x + arrowSize * 0.8f, center.y - arrowSize * 0.6f)
        }
        LudoColor.YELLOW -> {
            // Point left
            path.moveTo(center.x + arrowSize * 0.6f, center.y - arrowSize * 0.8f)
            path.lineTo(center.x - arrowSize * 0.6f, center.y)
            path.lineTo(center.x + arrowSize * 0.6f, center.y + arrowSize * 0.8f)
        }
        LudoColor.BLUE -> {
            // Point up
            path.moveTo(center.x - arrowSize * 0.8f, center.y + arrowSize * 0.6f)
            path.lineTo(center.x, center.y - arrowSize * 0.6f)
            path.lineTo(center.x + arrowSize * 0.8f, center.y + arrowSize * 0.6f)
        }
    }

    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.55f),
        style = Stroke(width = 2.2f)
    )
}

private fun DrawScope.drawStartArrow(color: LudoColor, center: Offset, cellSize: Float) {
    val size = cellSize * 0.18f
    val path = Path()

    when (color) {
        LudoColor.RED -> {
            // Moves right
            path.moveTo(center.x - size, center.y - size)
            path.lineTo(center.x + size, center.y)
            path.lineTo(center.x - size, center.y + size)
        }
        LudoColor.GREEN -> {
            // Moves down
            path.moveTo(center.x - size, center.y - size)
            path.lineTo(center.x, center.y + size)
            path.lineTo(center.x + size, center.y - size)
        }
        LudoColor.YELLOW -> {
            // Moves left
            path.moveTo(center.x + size, center.y - size)
            path.lineTo(center.x - size, center.y)
            path.lineTo(center.x + size, center.y + size)
        }
        LudoColor.BLUE -> {
            // Moves up
            path.moveTo(center.x - size, center.y + size)
            path.lineTo(center.x, center.y - size)
            path.lineTo(center.x + size, center.y + size)
        }
    }

    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.85f),
        style = Stroke(width = 2.0f)
    )
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
    drawPath(
        redPath,
        brush = Brush.radialGradient(
            colors = listOf(LudoThemeColors.RedPrimary, LudoThemeColors.RedDark),
            center = Offset(left, cy),
            radius = 2.5f * cellSize
        ),
        style = Fill
    )

    // Green triangle (Top)
    val greenPath = Path().apply {
        moveTo(left, top)
        lineTo(cx, cy)
        lineTo(right, top)
        close()
    }
    drawPath(
        greenPath,
        brush = Brush.radialGradient(
            colors = listOf(LudoThemeColors.GreenPrimary, LudoThemeColors.GreenDark),
            center = Offset(cx, top),
            radius = 2.5f * cellSize
        ),
        style = Fill
    )

    // Yellow triangle (Right)
    val yellowPath = Path().apply {
        moveTo(right, top)
        lineTo(cx, cy)
        lineTo(right, bottom)
        close()
    }
    drawPath(
        yellowPath,
        brush = Brush.radialGradient(
            colors = listOf(LudoThemeColors.YellowPrimary, LudoThemeColors.YellowDark),
            center = Offset(right, cy),
            radius = 2.5f * cellSize
        ),
        style = Fill
    )

    // Blue triangle (Bottom)
    val bluePath = Path().apply {
        moveTo(left, bottom)
        lineTo(cx, cy)
        lineTo(right, bottom)
        close()
    }
    drawPath(
        bluePath,
        brush = Brush.radialGradient(
            colors = listOf(LudoThemeColors.BluePrimary, LudoThemeColors.BlueDark),
            center = Offset(cx, bottom),
            radius = 2.5f * cellSize
        ),
        style = Fill
    )

    // Center divider lines
    val strokeColor = LudoThemeColors.TrackCellBorder
    drawLine(strokeColor, Offset(left, top), Offset(right, bottom), strokeWidth = 2.5f)
    drawLine(strokeColor, Offset(right, top), Offset(left, bottom), strokeWidth = 2.5f)

    // Center finish crown/star emblem
    drawCircle(
        color = Color(0xFF140E28),
        radius = cellSize * 0.65f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFFFFD700),
        radius = cellSize * 0.65f,
        center = Offset(cx, cy),
        style = Stroke(width = 2.0f)
    )

    drawStar(
        center = Offset(cx, cy),
        radius = cellSize * 0.42f,
        color = Color(0xFFFFD700)
    )
    drawStar(
        center = Offset(cx, cy),
        radius = cellSize * 0.18f,
        color = Color.White
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

