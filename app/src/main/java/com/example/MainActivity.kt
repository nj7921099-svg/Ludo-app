package com.example

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.BoxCountSelector
import com.example.ui.components.GameArena
import com.example.ui.components.ProtocolSpecsDialog
import com.example.ui.components.ScanDevicesDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TopControllerBar
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonDarkBg
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.TextMuted
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
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSettingsOpen by remember { mutableStateOf(false) }

    // Launcher for Bluetooth runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.retryPermissionsOrBluetooth()
        } else {
            Toast.makeText(context, "Bluetooth permissions are required to connect with Controller", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher to prompt user to enable Bluetooth if turned off
    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.retryPermissionsOrBluetooth()
    }

    // Check permissions on start
    LaunchedEffect(Unit) {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        permissionLauncher.launch(permissionsToRequest)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040209)),
        containerColor = Color(0xFF040209),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .testTag("main_screen_column"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Title: LUDO in glowing neon typography
            Text(
                text = "LUDO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    letterSpacing = 3.sp,
                    color = Color(0xFFF3E8FF)
                ),
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 14.dp)
                    .testTag("app_title_text")
            )

            // Top Row: CONTROLLER Card + Settings Button (top-right corner)
            TopControllerBar(
                connectionState = uiState.connectionState,
                onSettingsClick = { isSettingsOpen = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // SELECT PLAYERS Card
            BoxCountSelector(
                selectedCount = if (uiState.isGameStarted) uiState.activeBoxCount else uiState.selectedBoxCount,
                isGameStarted = uiState.isGameStarted,
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
                boxes = if (uiState.isGameStarted) {
                    uiState.boxes
                } else {
                    // Preview boxes before Start Game is tapped
                    (1..uiState.selectedBoxCount).map { id ->
                        com.example.game.model.BoxState(boxId = id)
                    }
                },
                activeBoxCount = if (uiState.isGameStarted) uiState.activeBoxCount else uiState.selectedBoxCount,
                onBoxTap = { boxId ->
                    if (!uiState.isGameStarted) {
                        viewModel.startGame()
                    } else {
                        viewModel.tapBox(boxId)
                    }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Navigation / Affordances: "Reset" on left, "Settings" on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6B5C82)
                    ),
                    modifier = Modifier
                        .clickable {
                            viewModel.resetToSetup()
                        }
                        .testTag("bottom_reset_button")
                )

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6B5C82)
                    ),
                    modifier = Modifier
                        .clickable {
                            isSettingsOpen = true
                        }
                        .testTag("bottom_settings_button")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Settings Dialog (Opened through top-right settings button or bottom Settings link)
    if (isSettingsOpen) {
        SettingsDialog(
            connectionState = uiState.connectionState,
            logs = logs,
            onScanClick = { viewModel.showScanDialog(true) },
            onMakeDiscoverableClick = {
                val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                context.startActivity(discoverableIntent)
            },
            onRestartListenerClick = { viewModel.bluetoothManager.startServerListener() },
            onDisconnectClick = { viewModel.disconnect() },
            onOpenProtocolSpecs = { viewModel.showProtocolInfo(true) },
            onClearLogs = { viewModel.bluetoothManager.clearLogs() },
            onResetGame = { viewModel.resetToSetup() },
            onDismiss = { isSettingsOpen = false }
        )
    }

    // Secondary Dialogs
    if (uiState.isScanDialogVisible) {
        ScanDevicesDialog(
            devices = discoveredDevices,
            isScanning = uiState.connectionState.isScanning,
            onDeviceSelected = { address ->
                viewModel.connectToDevice(address)
            },
            onRefreshScan = {
                viewModel.bluetoothManager.startScan()
            },
            onDismiss = {
                viewModel.showScanDialog(false)
            }
        )
    }

    if (uiState.isProtocolInfoVisible) {
        ProtocolSpecsDialog(
            onDismiss = { viewModel.showProtocolInfo(false) }
        )
    }
}
