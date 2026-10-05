package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.components.BoxCountSelector
import com.example.ui.components.ConnectScreen
import com.example.ui.components.GameArena
import com.example.ui.components.GlobalConnectionIndicator
import com.example.ui.components.ProtocolSpecsDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TopControllerBar
import com.example.ui.components.ludo.LudoGameBoard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonDarkBg
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSettingsOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040209)),
        containerColor = Color(0xFF040209),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Sleek bottom navigation bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0C0A18),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E1A33))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Game Tab Button
                    Row(
                        modifier = Modifier
                            .clickable { viewModel.setTab(AppTab.GAME) }
                            .padding(8.dp)
                            .testTag("nav_tab_game"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Game",
                            tint = if (uiState.currentTab == AppTab.GAME) NeonPurpleBright else Color(0xFF6B5C82),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "Game",
                            fontWeight = if (uiState.currentTab == AppTab.GAME) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentTab == AppTab.GAME) TextPrimary else Color(0xFF6B5C82),
                            fontSize = 13.sp
                        )
                    }

                    // Connect Tab Button
                    Row(
                        modifier = Modifier
                            .clickable { viewModel.setTab(AppTab.CONNECT) }
                            .padding(8.dp)
                            .testTag("nav_tab_connect"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = "Connect",
                            tint = if (uiState.currentTab == AppTab.CONNECT) NeonPurpleBright else Color(0xFF6B5C82),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "Connect",
                            fontWeight = if (uiState.currentTab == AppTab.CONNECT) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentTab == AppTab.CONNECT) TextPrimary else Color(0xFF6B5C82),
                            fontSize = 13.sp
                        )
                    }

                    // Reset Game Button
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6B5C82)
                        ),
                        modifier = Modifier
                            .clickable { viewModel.resetToSetup() }
                            .padding(8.dp)
                            .testTag("bottom_reset_button")
                    )

                    // Settings Button
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6B5C82)
                        ),
                        modifier = Modifier
                            .clickable { isSettingsOpen = true }
                            .padding(8.dp)
                            .testTag("bottom_settings_button")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Bar with GLOBAL CONNECTION INDICATOR in the TOP-LEFT CORNER!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left: Global Connection Indicator
                GlobalConnectionIndicator(
                    connectionState = uiState.connectionState,
                    onIndicatorClick = {
                        viewModel.setTab(AppTab.CONNECT)
                    }
                )

                // Top Title: LUDO in glowing neon typography
                Text(
                    text = "LUDO",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        letterSpacing = 3.sp,
                        color = Color(0xFFF3E8FF)
                    ),
                    modifier = Modifier.testTag("app_title_text")
                )

                Spacer(modifier = Modifier.width(60.dp)) // balance indicator
            }

            AnimatedContent(
                targetState = uiState.currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_animation"
            ) { tab ->
                when (tab) {
                    AppTab.GAME -> {
                        // Game Screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .testTag("main_screen_column"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Row: LUDO HOST & CONTROLLER Card + Settings Button
                            TopControllerBar(
                                connectionState = uiState.connectionState,
                                onStatusCardClick = { viewModel.setTab(AppTab.CONNECT) },
                                onSettingsClick = { isSettingsOpen = true }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            if (!uiState.isGameStarted) {
                                // SELECT PLAYERS Card
                                BoxCountSelector(
                                    selectedCount = uiState.selectedBoxCount,
                                    isGameStarted = false,
                                    onCountSelected = { count ->
                                        viewModel.selectBoxCount(count)
                                    },
                                    onStartGame = {
                                        viewModel.startGame()
                                    }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Dynamic Game Arena: R1..Rn with clockwise arrows
                                GameArena(
                                    boxes = (1..uiState.selectedBoxCount).map { id ->
                                        com.example.game.model.BoxState(boxId = id)
                                    },
                                    activeBoxCount = uiState.selectedBoxCount,
                                    onBoxTap = {
                                        viewModel.startGame()
                                    }
                                )
                            } else {
                                // Real Playable Ludo Game Board UI
                                LudoGameBoard(
                                    gameState = uiState.ludoGameState,
                                    pendingCommands = uiState.pendingNumbers,
                                    onDiceClick = {
                                        viewModel.activateLudoDice()
                                    },
                                    onTokenClick = { tokenId ->
                                        viewModel.moveLudoToken(tokenId)
                                    },
                                    onNewGame = {
                                        viewModel.restartLudoRematch()
                                    },
                                    onBackToSetup = {
                                        viewModel.resetToSetup()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Last roll summary
                            if (uiState.lastRollSummary != null) {
                                Text(
                                    text = uiState.lastRollSummary!!,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }

                    AppTab.CONNECT -> {
                        // Dedicated Connect Tab
                        ConnectScreen(
                            connectionState = uiState.connectionState,
                            logs = logs,
                            onTestConnection = { viewModel.testConnection() },
                            onRestartServer = { viewModel.restartHostServer() },
                            onDisconnectClient = { viewModel.disconnectClient() },
                            onSimulateNumberTest = { num -> viewModel.simulateNumberTest(num) },
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (isSettingsOpen) {
        SettingsDialog(
            connectionState = uiState.connectionState,
            logs = logs,
            onRestartServerClick = { viewModel.restartHostServer() },
            onDisconnectClick = { viewModel.disconnectClient() },
            onOpenProtocolSpecs = { viewModel.showProtocolInfo(true) },
            onOpenConnectTab = { viewModel.setTab(AppTab.CONNECT) },
            onClearLogs = { viewModel.clearLogs() },
            onResetGame = { viewModel.resetToSetup() },
            onDismiss = { isSettingsOpen = false }
        )
    }

    // Protocol Specs Dialog
    if (uiState.isProtocolInfoVisible) {
        ProtocolSpecsDialog(
            onDismiss = { viewModel.showProtocolInfo(false) }
        )
    }
}
