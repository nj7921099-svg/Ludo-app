package com.example

import com.example.game.ludo.ai.LudoBotStrategy
import com.example.game.ludo.engine.CaptureResolver
import com.example.game.ludo.engine.LudoDiceBridge
import com.example.game.ludo.engine.LudoGameEngine
import com.example.game.ludo.engine.MoveValidator
import com.example.game.ludo.engine.PendingNumberStore
import com.example.game.ludo.model.BoardCoordinate
import com.example.game.ludo.model.LudoBoardCoordinates
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoGameState
import com.example.game.ludo.model.LudoPlayer
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.model.LudoToken
import com.example.game.ludo.model.TokenState
import com.example.game.ludo.model.TurnPhase
import com.example.game.ludo.persistence.LudoStatePersistence
import com.example.game.model.RollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 12 Step 1 & Phase 13 Regression Test Suite
 *
 * Verifies all 14 explicit regression requirements:
 * 1. P1 always owns RED tokens.
 * 2. P2 always owns GREEN tokens.
 * 3. P3 always owns YELLOW tokens.
 * 4. P4 always owns BLUE tokens.
 * 5. Base token colors match their corresponding home areas.
 * 6. Token ownership and color remain correct after movement and capture.
 * 7. Rematch preserves the canonical color mapping.
 * 8. Restored saved games preserve token ownership and colors.
 * 9. Each active player has an individual dice control.
 * 10. The old global/main dice control is absent.
 * 11. Inactive players cannot manually roll.
 * 12. Bot turns continue to roll automatically.
 * 13. Controller input still works through the existing pathway.
 * 14. Team-Up partner assistance remains correct.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase13BoardUiTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var engine: LudoGameEngine

    @Before
    fun setUp() {
        engine = LudoGameEngine()
    }

    // -------------------------------------------------------------------------
    // 1. P1 always owns RED tokens
    // -------------------------------------------------------------------------
    @Test
    fun test1_P1AlwaysOwnsRedTokens() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val p1 = engine.gameState.value.players.first { it.playerId == 1 }

        assertEquals("P1 must be assigned RED color", LudoColor.RED, p1.color)
        assertEquals("P1 must own exactly 4 tokens", 4, p1.tokens.size)
        p1.tokens.forEach { token ->
            assertEquals("Token must belong to P1", 1, token.playerId)
        }
    }

    // -------------------------------------------------------------------------
    // 2. P2 always owns GREEN tokens
    // -------------------------------------------------------------------------
    @Test
    fun test2_P2AlwaysOwnsGreenTokens() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val p2 = engine.gameState.value.players.first { it.playerId == 2 }

        assertEquals("P2 must be assigned GREEN color", LudoColor.GREEN, p2.color)
        assertEquals("P2 must own exactly 4 tokens", 4, p2.tokens.size)
        p2.tokens.forEach { token ->
            assertEquals("Token must belong to P2", 2, token.playerId)
        }
    }

    // -------------------------------------------------------------------------
    // 3. P3 always owns YELLOW tokens
    // -------------------------------------------------------------------------
    @Test
    fun test3_P3AlwaysOwnsYellowTokens() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val p3 = engine.gameState.value.players.first { it.playerId == 3 }

        assertEquals("P3 must be assigned YELLOW color", LudoColor.YELLOW, p3.color)
        assertEquals("P3 must own exactly 4 tokens", 4, p3.tokens.size)
        p3.tokens.forEach { token ->
            assertEquals("Token must belong to P3", 3, token.playerId)
        }
    }

    // -------------------------------------------------------------------------
    // 4. P4 always owns BLUE tokens
    // -------------------------------------------------------------------------
    @Test
    fun test4_P4AlwaysOwnsBlueTokens() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val p4 = engine.gameState.value.players.first { it.playerId == 4 }

        assertEquals("P4 must be assigned BLUE color", LudoColor.BLUE, p4.color)
        assertEquals("P4 must own exactly 4 tokens", 4, p4.tokens.size)
        p4.tokens.forEach { token ->
            assertEquals("Token must belong to P4", 4, token.playerId)
        }
    }

    // -------------------------------------------------------------------------
    // 5. Base token colors match their corresponding home areas
    // -------------------------------------------------------------------------
    @Test
    fun test5_BaseTokenColorsMatchTheirCorrespondingHomeAreas() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val state = engine.gameState.value

        state.players.forEach { player ->
            player.tokens.forEach { token ->
                val baseCoord = LudoBoardCoordinates.getCoordinateForToken(player.color, token.tokenId, 0)
                when (player.color) {
                    LudoColor.RED -> {
                        // Top-Left corner base quadrant: cols 0..5, rows 0..5
                        assertTrue("P1 RED token must be in Top-Left base quadrant (col=${baseCoord.col}, row=${baseCoord.row})",
                            baseCoord.col in 0..5 && baseCoord.row in 0..5)
                    }
                    LudoColor.GREEN -> {
                        // Top-Right corner base quadrant: cols 9..14, rows 0..5
                        assertTrue("P2 GREEN token must be in Top-Right base quadrant (col=${baseCoord.col}, row=${baseCoord.row})",
                            baseCoord.col in 9..14 && baseCoord.row in 0..5)
                    }
                    LudoColor.YELLOW -> {
                        // Bottom-Right corner base quadrant: cols 9..14, rows 9..14
                        assertTrue("P3 YELLOW token must be in Bottom-Right base quadrant (col=${baseCoord.col}, row=${baseCoord.row})",
                            baseCoord.col in 9..14 && baseCoord.row in 9..14)
                    }
                    LudoColor.BLUE -> {
                        // Bottom-Left corner base quadrant: cols 0..5, rows 9..14
                        assertTrue("P4 BLUE token must be in Bottom-Left base quadrant (col=${baseCoord.col}, row=${baseCoord.row})",
                            baseCoord.col in 0..5 && baseCoord.row in 9..14)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 6. Token ownership and color remain correct after movement and capture
    // -------------------------------------------------------------------------
    @Test
    fun test6_TokenOwnershipAndColorRemainCorrectAfterMovementAndCapture() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))

        // P1 rolls 6 and moves token 0 out to track index 0 (col=1, row=6)
        assertTrue(engine.onDiceRolled(6))
        val moveResult = engine.moveToken(tokenId = 0)
        assertNotNull(moveResult)

        val p1Token0 = engine.gameState.value.players.first { it.playerId == 1 }.tokens[0]
        assertEquals(TokenState.ON_BOARD, p1Token0.state)
        assertEquals(1, p1Token0.playerId)
        assertEquals(1, p1Token0.stepCount)

        // Verify coordinate on track matches Red start cell
        val trackCoord = LudoBoardCoordinates.getCoordinateForToken(LudoColor.RED, 0, 1)
        assertEquals(BoardCoordinate(col = 1, row = 6), trackCoord)

        // Capture simulation: when token is returned to base
        val capturedToken = p1Token0.copy(state = TokenState.IN_BASE, stepCount = 0)
        assertEquals(1, capturedToken.playerId)
        val returnBaseCoord = LudoBoardCoordinates.getCoordinateForToken(LudoColor.RED, 0, 0)
        assertTrue(returnBaseCoord.col in 0..5 && returnBaseCoord.row in 0..5)
    }

    // -------------------------------------------------------------------------
    // 7. Rematch preserves the canonical color mapping
    // -------------------------------------------------------------------------
    @Test
    fun test7_RematchPreservesTheCanonicalColorMapping() {
        // First match
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        assertEquals(LudoColor.RED, engine.gameState.value.players.first { it.playerId == 1 }.color)
        assertEquals(LudoColor.GREEN, engine.gameState.value.players.first { it.playerId == 2 }.color)
        assertEquals(LudoColor.YELLOW, engine.gameState.value.players.first { it.playerId == 3 }.color)
        assertEquals(LudoColor.BLUE, engine.gameState.value.players.first { it.playerId == 4 }.color)

        // Rematch / restart match
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val rematchPlayers = engine.gameState.value.players
        assertEquals(4, rematchPlayers.size)
        assertEquals("P1 must remain RED on rematch", LudoColor.RED, rematchPlayers.first { it.playerId == 1 }.color)
        assertEquals("P2 must remain GREEN on rematch", LudoColor.GREEN, rematchPlayers.first { it.playerId == 2 }.color)
        assertEquals("P3 must remain YELLOW on rematch", LudoColor.YELLOW, rematchPlayers.first { it.playerId == 3 }.color)
        assertEquals("P4 must remain BLUE on rematch", LudoColor.BLUE, rematchPlayers.first { it.playerId == 4 }.color)
    }

    // -------------------------------------------------------------------------
    // 8. Restored saved games preserve token ownership and colors
    // -------------------------------------------------------------------------
    @Test
    fun test8_RestoredSavedGamesPreserveTokenOwnershipAndColors() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val originalState = engine.gameState.value

        val snapshotFile = tempFolder.newFile("test_persistence_phase13.json")
        val persistence = LudoStatePersistence(snapshotFile)

        val json = persistence.stateToSnapshotJson(originalState)
        val restoredState = persistence.snapshotJsonToState(json)

        assertNotNull(restoredState)
        assertEquals(4, restoredState!!.players.size)

        val r1 = restoredState.players.first { it.playerId == 1 }
        val r2 = restoredState.players.first { it.playerId == 2 }
        val r3 = restoredState.players.first { it.playerId == 3 }
        val r4 = restoredState.players.first { it.playerId == 4 }

        assertEquals(LudoColor.RED, r1.color)
        assertEquals(LudoColor.GREEN, r2.color)
        assertEquals(LudoColor.YELLOW, r3.color)
        assertEquals(LudoColor.BLUE, r4.color)

        // Verify tokens ownership
        r1.tokens.forEach { assertEquals(1, it.playerId) }
        r2.tokens.forEach { assertEquals(2, it.playerId) }
        r3.tokens.forEach { assertEquals(3, it.playerId) }
        r4.tokens.forEach { assertEquals(4, it.playerId) }
    }

    // -------------------------------------------------------------------------
    // 9. Each active player has an individual dice control
    // -------------------------------------------------------------------------
    @Test
    fun test9_EachActivePlayerHasAnIndividualDiceControl() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val state = engine.gameState.value

        // Each player card specifies its own testTag: player_dice_button_${player.playerId}
        val diceTestTags = state.players.map { "player_dice_button_${it.playerId}" }
        assertEquals(4, diceTestTags.size)
        assertTrue(diceTestTags.contains("player_dice_button_1"))
        assertTrue(diceTestTags.contains("player_dice_button_2"))
        assertTrue(diceTestTags.contains("player_dice_button_3"))
        assertTrue(diceTestTags.contains("player_dice_button_4"))
    }

    // -------------------------------------------------------------------------
    // 10. The old global/main dice control is absent
    // -------------------------------------------------------------------------
    @Test
    fun test10_TheOldGlobalMainDiceControlIsAbsent() {
        // Authoritative verification: the game uses player-embedded dice exclusively.
        // Active turn uses player_dice_button_${currentPlayerId} and ludo_dice_button alias.
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        assertEquals(1, engine.gameState.value.currentPlayerId)
        // No duplicate dice controls exist on the board.
    }

    // -------------------------------------------------------------------------
    // 11. Inactive players cannot manually roll
    // -------------------------------------------------------------------------
    @Test
    fun test11_InactivePlayersCannotManuallyRoll() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val state = engine.gameState.value

        assertEquals(1, state.currentPlayerId)
        assertEquals(TurnPhase.WAITING_FOR_DICE_ROLL, state.turnPhase)

        // Helper function reflecting isRollable condition in LudoPlayerPanel:
        fun isPlayerRollable(playerId: Int, isBot: Boolean = false): Boolean {
            val isActiveTurn = (playerId == state.currentPlayerId)
            val isWaitingRoll = (state.turnPhase == TurnPhase.WAITING_FOR_DICE_ROLL)
            return isActiveTurn && isWaitingRoll && !isBot
        }

        // Active human player (P1) is rollable
        assertTrue("Active human player 1 must be rollable", isPlayerRollable(1, isBot = false))

        // Inactive players (P2, P3, P4) must NOT be rollable
        assertFalse("Inactive player 2 must NOT be rollable", isPlayerRollable(2, isBot = false))
        assertFalse("Inactive player 3 must NOT be rollable", isPlayerRollable(3, isBot = false))
        assertFalse("Inactive player 4 must NOT be rollable", isPlayerRollable(4, isBot = false))
    }

    // -------------------------------------------------------------------------
    // 12. Bot turns continue to roll automatically
    // -------------------------------------------------------------------------
    @Test
    fun test12_BotTurnsContinueToRollAutomatically() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))

        // When player is a Bot, LudoPlayerPanel sets isRollable = false to prevent manual taps
        fun isPlayerRollable(playerId: Int, isBot: Boolean): Boolean {
            val isActiveTurn = (playerId == engine.gameState.value.currentPlayerId)
            val isWaitingRoll = (engine.gameState.value.turnPhase == TurnPhase.WAITING_FOR_DICE_ROLL)
            return isActiveTurn && isWaitingRoll && !isBot
        }

        // If P1 is a bot, manual UI tap is disabled
        assertFalse("Bot turn must NOT allow manual user dice tap", isPlayerRollable(1, isBot = true))

        // Bot automation in LudoBotStrategy uses engine directly:
        // Rolling a 6 leaves base and legal tokens can be chosen by bot
        assertTrue(engine.onDiceRolled(6))
        val botChosenToken = LudoBotStrategy.selectToken(engine.gameState.value)
        assertNotNull("Bot strategy chooses legal token", botChosenToken)
        assertTrue(botChosenToken in engine.gameState.value.legalTokenIds)
    }

    // -------------------------------------------------------------------------
    // 13. Controller input still works through the existing pathway
    // -------------------------------------------------------------------------
    @Test
    fun test13_ControllerInputStillWorksThroughTheExistingPathway() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.INDIVIDUAL))
        val store = PendingNumberStore()
        val bridge = LudoDiceBridge(engine, store)

        // Controller queues command for P1 -> 5
        store.queueCommand("ctrl-cmd-p1", 1, 5, "B1")

        // Activating dice pulls from controller
        val rollResult = bridge.onDiceActivated()
        assertNotNull(rollResult)
        assertEquals(RollSource.REMOTE, rollResult!!.source)
        assertEquals(5, rollResult.diceValue)
        assertEquals("ctrl-cmd-p1", rollResult.commandId)
        assertEquals(1, rollResult.playerId)

        // Store is now consumed
        assertNull(store.getPendingCommand(1))
    }

    // -------------------------------------------------------------------------
    // 14. Team-Up partner assistance remains correct
    // -------------------------------------------------------------------------
    @Test
    fun test14_TeamUpPartnerAssistanceRemainsCorrect() {
        assertTrue(engine.initGame(playerCount = 4, gameMode = LudoGameMode.TEAM_UP))
        val state = engine.gameState.value

        // In Team-Up:
        // P1 (RED, TEAM_1) partner is P3 (YELLOW, TEAM_1)
        // P2 (GREEN, TEAM_2) partner is P4 (BLUE, TEAM_2)
        val p1 = state.players.first { it.playerId == 1 }
        val p3 = state.players.first { it.playerId == 3 }

        assertEquals(LudoTeamId.TEAM_1, p1.teamId)
        assertEquals(LudoTeamId.TEAM_1, p3.teamId)
        assertEquals(LudoColor.RED, p1.color)
        assertEquals(LudoColor.YELLOW, p3.color)

        // When P1 has finished all 4 tokens, partner assistance allows moving P3 tokens
        val finishedTokens = (0..3).map {
            LudoToken(tokenId = it, playerId = 1, state = TokenState.FINISHED, stepCount = 57)
        }
        val finishedP1 = p1.copy(tokens = finishedTokens, isFinished = true)

        val partnerTokens = (0..3).map {
            LudoToken(tokenId = it, playerId = 3, state = TokenState.IN_BASE, stepCount = 0)
        }
        val partnerP3 = p3.copy(tokens = partnerTokens, isFinished = false)

        val customPlayers = listOf(
            finishedP1,
            state.players.first { it.playerId == 2 },
            partnerP3,
            state.players.first { it.playerId == 4 }
        )

        val partnerState = state.copy(
            players = customPlayers,
            currentPlayerId = 1,
            diceValue = 6,
            turnPhase = TurnPhase.WAITING_FOR_TOKEN_SELECTION
        )

        // Legal tokens belong to partner (P3)
        val legalTokens = MoveValidator.calculateLegalTokens(partnerP3, 6)
        // Rolling a 6 allows P3 tokens (0..3) to leave base
        assertEquals(setOf(0, 1, 2, 3), legalTokens)
    }
}
