package com.example.bluetooth.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.bluetooth.BluetoothConstants
import com.example.bluetooth.model.BluetoothConnectionState
import com.example.bluetooth.model.BluetoothMessage
import com.example.bluetooth.model.BluetoothStatusType
import com.example.bluetooth.model.DiscoveredBluetoothDevice
import com.example.bluetooth.model.ErrorMessage
import com.example.bluetooth.model.HandshakeAckMessage
import com.example.bluetooth.model.HandshakeMessage
import com.example.bluetooth.model.PingMessage
import com.example.bluetooth.model.PongMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    companion object {
        private const val TAG = "BluetoothConnManager"
        private const val MAX_LOGS = 60
        private const val MAX_SEEN_REQUESTS = 100
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(BluetoothConnectionState())
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<BluetoothMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<BluetoothMessage> = _incomingMessages.asSharedFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var serverJob: Job? = null
    private var clientConnectJob: Job? = null
    private var communicationJob: Job? = null
    private var pingJob: Job? = null

    private var activeSocket: BluetoothSocket? = null
    private var activeServerSocket: BluetoothServerSocket? = null
    private var socketWriter: BufferedWriter? = null

    private val isRunning = AtomicBoolean(true)
    private var isReceiverRegistered = false

    // Cache to deduplicate duplicate requestIds
    private val processedRequestIds = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(MAX_SEEN_REQUESTS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > MAX_SEEN_REQUESTS
            }
        }
    )

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    device?.let {
                        val name = try {
                            if (hasConnectPermission()) it.name ?: "Unknown Device" else "Unknown Device"
                        } catch (e: Exception) {
                            "Unknown Device"
                        }
                        val address = it.address
                        val isBonded = try {
                            if (hasConnectPermission()) it.bondState == BluetoothDevice.BOND_BONDED else false
                        } catch (e: Exception) {
                            false
                        }

                        val foundItem = DiscoveredBluetoothDevice(name = name, address = address, isBonded = isBonded)
                        _discoveredDevices.update { list ->
                            if (list.none { d -> d.address == address }) {
                                list + foundItem
                            } else {
                                list
                            }
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _connectionState.update { it.copy(isScanning = false) }
                    appendLog("Device scanning finished")
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            appendLog("Bluetooth disabled by system/user")
                            _connectionState.update {
                                it.copy(
                                    status = BluetoothStatusType.BLUETOOTH_OFF,
                                    statusMessage = "Bluetooth is turned off"
                                )
                            }
                            disconnectCurrent(closeServer = true)
                        }

                        BluetoothAdapter.STATE_ON -> {
                            appendLog("Bluetooth enabled. Starting server listener...")
                            checkAndInitialize()
                        }
                    }
                }
            }
        }
    }

    init {
        registerReceiver()
        checkAndInitialize()
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkAndInitialize() {
        if (bluetoothAdapter == null) {
            _connectionState.update {
                it.copy(
                    status = BluetoothStatusType.ERROR,
                    statusMessage = "Device does not support Bluetooth",
                    lastError = "Bluetooth hardware not available"
                )
            }
            appendLog("Error: Bluetooth hardware not available")
            return
        }

        if (!hasConnectPermission()) {
            _connectionState.update {
                it.copy(
                    status = BluetoothStatusType.PERMISSIONS_REQUIRED,
                    statusMessage = "Bluetooth permissions required"
                )
            }
            appendLog("Waiting for Bluetooth permissions...")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            _connectionState.update {
                it.copy(
                    status = BluetoothStatusType.BLUETOOTH_OFF,
                    statusMessage = "Bluetooth is currently turned off"
                )
            }
            appendLog("Bluetooth is OFF. Turn ON Bluetooth to connect.")
            return
        }

        // Bluetooth is on and permissions are granted:
        // Automatically start server socket listener so App 2 can discover and connect immediately!
        refreshPairedDevices()
        startServerListener()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (!hasConnectPermission() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        try {
            val paired = bluetoothAdapter.bondedDevices?.map { device ->
                DiscoveredBluetoothDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address,
                    isBonded = true
                )
            } ?: emptyList()

            _discoveredDevices.update { current ->
                val nonBonded = current.filter { !it.isBonded }
                paired + nonBonded
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load paired devices", e)
        }
    }

    /**
     * Start RFCOMM Server listener.
     * App 1 listens for connections from App 2.
     */
    @SuppressLint("MissingPermission")
    fun startServerListener() {
        if (!hasConnectPermission() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        if (_connectionState.value.status == BluetoothStatusType.CONNECTED) {
            return
        }

        serverJob?.cancel()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                activeServerSocket?.close()
            } catch (_: Exception) {}

            appendLog("Starting RFCOMM Server listener for App 2...")
            withContext(Dispatchers.Main) {
                _connectionState.update {
                    it.copy(
                        status = BluetoothStatusType.LISTENING,
                        statusMessage = "Ready and waiting for App 2 to connect...",
                        isServerListening = true,
                        lastError = null
                    )
                }
            }

            var serverSocket: BluetoothServerSocket? = null
            try {
                // Try custom app UUID first, fallback to standard SPP UUID
                serverSocket = try {
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        BluetoothConstants.SERVICE_NAME,
                        BluetoothConstants.APP_SERVICE_UUID
                    )
                } catch (e: Exception) {
                    appendLog("Using standard SPP service record for server...")
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        BluetoothConstants.SERVICE_NAME,
                        BluetoothConstants.STANDARD_SPP_UUID
                    )
                }

                activeServerSocket = serverSocket

                while (isActive) {
                    appendLog("Server listening on RFCOMM socket...")
                    val socket: BluetoothSocket = try {
                        serverSocket.accept()
                    } catch (e: IOException) {
                        if (!isActive) break
                        appendLog("Server accept interrupted: ${e.message}")
                        break
                    }

                    // A remote device (App 2) connected!
                    val remoteDevice = socket.remoteDevice
                    val deviceName = try {
                        remoteDevice.name ?: remoteDevice.address
                    } catch (e: Exception) {
                        remoteDevice.address
                    }

                    appendLog("Accepted connection from: $deviceName (${remoteDevice.address})")

                    // Hand off to connected session
                    handleConnectedSocket(socket, deviceName, remoteDevice.address)
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server listener failed", e)
                appendLog("Server listener error: ${e.localizedMessage}")
                withContext(Dispatchers.Main) {
                    _connectionState.update {
                        it.copy(
                            isServerListening = false,
                            lastError = e.localizedMessage
                        )
                    }
                }
            }
        }
    }

    /**
     * Connect as client to App 2 device (e.g., from paired list or scanned device).
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        if (!hasConnectPermission() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            appendLog("Cannot connect: permissions missing or Bluetooth off")
            return
        }

        clientConnectJob?.cancel()
        clientConnectJob = scope.launch(Dispatchers.IO) {
            stopScan()
            val device = try {
                bluetoothAdapter.getRemoteDevice(deviceAddress)
            } catch (e: Exception) {
                appendLog("Invalid Bluetooth address: $deviceAddress")
                return@launch
            }

            val deviceName = try { device.name ?: deviceAddress } catch (_: Exception) { deviceAddress }

            withContext(Dispatchers.Main) {
                _connectionState.update {
                    it.copy(
                        status = BluetoothStatusType.CONNECTING,
                        statusMessage = "Connecting to $deviceName...",
                        connectedDeviceName = deviceName,
                        connectedDeviceAddress = deviceAddress
                    )
                }
            }
            appendLog("Initiating connection to $deviceName ($deviceAddress)...")

            var socket: BluetoothSocket? = null
            var connected = false

            // Try UUID 1: App custom UUID
            try {
                socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.APP_SERVICE_UUID)
                socket.connect()
                connected = true
                appendLog("Connected using App Service UUID")
            } catch (e: Exception) {
                appendLog("App UUID failed (${e.message}), trying standard SPP...")
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }

            // Fallback to Standard SPP UUID
            if (!connected) {
                try {
                    socket = device.createRfcommSocketToServiceRecord(BluetoothConstants.STANDARD_SPP_UUID)
                    socket.connect()
                    connected = true
                    appendLog("Connected using Standard SPP UUID")
                } catch (e: Exception) {
                    appendLog("Standard SPP failed: ${e.message}")
                    try {
                        socket?.close()
                    } catch (_: Exception) {}
                }
            }

            // Insecure fallback if supported
            if (!connected) {
                try {
                    socket = device.createInsecureRfcommSocketToServiceRecord(BluetoothConstants.STANDARD_SPP_UUID)
                    socket.connect()
                    connected = true
                    appendLog("Connected using Insecure SPP")
                } catch (e: Exception) {
                    appendLog("Insecure SPP failed: ${e.message}")
                    try {
                        socket?.close()
                    } catch (_: Exception) {}
                }
            }

            if (connected && socket != null) {
                handleConnectedSocket(socket, deviceName, deviceAddress)
            } else {
                appendLog("Connection to $deviceName failed")
                withContext(Dispatchers.Main) {
                    _connectionState.update {
                        it.copy(
                            status = BluetoothStatusType.ERROR,
                            statusMessage = "Connection failed to $deviceName",
                            lastError = "Could not establish RFCOMM channel"
                        )
                    }
                }
                delay(2000)
                startServerListener()
            }
        }
    }

    private suspend fun handleConnectedSocket(
        socket: BluetoothSocket,
        deviceName: String,
        deviceAddress: String
    ) {
        disconnectCurrent(closeServer = false)

        activeSocket = socket
        socketWriter = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

        withContext(Dispatchers.Main) {
            _connectionState.update {
                it.copy(
                    status = BluetoothStatusType.CONNECTED,
                    statusMessage = "Connected to $deviceName",
                    connectedDeviceName = deviceName,
                    connectedDeviceAddress = deviceAddress,
                    lastError = null,
                    isServerListening = false
                )
            }
        }
        appendLog("✓ Bluetooth Channel established with $deviceName")

        // Start bidirectional communication coroutine
        communicationJob = scope.launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        processIncomingLine(line)
                    }
                }
            } catch (e: IOException) {
                appendLog("Connection lost: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    appendLog("Disconnected from $deviceName")
                    _connectionState.update {
                        it.copy(
                            status = BluetoothStatusType.DISCONNECTED,
                            statusMessage = "Disconnected from $deviceName",
                            connectedDeviceName = null,
                            connectedDeviceAddress = null
                        )
                    }
                }
                // Automatically restart server listener so App 2 can reconnect!
                delay(1500)
                startServerListener()
            }
        }
    }

    private suspend fun processIncomingLine(line: String) {
        val message = BluetoothMessage.fromJson(line)
        if (message == null) {
            appendLog("[RX ERROR] Malformed message: $line")
            sendMessage(ErrorMessage(code = "MALFORMED_JSON", message = "Could not parse message"))
            return
        }

        // Deduplication check for legacy roll requests
        val reqId = when (message) {
            is com.example.bluetooth.model.RequestRandomMessage -> message.requestId
            is com.example.bluetooth.model.ConfigUpdateMessage -> message.requestId
            is com.example.bluetooth.model.ResetMessage -> message.requestId
            else -> null
        }

        if (reqId != null) {
            val seenTime = processedRequestIds[reqId]
            val now = System.currentTimeMillis()
            if (seenTime != null && now - seenTime < 10000L) {
                appendLog("[RX DUP] Ignored duplicate request: $reqId")
                return
            }
            processedRequestIds[reqId] = now
        }

        when (message) {
            is PingMessage -> {
                sendMessage(PongMessage())
            }

            is PongMessage -> {
                // Heartbeat response received
            }

            else -> {
                appendLog("[RX] ${message.type} -> $line")
                _incomingMessages.emit(message)
            }
        }
    }

    /**
     * Send a structured Bluetooth message to connected App 2.
     */
    fun sendMessage(message: BluetoothMessage): Boolean {
        val jsonStr = message.toJsonString()
        return sendRaw(jsonStr)
    }

    /**
     * Low-level send over RFCOMM socket.
     */
    @Synchronized
    fun sendRaw(data: String): Boolean {
        val writer = socketWriter ?: return false
        val socket = activeSocket ?: return false
        if (!socket.isConnected) return false

        return try {
            writer.write(data + BluetoothConstants.MESSAGE_DELIMITER)
            writer.flush()
            val logSummary = if (data.length > 90) data.take(90) + "..." else data
            appendLog("[TX] $logSummary")
            true
        } catch (e: Exception) {
            appendLog("[TX FAIL] Failed to send: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasScanPermission() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            appendLog("Cannot scan: permission missing or Bluetooth OFF")
            return
        }

        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            refreshPairedDevices()
            val started = bluetoothAdapter.startDiscovery()
            _connectionState.update { it.copy(isScanning = started) }
            appendLog(if (started) "Bluetooth device discovery started..." else "Failed to start discovery")
        } catch (e: Exception) {
            appendLog("Scan error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            if (hasScanPermission() && bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (_: Exception) {}
        _connectionState.update { it.copy(isScanning = false) }
    }

    fun disconnectCurrent(closeServer: Boolean = false) {
        communicationJob?.cancel()
        clientConnectJob?.cancel()
        pingJob?.cancel()

        try {
            socketWriter?.close()
        } catch (_: Exception) {}
        socketWriter = null

        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null

        if (closeServer) {
            serverJob?.cancel()
            try {
                activeServerSocket?.close()
            } catch (_: Exception) {}
            activeServerSocket = null
        }
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
        }
    }

    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    fun appendLog(entry: String) {
        val time = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        val formatted = "[$time] $entry"
        Log.d(TAG, formatted)
        _logs.update { list ->
            val updated = list + formatted
            if (updated.size > MAX_LOGS) updated.takeLast(MAX_LOGS) else updated
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun cleanup() {
        isRunning.set(false)
        unregisterReceiver()
        disconnectCurrent(closeServer = true)
        scope.cancel()
    }
}
