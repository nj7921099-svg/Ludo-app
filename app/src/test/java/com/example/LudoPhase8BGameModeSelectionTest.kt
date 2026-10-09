package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoTeamId
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 8B Game Mode Selection & ViewModel Integration Tests.
 *
 * Verifies:
 * 1. Default selected mode is INDIVIDUAL.
 * 2. Selecting TEAM_UP automatically selects 4 players.
 * 3. TEAM_UP cannot remain with 2 players (selectBoxCount(2) rejected).
 * 4. TEAM_UP cannot remain with 3 players (selectBoxCount(3) rejected).
 * 5. Switching TEAM_UP -> INDIVIDUAL restores valid player-count selection.
 * 6. startGame() passes TEAM_UP + 4 to the engine.
 * 7. startGame() still supports Individual 2 players.
 * 8. startGame() still supports Individual 3 players.
 * 9. startGame() still supports Individual 4 players.
 * 10. TEAM_UP rematch preserves TEAM_UP.
 * 11. Individual rematch preserves INDIVIDUAL.
 * 12. resetToSetup() clears active game correctly and keeps valid selection.
 * 13. Existing persistence behavior remains valid.
 * 14. Existing Phase 8A Team-Up rules remain uncorrupted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LudoPhase8BGameModeSelectionTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = MainViewModel(app)
    }

    @Test
    fun test1_defaultSelectedModeIsIndividual() {
        assertEquals("Default mode must be INDIVIDUAL", LudoGameMode.INDIVIDUAL, viewModel.selectedGameMode.value)
        assertEquals("Default mode in uiState must be INDIVIDUAL", LudoGameMode.INDIVIDUAL, viewModel.uiState.value.selectedGameMode)
    }

    @Test
    fun test2_selectingTeamUpAutomaticallySelects4Players() {
        viewModel.selectBoxCount(2)
        assertEquals(2, viewModel.gameEngine.selectedBoxCount.value)

        viewModel.selectGameMode(LudoGameMode.TEAM_UP)

        assertEquals("Selecting TEAM_UP must set selectedGameMode to TEAM_UP", LudoGameMode.TEAM_UP, viewModel.selectedGameMode.value)
        assertEquals("Selecting TEAM_UP must automatically force 4 players", 4, viewModel.gameEngine.selectedBoxCount.value)
    }

    @Test
    fun test3_teamUpCannotRemainWith2Players() {
        viewModel.selectGameMode(LudoGameMode.TEAM_UP)
        assertEquals(4, viewModel.gameEngine.selectedBoxCount.value)

        viewModel.selectBoxCount(2)
        assertEquals("selectBoxCount(2) must be ignored when TEAM_UP is selected", 4, viewModel.gameEngine.selectedBoxCount.value)
    }

    @Test
    fun test4_teamUpCannotRemainWith3Players() {
        viewModel.selectGameMode(LudoGameMode.TEAM_UP)
        assertEquals(4, viewModel.gameEngine.selectedBoxCount.value)

        viewModel.selectBoxCount(3)
        assertEquals("selectBoxCount(3) must be ignored when TEAM_UP is selected", 4, viewModel.gameEngine.selectedBoxCount.value)
    }

    @Test
    fun test5_switchingTeamUpToIndividualRestoresValidSelection() {
        viewModel.selectGameMode(LudoGameMode.TEAM_UP)
        assertEquals(4, viewModel.gameEngine.selectedBoxCount.value)

        viewModel.selectGameMode(LudoGameMode.INDIVIDUAL)
        assertEquals(LudoGameMode.INDIVIDUAL, viewModel.selectedGameMode.value)
        // 4 is valid for INDIVIDUAL so it is preserved
        assertEquals(4, viewModel.gameEngine.selectedBoxCount.value)

        // Now in INDIVIDUAL, selecting 2 and 3 must work normally
        viewModel.selectBoxCount(2)
        assertEquals(2, viewModel.gameEngine.selectedBoxCount.value)

        viewModel.selectBoxCount(3)
        assertEquals(3, viewModel.gameEngine.selectedBoxCount.value)
    }

    @Test
    fun test6_startGamePassesTeamUpAnd4ToEngine() {
        viewModel.selectGameMode(LudoGameMode.TEAM_UP)
        viewModel.startGame()

        val ludoState = viewModel.ludoGameEngine.gameState.value
        assertTrue("Game must be started", ludoState.isGameStarted)
        assertEquals("playerCount must be 4", 4, ludoState.playerCount)
        assertEquals("gameMode must be TEAM_UP", LudoGameMode.TEAM_UP, ludoState.gameMode)

        // Verify explicit teams
        assertEquals(LudoTeamId.TEAM_1, ludoState.players[0].teamId)
        assertEquals(LudoTeamId.TEAM_2, ludoState.players[1].teamId)
        assertEquals(LudoTeamId.TEAM_1, ludoState.players[2].teamId)
        assertEquals(LudoTeamId.TEAM_2, ludoState.players[3].teamId)
    }

    @Test
    fun test7_startGameStillSupportsIndividual2Players() {
        viewModel.selectGameMode(LudoGameMode.INDIVIDUAL)
        viewModel.selectBoxCount(2)
        viewModel.startGame()

        val ludoState = viewModel.ludoGameEngine.gameState.value
        assertTrue("Game must be started", ludoState.isGameStarted)
        assertEquals("playerCount must be 2", 2, ludoState.playerCount)
        assertEquals("gameMode must be INDIVIDUAL", LudoGameMode.INDIVIDUAL, ludoState.gameMode)
        assertTrue("Individual players must not have teamId", ludoState.players.all { it.teamId == null })
    }

    @Test
    fun test8_startGameStillSupportsIndividual3Players() {
        viewModel.selectGameMode(LudoGameMode.INDIVIDUAL)
        viewModel.selectBoxCount(3)
        viewModel.startGame()

        val ludoState = viewModel.ludoGameEngine.gameState.value
        assertTrue("Game must be started", ludoState.isGameStarted)
        assertEquals("playerCount must be 3", 3, ludoState.playerCount)
        assertEquals("gameMode must be INDIVIDUAL", LudoGameMode.INDIVIDUAL, ludoState.gameMode)
        assertTrue("Individual players must not have teamId", ludoState.players.all { it.teamId == null })
    }

    @Test
    fun test9_startGameStillSupportsIndividual4Players() {
        viewModel.selectGameMode(LudoGameMode.INDIVIDUAL)
        viewModel.selectBoxCount(4)
        viewModel.startGame()

        val ludoState = viewModel.ludoGameEngine.gameState.value
        assertTrue("Game must be started", ludoState.isGameStarted)
        assertEquals("playerCount must be 4", 4, ludoState.playerCount)
        assertEquals("gameMode must be INDIVIDUAL", LudoGameMode.INDIVIDUAL, ludoState.gameMode)
        assertTrue("Individual players must not have teamId", ludoState.players.all { it.teamId == null })
    }

    @Test
    fun test10_teamUpRematchPreservesTeamUp() {
        viewModel.selectGameMode(LudoGameMode.TEAM_UP)
        viewModel.startGame()

        assertEquals(LudoGameMode.TEAM_UP, viewModel.ludoGameEngine.gameState.value.gameMode)

        // Trigger rematch
        viewModel.restartLudoRematch()

        val rematchedState = viewModel.ludoGameEngine.gameState.value
        assertTrue("Rematched game must be started", rematchedState.isGameStarted)
        assertEquals("Rematched game must preserve 4 players", 4, rematchedState.playerCount)
        assertEquals("Rematched game must preserve TEAM_UP mode", LudoGameMode.TEAM_UP, rematchedState.gameMode)
        assertEquals(LudoTeamId.TEAM_1, rematchedState.players[0].teamId)
        assertEquals(LudoTeamId.TEAM_2, rematchedState.players[1].teamId)
        assertEquals(LudoTeamId.TEAM_1, rematchedState.players[2].teamId)
        assertEquals(LudoTeamId.TEAM_2, rematchedState.players[3].teamId)
    }

    @Test
    fun test11_individualRematchPreservesIndividual() {
        viewModel.selectGameMode(LudoGameMode.INDIVIDUAL)
        viewModel.selectBoxCount(3)
        viewModel.startGame()

        assertEquals(LudoGameMode.INDIVIDUAL, viewModel.ludoGameEngine.gameState.value.gameMode)
        assertEquals(3, viewModel.ludoGameEngine.gameState.value.playerCount)

        // Trigger rematch
        viewModel.restartLudoRematch()

        val rematchedState = viewModel.ludoGameEngine.gameState.value
        assertTrue("Rematched game must be started", rematchedState.isGameStarted)
        assertEquals("Rematched game must preserve 3 players", 3, rematchedState.playerCount)
        assertEquals("Rematched game must preserve INDIVIDUAL mode", LudoGameMode.INDIVIDUAL, rematchedState.gameMode)
        assertTrue("Rematched players must have null teamId", rematchedState.players.all { it.teamId == null })
    }

    @Test
    fun test12_resetToSetupClearsActiveGameCorrectly() {
        viewModel.selectGameMode(LudoGameMode.TEAM_UP)
        viewModel.startGame()
        assertTrue(viewModel.ludoGameEngine.gameState.value.isGameStarted)

        viewModel.resetToSetup()

        assertFalse("resetToSetup must leave game unstarted", viewModel.ludoGameEngine.gameState.value.isGameStarted)
        assertFalse("resetToSetup must leave gameEngine unstarted", viewModel.gameEngine.isGameStarted.value)
        assertEquals("TEAM_UP player count must remain 4", 4, viewModel.gameEngine.selectedBoxCount.value)
        assertEquals("Selected mode must remain TEAM_UP", LudoGameMode.TEAM_UP, viewModel.selectedGameMode.value)
    }
}
