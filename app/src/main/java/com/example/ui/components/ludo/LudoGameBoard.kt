package com.example.ui.components.ludo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ludo.model.BoardCoordinate
import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TurnPhase
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import kotlinx.coroutines.delay

/**
 * Data holder pairing a token with its computed screen position and legality.
 */
private data class TokenRenderItem(
    val player: LudoPlayer,
    val token: LudoToken,
    val visualCoord: BoardCoordinate,
    val isLegal: Boolean,
    val stackIndex: Int,
    val stackTotal: Int,
    val elevationHopDp: Float = 0f,
    val isAnimating: Boolean = false
)

/**
 * Real, responsive, interactive Compose 15x15 Ludo Game Board with smooth step-by-step movement animations.
 *
 * @param gameState Immutable snapshot from authoritative [com.example.game.ludo.engine.LudoGameEngine].
 * @param pendingCommands Map of playerId -> number pending from Controller.
 * @param onDiceClick Triggered when player activates the dice.
 * @param onTokenClick Triggered when player taps a legal token to move.
 * @param onNewGame Triggered when player starts a rematch from victory overlay.
 * @param onBackToSetup Triggered when player returns to setup from victory overlay.
 */
@Composable
fun LudoGameBoard(
    gameState: LudoGameState,
    pendingCommands: Map<Int, Int>,
    onDiceClick: () -> Unit,
    onTokenClick: (Int) -> Unit,
    onNewGame: () -> Unit,
    onBackToSetup: () -> Unit,
    modifier: Modifier = Modifier,
    isMoveLocked: Boolean = false,
    onViewStats: () -> Unit = {}
) {
    val activePlayer = gameState.activePlayer
    val hasPendingForActive = pendingCommands.containsKey(gameState.currentPlayerId)

    // Animation position override map: Key is Pair(playerId, tokenId) -> visual step override
    val visualStepOverrides = remember { mutableStateMapOf<Pair<Int, Int>, Int>() }
    val visualElevationHops = remember { mutableStateMapOf<Pair<Int, Int>, Float>() }
    var isAnimationActive by remember { mutableStateOf(false) }
    var captureNotification by remember { mutableStateOf<String?>(null) }

    // Track previous token steps to trigger step-by-step animations
    val previousSteps = remember { mutableStateMapOf<Pair<Int, Int>, Int>() }

    // Listen to changes in gameState tokens to trigger visual movement sequences
    LaunchedEffect(gameState) {
        // Find moved tokens
        var movedPlayer: LudoPlayer? = null
        var movedToken: LudoToken? = null
        var fromStep = 0
        var toStep = 0

        val currentStepMap = mutableMapOf<Pair<Int, Int>, Int>()
        val capturedTokensList = mutableListOf<Pair<LudoPlayer, LudoToken>>()

        gameState.players.forEach { player ->
            player.tokens.forEach { token ->
                val key = Pair(player.playerId, token.tokenId)
                val oldStep = previousSteps[key]
                val currentStep = token.stepCount
                currentStepMap[key] = currentStep

                if (oldStep != null && oldStep != currentStep) {
                    if (currentStep > oldStep) {
                        movedPlayer = player
                        movedToken = token
                        fromStep = oldStep
                        toStep = currentStep
                    } else if (oldStep > 0 && currentStep == 0) {
                        // Token was captured and returned to yard!
                        capturedTokensList.add(Pair(player, token))
                    }
                }
            }
        }

        // Update previous steps tracking
        previousSteps.clear()
        previousSteps.putAll(currentStepMap)

        // Execute animated movement sequence if a move was detected
        if (movedPlayer != null && movedToken != null) {
            val key = Pair(movedPlayer!!.playerId, movedToken!!.tokenId)
            try {
                isAnimationActive = true

                if (fromStep == 0 && toStep == 1) {
                    // Yard exit jump
                    visualStepOverrides[key] = 0
                    visualElevationHops[key] = 14f
                    delay(90)
                    visualStepOverrides[key] = 1
                    visualElevationHops[key] = 0f
                    delay(100)
                } else {
                    // Step-by-step path traversal with responsive cadence
                    for (s in (fromStep + 1)..toStep) {
                        visualStepOverrides[key] = s
                        visualElevationHops[key] = 8f
                        delay(45)
                        visualElevationHops[key] = 0f
                        delay(20)
                    }
                }

                // If a capture accompanied the move, display temporary visual burst
                if (capturedTokensList.isNotEmpty()) {
                    val captured = capturedTokensList.first()
                    captureNotification = "💥 Player ${captured.first.playerId}'s token captured!"
                    delay(500)
                    captureNotification = null
                }
            } finally {
                // ALWAYS clean up state maps and release input lock
                visualStepOverrides.remove(key)
                visualElevationHops.remove(key)
                isAnimationActive = false
            }
        }
    }

    val totalLock = isMoveLocked || isAnimationActive

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ludo_game_board_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Turn Indicator Banner
        LudoTurnIndicator(
            activePlayer = activePlayer,
            turnPhase = gameState.turnPhase,
            consecutiveSixCount = gameState.consecutiveSixCount,
            hasPendingControllerNumber = hasPendingForActive
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Capture Toast Banner
        AnimatedVisibility(
            visible = captureNotification != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3F111E)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Capture",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = captureNotification ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFF1F2),
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }

        // 2. Responsive 15x15 Board Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.0f)
                .testTag("ludo_board_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFF1E1B2E)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val boardWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val cellSizePx = boardWidthPx / 15f
                val tokenSizeDp = (maxWidth / 15f) * 0.82f

                // Draw Static Board (Bases, 52 Track Cells, Home Lanes, Center Triangle)
                LudoBoardCanvas(modifier = Modifier.fillMaxSize())

                // 3. Calculate and Render Tokens with Visual Coordinate Overrides & Stacking
                val tokenItems = buildTokenRenderList(
                    gameState = gameState,
                    visualStepOverrides = visualStepOverrides,
                    visualElevationHops = visualElevationHops
                )

                tokenItems.forEach { item ->
                    val (offsetX, offsetY) = calculateTokenOffset(
                        coord = item.visualCoord,
                        stackIndex = item.stackIndex,
                        stackTotal = item.stackTotal,
                        cellSizePx = cellSizePx,
                        elevationHop = item.elevationHopDp
                    )

                    val isClickable = item.isLegal && !totalLock

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                            .size(maxWidth / 15f),
                        contentAlignment = Alignment.Center
                    ) {
                        LudoTokenComposable(
                            color = item.player.color,
                            tokenId = item.token.tokenId,
                            isLegalToMove = isClickable,
                            onClick = {
                                if (isClickable) {
                                    onTokenClick(item.token.tokenId)
                                }
                            },
                            size = if (item.stackTotal > 1) tokenSizeDp * 0.84f else tokenSizeDp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Interactive Bottom Dice Control
        if (activePlayer != null) {
            LudoDiceControl(
                diceValue = gameState.diceValue,
                turnPhase = gameState.turnPhase,
                activeColor = activePlayer.color,
                onDiceClick = onDiceClick,
                isMoveLocked = totalLock
            )
        }

        // 5. Winner Celebration Dialog
        if (gameState.isGameOver && gameState.winners.isNotEmpty()) {
            LudoWinnerOverlay(
                winners = gameState.winners,
                players = gameState.players,
                onNewGame = onNewGame,
                onBackToSetup = onBackToSetup,
                onViewStats = onViewStats
            )
        }
    }
}

/**
 * Builds list of tokens to render with coordinate resolution, legality, and stacking offsets.
 */
private fun buildTokenRenderList(
    gameState: LudoGameState,
    visualStepOverrides: Map<Pair<Int, Int>, Int>,
    visualElevationHops: Map<Pair<Int, Int>, Float>
): List<TokenRenderItem> {
    val items = mutableListOf<TokenRenderItem>()

    gameState.players.forEach { player ->
        val activePlayer = gameState.activePlayer
        val isPartnerAssistance = (gameState.gameMode == com.example.game.ludo.model.LudoGameMode.TEAM_UP &&
                activePlayer != null &&
                activePlayer.isFinished &&
                activePlayer.teamId != null)
        val tokenOwnerPlayerId = if (isPartnerAssistance) {
            gameState.players.find { it.playerId != activePlayer!!.playerId && it.teamId == activePlayer.teamId }?.playerId ?: gameState.currentPlayerId
        } else {
            gameState.currentPlayerId
        }

        val isTargetPlayer = (player.playerId == tokenOwnerPlayerId)
        val isWaitingToken = (gameState.turnPhase == TurnPhase.WAITING_FOR_TOKEN_SELECTION)

        player.tokens.forEach { token ->
            val key = Pair(player.playerId, token.tokenId)
            val effectiveStep = visualStepOverrides[key] ?: token.stepCount
            val elevationHop = visualElevationHops[key] ?: 0f

            val coord = LudoBoardCoordinates.getCoordinateForToken(
                color = player.color,
                tokenId = token.tokenId,
                stepCount = effectiveStep
            )
            val isLegal = isTargetPlayer && isWaitingToken && (token.tokenId in gameState.legalTokenIds)

            items.add(
                TokenRenderItem(
                    player = player,
                    token = token,
                    visualCoord = coord,
                    isLegal = isLegal,
                    stackIndex = 0,
                    stackTotal = 1,
                    elevationHopDp = elevationHop,
                    isAnimating = visualStepOverrides.containsKey(key)
                )
            )
        }
    }

    // Stable grouping by coordinate to calculate stacking offsets
    val groupedByCoord = items.groupBy { it.visualCoord }
    val finalItems = mutableListOf<TokenRenderItem>()

    groupedByCoord.forEach { (_, group) ->
        val total = group.size
        // Stable sort by playerId and tokenId
        val sortedGroup = group.sortedWith(compareBy({ it.player.playerId }, { it.token.tokenId }))
        sortedGroup.forEachIndexed { index, item ->
            finalItems.add(
                item.copy(stackIndex = index, stackTotal = total)
            )
        }
    }

    return finalItems
}

/**
 * Computes pixel offset for a token, applying stacking dispersion and hop arcs.
 */
private fun calculateTokenOffset(
    coord: BoardCoordinate,
    stackIndex: Int,
    stackTotal: Int,
    cellSizePx: Float,
    elevationHop: Float
): Pair<Float, Float> {
    val baseX = coord.col * cellSizePx
    val baseY = coord.row * cellSizePx - (elevationHop * (cellSizePx / 36f))

    if (stackTotal <= 1) {
        return Pair(baseX, baseY)
    }

    // Cluster stacking dispersion with distinct quadrants
    val delta = cellSizePx * 0.18f
    val (dx, dy) = when (stackTotal) {
        2 -> when (stackIndex) {
            0 -> Pair(-delta, -delta)
            else -> Pair(delta, delta)
        }
        3 -> when (stackIndex) {
            0 -> Pair(0f, -delta)
            1 -> Pair(-delta, delta)
            else -> Pair(delta, delta)
        }
        else -> when (stackIndex % 4) {
            0 -> Pair(-delta, -delta)
            1 -> Pair(delta, -delta)
            2 -> Pair(-delta, delta)
            else -> Pair(delta, delta)
        }
    }

    return Pair(baseX + dx, baseY + dy)
}

