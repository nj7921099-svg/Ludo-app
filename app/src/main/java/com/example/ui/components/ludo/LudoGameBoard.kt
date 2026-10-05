package com.example.ui.components.ludo

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.game.ludo.model.BoardCoordinate
import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TurnPhase
import com.example.ui.theme.NeonPurple

/**
 * Data holder pairing a token with its computed screen position and legality.
 */
private data class TokenRenderItem(
    val player: LudoPlayer,
    val token: LudoToken,
    val coord: BoardCoordinate,
    val isLegal: Boolean,
    val stackIndex: Int,
    val stackTotal: Int
)

/**
 * Real, responsive, interactive Compose 15x15 Ludo Game Board.
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
    modifier: Modifier = Modifier
) {
    val activePlayer = gameState.activePlayer
    val hasPendingForActive = pendingCommands.containsKey(gameState.currentPlayerId)

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

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Responsive 15x15 Board Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.0f)
                .testTag("ludo_board_card"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = LudoThemeColors.BoardBg),
            border = androidx.compose.foundation.BorderStroke(2.dp, NeonPurple)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
            ) {
                val boardWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val cellSizePx = boardWidthPx / 15f
                val tokenSizeDp = (maxWidth / 15f) * 0.78f

                // Draw Static Board (Bases, 52 Track Cells, Home Lanes, Center Triangle)
                LudoBoardCanvas(modifier = Modifier.fillMaxSize())

                // 3. Calculate and Render Tokens
                val tokenItems = buildTokenRenderList(gameState)

                tokenItems.forEach { item ->
                    val (offsetX, offsetY) = calculateTokenOffset(
                        coord = item.coord,
                        stackIndex = item.stackIndex,
                        stackTotal = item.stackTotal,
                        cellSizePx = cellSizePx
                    )

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                            .size(maxWidth / 15f),
                        contentAlignment = Alignment.Center
                    ) {
                        LudoTokenComposable(
                            color = item.player.color,
                            tokenId = item.token.tokenId,
                            isLegalToMove = item.isLegal,
                            onClick = {
                                if (item.isLegal) {
                                    onTokenClick(item.token.tokenId)
                                }
                            },
                            size = tokenSizeDp
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
                onDiceClick = onDiceClick
            )
        }

        // 5. Winner Celebration Dialog
        if (gameState.isGameOver && gameState.winners.isNotEmpty()) {
            LudoWinnerOverlay(
                winners = gameState.winners,
                players = gameState.players,
                onNewGame = onNewGame,
                onBackToSetup = onBackToSetup
            )
        }
    }
}

/**
 * Builds list of tokens to render with coordinate resolution, legality, and stacking offsets.
 */
private fun buildTokenRenderList(gameState: LudoGameState): List<TokenRenderItem> {
    val items = mutableListOf<TokenRenderItem>()

    gameState.players.forEach { player ->
        val isCurrentTurn = (player.playerId == gameState.currentPlayerId)
        val isWaitingToken = (gameState.turnPhase == TurnPhase.WAITING_FOR_TOKEN_SELECTION)

        player.tokens.forEach { token ->
            val coord = LudoBoardCoordinates.getCoordinateForToken(
                color = player.color,
                tokenId = token.tokenId,
                stepCount = token.stepCount
            )
            val isLegal = isCurrentTurn && isWaitingToken && (token.tokenId in gameState.legalTokenIds)

            items.add(
                TokenRenderItem(
                    player = player,
                    token = token,
                    coord = coord,
                    isLegal = isLegal,
                    stackIndex = 0,
                    stackTotal = 1
                )
            )
        }
    }

    // Group by coordinate to calculate stacking offsets
    val groupedByCoord = items.groupBy { it.coord }
    val finalItems = mutableListOf<TokenRenderItem>()

    groupedByCoord.forEach { (_, group) ->
        val total = group.size
        group.forEachIndexed { index, item ->
            finalItems.add(
                item.copy(stackIndex = index, stackTotal = total)
            )
        }
    }

    return finalItems
}

/**
 * Computes pixel offset for a token, applying stacking dispersion when multiple tokens share a square.
 */
private fun calculateTokenOffset(
    coord: BoardCoordinate,
    stackIndex: Int,
    stackTotal: Int,
    cellSizePx: Float
): Pair<Float, Float> {
    val baseX = coord.col * cellSizePx
    val baseY = coord.row * cellSizePx

    if (stackTotal <= 1) {
        return Pair(baseX, baseY)
    }

    // Cluster stacking dispersion
    val delta = cellSizePx * 0.16f
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
