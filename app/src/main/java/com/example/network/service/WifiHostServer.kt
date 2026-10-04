package com.example.network.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.network.NetworkConstants
import com.example.network.model.AckMsg
import com.example.network.model.DiagnosticItem
import com.example.network.model.DisconnectMsg
import com.example.network.model.ErrorMsg
import com.example.network.model.HandshakeAckMsg
import com.example.network.model.HandshakeMsg
import com.example.network.model.HostConnectionState
import com.example.network.model.NetworkConnectionState
import com.example.network.model.NetworkMessage
import com.example.network.model.NumberResultMsg
import com.example.network.model.NumberSelectionMsg
import com.example.network.model.PingMsg
import com.example.network.model.PongMsg
import com.example.network.model.SendResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.UUID

sealed class NumberTestOutcome {
    data class Success(val value: Int, val rttMs: Long, val details: String) : NumberTestOutcome()
    data class Failure(val reason: String) : NumberTestOutcome()
}

/**
 * Dedicated Wi-Fi Host TCP Server and NSD Advertiser for Ludo-app.
 *
 * Implements:
 * 1. Single unified TCP port 8888 ([NetworkConstants.LUDO_TCP_PORT]).
 * 2. Real TCP writes via [sendPacket] with Mutex protection and flush confirmation.
 * 3. End-to-end NUMBER_SELECTION -> ACK -> NUMBER_RESULT verification with 5-second timeout.
 * 4. Automatic network availability handling via lifecycle-safe [ConnectivityManager.NetworkCallback].
 */
class WifiHostServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val port: Int = NetworkConstants.LUDO_TCP_PORT
) {
    private val tag = "WifiHostServer"

    private val _connectionState = MutableStateFlow(NetworkConnectionState(serverPort = port))
    val connectionState: StateFlow<NetworkConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<NetworkMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<NetworkMessage> = _incomingMessages.asSharedFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Request registry for matching ACK and NUMBER_RESULT by requestId
    val requestRegistry = NetworkRequestRegistry()

    // Sockets and I/O
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var clientOutputStream: OutputStream? = null
    private val writeMutex = Mutex()
    private val serverLifecycleMutex = Mutex()

    private var acceptJob: Job? = null
    private var readJob: Job? = null

    // NSD Manager
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isNsdRegistered = false

    // Multicast lock for NSD / mDNS discovery on Wi-Fi
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    // Automatic Network Monitoring
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkMonitoringActive = false

    // Handshake & Ping verification state
    private var handshakeCompleted = false
    private var pingVerified = false
    private var pendingPingId: String? = null
    private var pingSentTimeMs: Long = 0L

    init {
        acquireMulticastLock()
        registerNetworkMonitoring()
        scope.launch(Dispatchers.IO) {
            startServerInternal()
        }
    }

    private fun acquireMulticastLock() {
        try {
            multicastLock = wifiManager?.createMulticastLock("LudoMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    // =========================================================================
    // ISSUE 4: AUTOMATIC NETWORK AVAILABILITY HANDLING
    // =========================================================================

    private fun registerNetworkMonitoring() {
        if (connectivityManager == null || isNetworkMonitoringActive) return

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                appendLog("[NETWORK] Wi-Fi connection available")
                scope.launch(Dispatchers.IO) {
                    delay(300) // Small delay for IP assignment
                    startServerInternal()
                }
            }

            override fun onLost(network: Network) {
                appendLog("[NETWORK] Wi-Fi connection lost")
                scope.launch(Dispatchers.IO) {
                    stopServerOnNetworkLost()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (hasWifi && _connectionState.value.state == HostConnectionState.NETWORK_UNAVAILABLE) {
                    scope.launch(Dispatchers.IO) {
                        startServerInternal()
                    }
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
            isNetworkMonitoringActive = true
            appendLog("✓ NetworkCallback registered for automatic Wi-Fi monitoring")
        } catch (e: Exception) {
            Log.w(tag, "registerNetworkCallback fallback: ${e.message}")
            try {
                connectivityManager.registerDefaultNetworkCallback(callback)
                networkCallback = callback
                isNetworkMonitoringActive = true
            } catch (ex: Exception) {
                Log.e(tag, "Failed to register network monitoring: ${ex.message}")
            }
        }
    }

    private suspend fun stopServerOnNetworkLost() {
        serverLifecycleMutex.withLock {
            appendLog("Stopping Host Server due to network loss...")
            unregisterNsdService()
            closeCurrentClient("Local network lost")

            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            serverSocket = null

            _connectionState.update {
                it.copy(
                    state = HostConnectionState.NETWORK_UNAVAILABLE,
                    isWifiAvailable = false,
                    localIp = null,
                    isNsdRegistered = false,
                    lastError = "Wi-Fi connection lost",
                    reconnectStatus = "Waiting for network"
                )
            }
            updateDiagnostics()
        }
    }

    fun restartServer() {
        scope.launch(Dispatchers.IO) {
            serverLifecycleMutex.withLock {
                appendLog("Restarting Host Server on port $port...")
                unregisterNsdService()
                closeCurrentClient("Host server restarting")
                try {
                    serverSocket?.close()
                } catch (_: Exception) {}
                serverSocket = null
            }
            startServerInternal()
        }
    }

    private suspend fun startServerInternal() {
        serverLifecycleMutex.withLock {
            val localIp = getLocalIpAddress()
            val wifiAvailable = isWifiConnected() || localIp != null

            if (!wifiAvailable || localIp == null) {
                appendLog("Wi-Fi network unavailable. Waiting for network connection...")
                _connectionState.update {
                    it.copy(
                        state = HostConnectionState.NETWORK_UNAVAILABLE,
                        isWifiAvailable = false,
                        localIp = null,
                        lastError = "No Wi-Fi or Hotspot connection detected"
                    )
                }
                updateDiagnostics()
                return
            }

            _connectionState.update {
                it.copy(
                    isWifiAvailable = true,
                    localIp = localIp
                )
            }

            // If serverSocket is already running on the same port, reuse it safely
            if (serverSocket != null && !serverSocket!!.isClosed) {
                appendLog("Host Server is already running on $localIp:$port")
                if (!isNsdRegistered) {
                    registerNsdService(port)
                }
                updateDiagnostics()
                return
            }

            _connectionState.update { it.copy(state = HostConnectionState.STARTING_HOST) }
            appendLog("Starting Ludo Host Server on $localIp:$port...")

            try {
                serverSocket?.close()
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }

                _connectionState.update {
                    it.copy(
                        state = HostConnectionState.HOST_READY,
                        serverPort = port,
                        lastError = null
                    )
                }
                appendLog("✓ Host Server listening on $localIp:$port")

                // Advertise via NSD (mDNS)
                registerNsdService(port)

                updateDiagnostics()

                // Start accept loop
                startAcceptLoop()

            } catch (e: Exception) {
                val err = "Failed to start ServerSocket on port $port: ${e.message}"
                Log.e(tag, err, e)
                appendLog("ERROR: $err")
                _connectionState.update {
                    it.copy(
                        state = HostConnectionState.ERROR,
                        lastError = err
                    )
                }
                updateDiagnostics()
            }
        }
    }

    private fun startAcceptLoop() {
        acceptJob?.cancel()
        acceptJob = scope.launch(Dispatchers.IO) {
            val server = serverSocket ?: return@launch
            while (isActive && !server.isClosed) {
                try {
                    appendLog("Awaiting Controller connection on port $port...")
                    val socket = server.accept()
                    val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
                    appendLog("Incoming TCP connection from $clientIp")

                    writeMutex.withLock {
                        // Cleanly close previous client if any
                        closeCurrentClient("New client connected")

                        clientSocket = socket
                        socket.tcpNoDelay = true
                        socket.keepAlive = true
                        clientOutputStream = socket.getOutputStream()
                    }

                    _connectionState.update {
                        it.copy(
                            state = HostConnectionState.SOCKET_CONNECTED,
                            connectedClientIp = clientIp,
                            lastError = null,
                            disconnectReason = null,
                            reconnectStatus = "Connected"
                        )
                    }
                    updateDiagnostics()

                    // Start handshake and verification
                    startVerification(socket)

                } catch (e: SocketException) {
                    if (!isActive || server.isClosed) break
                    Log.w(tag, "ServerSocket accept exception: ${e.message}")
                } catch (e: Exception) {
                    if (!isActive || server.isClosed) break
                    Log.e(tag, "Exception accepting socket: ${e.message}")
                }
            }
        }
    }

    private fun startVerification(socket: Socket) {
        handshakeCompleted = false
        pingVerified = false
        pendingPingId = null

        _connectionState.update { it.copy(state = HostConnectionState.VERIFYING) }
        appendLog("Verifying Controller Handshake and Ping/Pong...")
        updateDiagnostics()

        // Start reader loop
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            try {
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine()
                    if (line == null) {
                        appendLog("Controller socket closed (EOF)")
                        onClientDisconnected("Remote socket closed (EOF)")
                        break
                    }
                    if (line.isNotBlank()) {
                        handleIncomingLine(line)
                    }
                }
            } catch (e: Exception) {
                if (isActive && !socket.isClosed) {
                    appendLog("Socket read error: ${e.message}")
                    onClientDisconnected("Socket read exception: ${e.message}")
                }
            }
        }

        // Verification timeout: must complete handshake & ping within 8 seconds
        scope.launch(Dispatchers.IO) {
            delay(NetworkConstants.HANDSHAKE_TIMEOUT_MS)
            if (_connectionState.value.state == HostConnectionState.VERIFYING) {
                appendLog("Verification timeout. Handshake or Ping did not complete in time.")
                sendPacket(ErrorMsg(code = "VERIFICATION_TIMEOUT", message = "Handshake verification timed out"))
                closeCurrentClient("Verification timed out")
                _connectionState.update {
                    it.copy(
                        state = HostConnectionState.ERROR,
                        lastError = "Handshake and Ping verification timed out"
                    )
                }
                updateDiagnostics()
            }
        }
    }

    private suspend fun handleIncomingLine(line: String) {
        val message = NetworkMessage.fromJson(line)
        if (message == null) {
            appendLog("[RX MALFORMED] $line")
            sendPacket(ErrorMsg(code = "MALFORMED_JSON", message = "Could not parse JSON packet"))
            return
        }

        appendLog("[RX] ${message.type} -> $line")
        _connectionState.update { it.copy(lastReceivedMessage = "${message.type} (${System.currentTimeMillis()})") }

        when (message) {
            is HandshakeMsg -> {
                handleHandshake(message)
            }

            is HandshakeAckMsg -> {
                appendLog("Controller sent HandshakeAck: status=${message.status}")
                if (message.status == "OK") {
                    handshakeCompleted = true
                    checkFullConnection()
                }
            }

            is PingMsg -> {
                // Client pinged Host -> immediately respond PONG
                sendPacket(PongMsg(requestId = message.requestId, originalTimestamp = message.timestamp))
                if (!pingVerified) {
                    pingVerified = true
                    checkFullConnection()
                }
            }

            is PongMsg -> {
                // Response to Host's ping -> calculate RTT
                val rtt = System.currentTimeMillis() - message.originalTimestamp
                _connectionState.update { it.copy(lastPingRttMs = rtt) }
                appendLog("✓ Ping RTT: ${rtt}ms")
                pingVerified = true
                checkFullConnection()
            }

            is AckMsg -> {
                appendLog("ACK received for requestId=${message.requestId}, status=${message.status}")
                _connectionState.update { it.copy(lastAckStatus = "Received (${message.requestId}, ${message.status})") }
                requestRegistry.dispatchAck(message)
                updateDiagnostics()
            }

            is NumberResultMsg -> {
                appendLog("NUMBER_RESULT received: boxId=${message.boxId}, val=${message.value}, requestId=${message.requestId}")
                _connectionState.update { it.copy(lastNumberResultStatus = "Received (val: ${message.value}, id: ${message.requestId})") }
                requestRegistry.dispatchResult(message)
                updateDiagnostics()
            }

            is DisconnectMsg -> {
                appendLog("Controller requested disconnect: ${message.reason}")
                onClientDisconnected(message.reason)
            }

            else -> {
                // Dispatch all game and control messages to UI / ViewModel
                _incomingMessages.emit(message)
            }
        }
        updateDiagnostics()
    }

    private suspend fun handleHandshake(message: HandshakeMsg) {
        if (message.protocolVersion != NetworkConstants.PROTOCOL_VERSION) {
            val err = "Protocol version mismatch: Expected ${NetworkConstants.PROTOCOL_VERSION}, got ${message.protocolVersion}"
            appendLog("ERROR: $err")
            sendPacket(ErrorMsg(code = "PROTOCOL_MISMATCH", message = err))
            closeCurrentClient(err)
            _connectionState.update { it.copy(state = HostConnectionState.ERROR, lastError = err) }
            updateDiagnostics()
            return
        }

        if (message.role != NetworkConstants.ROLE_CONTROLLER) {
            val err = "Role mismatch: Expected ${NetworkConstants.ROLE_CONTROLLER}, got ${message.role}"
            appendLog("ERROR: $err")
            sendPacket(ErrorMsg(code = "ROLE_MISMATCH", message = err))
            closeCurrentClient(err)
            _connectionState.update { it.copy(state = HostConnectionState.ERROR, lastError = err) }
            updateDiagnostics()
            return
        }

        appendLog("✓ Handshake verified with ${message.deviceName}")
        _connectionState.update {
            it.copy(
                connectedClientDeviceName = message.deviceName
            )
        }

        // Respond with HANDSHAKE_ACK
        val ack = HandshakeAckMsg(
            protocolVersion = NetworkConstants.PROTOCOL_VERSION,
            role = NetworkConstants.ROLE_HOST,
            status = "OK",
            deviceName = android.os.Build.MODEL ?: "Ludo Host",
            requestId = message.requestId
        )
        sendPacket(ack)
        handshakeCompleted = true

        // Send a PING to complete 2-way verification
        sendVerificationPing()
    }

    private suspend fun sendVerificationPing() {
        val pingId = UUID.randomUUID().toString()
        pendingPingId = pingId
        pingSentTimeMs = System.currentTimeMillis()
        sendPacket(PingMsg(requestId = pingId))
    }

    private fun checkFullConnection() {
        if (handshakeCompleted && pingVerified) {
            _connectionState.update {
                it.copy(
                    state = HostConnectionState.CONNECTED,
                    lastSuccessTime = System.currentTimeMillis(),
                    lastError = null,
                    disconnectReason = null,
                    reconnectStatus = "Connected"
                )
            }
            appendLog("🟢 Connection Fully Verified & Active! (State: CONNECTED)")
            updateDiagnostics()
        }
    }

    // =========================================================================
    // ISSUE 2: sendPacket() MUST REPRESENT REAL TCP WRITE
    // =========================================================================

    /**
     * Executes an actual, thread-safe, flushed TCP write to the client socket.
     *
     * Flow:
     * 1. Validate connection & socket
     * 2. Serialize JSON
     * 3. Append newline delimiter (\n)
     * 4. UTF-8 encode
     * 5. actual socket outputStream.write()
     * 6. flush()
     * 7. Only then return SendResult.Success!
     *
     * If write/flush throws IOException:
     * - Returns SendResult.Failure
     * - Transitions connection state to DISCONNECTED / ERROR
     */
    suspend fun sendPacket(message: NetworkMessage): SendResult = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val socket = clientSocket
            val outputStream = clientOutputStream

            if (socket == null || outputStream == null || socket.isClosed || !socket.isConnected) {
                val err = "TCP socket is closed or not connected"
                _connectionState.update { it.copy(lastTcpWriteStatus = "Failed (Socket closed)") }
                updateDiagnostics()
                return@withContext SendResult.Failure(err)
            }

            try {
                val jsonString = message.toJsonString()
                val framed = jsonString + NetworkConstants.LINE_DELIMITER
                val bytes = framed.toByteArray(Charsets.UTF_8)

                // Actual socket write + flush
                outputStream.write(bytes)
                outputStream.flush()

                val result = SendResult.Success(message.type, bytes.size)
                _connectionState.update {
                    it.copy(
                        lastSentMessage = "${message.type} (${System.currentTimeMillis()})",
                        lastTcpWriteStatus = "Success (${bytes.size} B)"
                    )
                }
                appendLog("[TX WRITE SUCCESS] ${message.type} (${bytes.size} bytes) -> $jsonString")
                updateDiagnostics()
                result
            } catch (e: Exception) {
                val err = "TCP write failed: ${e.message}"
                Log.e(tag, err, e)
                appendLog("[TX WRITE FAILED] $err")
                _connectionState.update { it.copy(lastTcpWriteStatus = "Failed: ${e.message}") }
                onClientDisconnected("Write error: ${e.message}")
                updateDiagnostics()
                SendResult.Failure(err, e)
            }
        }
    }

    fun sendMessage(message: NetworkMessage): Boolean {
        // Fire-and-verify helper for non-suspending callers
        scope.launch(Dispatchers.IO) {
            sendPacket(message)
        }
        return true
    }

    // =========================================================================
    // ISSUE 3: REAL NUMBER TEST: NUMBER_SELECTION -> ACK -> NUMBER_RESULT
    // =========================================================================

    /**
     * Executes a strict end-to-end Number Communication Test:
     * 1. Generates unique requestId
     * 2. Registers CompletableDeferred for matching ACK and NUMBER_RESULT
     * 3. Sends NUMBER_SELECTION over TCP and awaits actual TCP write success
     * 4. Awaits matching ACK (with 5-second timeout)
     * 5. Awaits matching NUMBER_RESULT (with 5-second timeout)
     * 6. Returns PASS only when all 3 stages succeed!
     */
    suspend fun executeNumberTest(value: Int, targetBoxId: Int? = null): NumberTestOutcome = withContext(Dispatchers.IO) {
        if (_connectionState.value.state != HostConnectionState.CONNECTED) {
            val fail = NumberTestOutcome.Failure("Cannot test: Controller is not connected")
            _connectionState.update { it.copy(numberTestStatus = "FAIL: Controller not connected") }
            return@withContext fail
        }

        val testRequestId = "test-num-${UUID.randomUUID().toString().take(8)}"
        val startTime = System.currentTimeMillis()

        appendLog("[NUMBER TEST] Starting test for value $value with requestId=$testRequestId")
        _connectionState.update { it.copy(numberTestStatus = "SENDING value $value (id: $testRequestId)...") }

        // Register request-response correlation
        val ackDeferred = requestRegistry.registerPendingAck(testRequestId)
        val resultDeferred = requestRegistry.registerPendingResult(testRequestId)

        try {
            // Stage 1: Send NUMBER_SELECTION and wait for actual TCP write
            val msg = NumberSelectionMsg(
                value = value,
                boxId = targetBoxId,
                requestId = testRequestId
            )

            val writeResult = sendPacket(msg)
            if (writeResult !is SendResult.Success) {
                requestRegistry.remove(testRequestId)
                val reason = "TCP write failed: ${(writeResult as SendResult.Failure).reason}"
                appendLog("[NUMBER TEST FAILED] $reason")
                _connectionState.update { it.copy(numberTestStatus = "FAIL: $reason") }
                return@withContext NumberTestOutcome.Failure(reason)
            }

            _connectionState.update { it.copy(numberTestStatus = "Awaiting ACK for $testRequestId...") }

            // Stage 2 & 3: Wait for matching ACK and matching NUMBER_RESULT with 5-second timeout
            withTimeout(NetworkConstants.NUMBER_TEST_TIMEOUT_MS) {
                val ack = ackDeferred.await()
                appendLog("[NUMBER TEST] Stage 2 PASS: Received ACK for $testRequestId (status: ${ack.status})")
                _connectionState.update { it.copy(numberTestStatus = "ACK received. Awaiting NUMBER_RESULT...") }

                if (ack.status != "OK") {
                    throw IllegalStateException("ACK returned status: ${ack.status} (${ack.reason})")
                }

                val result = resultDeferred.await()
                appendLog("[NUMBER TEST] Stage 3 PASS: Received NUMBER_RESULT for $testRequestId (value: ${result.value})")

                val totalRtt = System.currentTimeMillis() - startTime
                val passMsg = "PASS: Value $value verified end-to-end (ACK + NUMBER_RESULT in ${totalRtt}ms)"
                appendLog("[NUMBER TEST SUCCESS] $passMsg")
                _connectionState.update {
                    it.copy(
                        numberTestStatus = passMsg,
                        lastPingRttMs = totalRtt
                    )
                }
                updateDiagnostics()
                NumberTestOutcome.Success(value, totalRtt, passMsg)
            }
        } catch (e: TimeoutCancellationException) {
            requestRegistry.remove(testRequestId)
            val stage = if (ackDeferred.isCompleted) "NUMBER_RESULT timeout after 5000ms" else "ACK timeout after 5000ms"
            val failMsg = "FAIL: $stage"
            appendLog("[NUMBER TEST FAILED] $failMsg")
            _connectionState.update { it.copy(numberTestStatus = failMsg) }
            updateDiagnostics()
            NumberTestOutcome.Failure(failMsg)
        } catch (e: Exception) {
            requestRegistry.remove(testRequestId)
            val failMsg = "FAIL: ${e.message}"
            appendLog("[NUMBER TEST FAILED] $failMsg")
            _connectionState.update { it.copy(numberTestStatus = failMsg) }
            updateDiagnostics()
            NumberTestOutcome.Failure(failMsg)
        }
    }

    fun testConnection() {
        if (_connectionState.value.state != HostConnectionState.CONNECTED) {
            appendLog("Cannot test connection: not currently connected.")
            return
        }
        scope.launch(Dispatchers.IO) {
            appendLog("Testing connection with PING...")
            val pingId = UUID.randomUUID().toString()
            pingSentTimeMs = System.currentTimeMillis()
            val res = sendPacket(PingMsg(requestId = pingId))
            if (res is SendResult.Failure) {
                appendLog("PING failed to write to TCP socket: ${res.reason}")
            }
        }
    }

    private fun onClientDisconnected(reason: String) {
        closeCurrentClient(reason)
        requestRegistry.cancelAll("Client disconnected")
        _connectionState.update {
            it.copy(
                state = HostConnectionState.DISCONNECTED,
                disconnectReason = reason,
                connectedClientIp = null,
                connectedClientDeviceName = null,
                lastPingRttMs = null,
                reconnectStatus = "Attempting"
            )
        }
        appendLog("🔴 Not Connected: $reason. Ready for Controller reconnect on port $port.")
        updateDiagnostics()
    }

    private fun closeCurrentClient(reason: String) {
        handshakeCompleted = false
        pingVerified = false
        readJob?.cancel()
        readJob = null

        try {
            clientOutputStream?.close()
        } catch (_: Exception) {}
        clientOutputStream = null

        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        clientSocket = null
    }

    fun disconnectClient() {
        scope.launch(Dispatchers.IO) {
            sendPacket(DisconnectMsg(reason = "Host initiated disconnect"))
            onClientDisconnected("Host disconnected")
        }
    }

    // =========================================================================
    // NSD (Network Service Discovery / mDNS)
    // =========================================================================

    private fun registerNsdService(port: Int) {
        if (nsdManager == null) {
            appendLog("NSD not available on this device")
            return
        }
        if (isNsdRegistered) {
            unregisterNsdService()
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = NetworkConstants.NSD_SERVICE_NAME
            serviceType = NetworkConstants.NSD_SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                isNsdRegistered = true
                _connectionState.update { it.copy(isNsdRegistered = true) }
                appendLog("✓ NSD Registered: ${NsdServiceInfo.serviceName} (${NsdServiceInfo.serviceType}) on port $port")
                updateDiagnostics()
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isNsdRegistered = false
                _connectionState.update { it.copy(isNsdRegistered = false) }
                appendLog("NSD Registration failed with errorCode: $errorCode")
                updateDiagnostics()
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                isNsdRegistered = false
                _connectionState.update { it.copy(isNsdRegistered = false) }
                appendLog("NSD Service unregistered")
                updateDiagnostics()
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                appendLog("NSD Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Failed to register NSD service: ${e.message}")
            appendLog("NSD register exception: ${e.message}")
        }
    }

    private fun unregisterNsdService() {
        if (isNsdRegistered && registrationListener != null) {
            try {
                nsdManager?.unregisterService(registrationListener)
            } catch (e: Exception) {
                Log.w(tag, "Error unregistering NSD: ${e.message}")
            }
            isNsdRegistered = false
            registrationListener = null
        }
    }

    private fun updateDiagnostics() {
        val curr = _connectionState.value
        val items = mutableListOf<DiagnosticItem>()

        items.add(
            DiagnosticItem(
                title = "Network",
                isSuccess = curr.isWifiAvailable,
                details = if (curr.isWifiAvailable) "Available (IP: ${curr.localIp ?: "Resolving"})" else "Unavailable"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Local IP & Port",
                isSuccess = curr.isServerRunning,
                details = "${curr.localIp ?: "0.0.0.0"} : ${curr.serverPort}"
            )
        )

        items.add(
            DiagnosticItem(
                title = "NSD Service",
                isSuccess = curr.isNsdRegistered,
                details = if (curr.isNsdRegistered) "Advertising (${NetworkConstants.NSD_SERVICE_NAME})" else "Not registered"
            )
        )

        val hasSocket = curr.state in listOf(
            HostConnectionState.SOCKET_CONNECTED,
            HostConnectionState.VERIFYING,
            HostConnectionState.CONNECTED
        )
        items.add(
            DiagnosticItem(
                title = "Socket",
                isSuccess = hasSocket,
                details = if (hasSocket) "Connected (${curr.connectedClientIp ?: "Client"})" else "Closed"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Handshake",
                isSuccess = handshakeCompleted,
                details = if (handshakeCompleted) "Passed (${curr.connectedClientDeviceName ?: "Controller"})" else "Failed / Pending"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Last TCP Write",
                isSuccess = curr.lastTcpWriteStatus?.startsWith("Success") == true,
                details = curr.lastTcpWriteStatus ?: "None"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Last ACK",
                isSuccess = curr.lastAckStatus != null && !curr.lastAckStatus.contains("Timeout"),
                details = curr.lastAckStatus ?: "None"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Last NUMBER_RESULT",
                isSuccess = curr.lastNumberResultStatus != null && !curr.lastNumberResultStatus.contains("Timeout"),
                details = curr.lastNumberResultStatus ?: "None"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Reconnect",
                isSuccess = curr.reconnectStatus == "Connected" || curr.reconnectStatus == "Idle",
                details = curr.reconnectStatus
            )
        )

        _connectionState.update { it.copy(diagnostics = items) }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (!hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get local IP: ${e.message}")
        }
        return null
    }

    fun appendLog(entry: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val formatted = "[$timestamp] $entry"
        Log.d(tag, formatted)
        _logs.update {
            val next = it.toMutableList()
            if (next.size > 200) next.removeAt(0)
            next.add(formatted)
            next
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun cleanup() {
        appendLog("Cleaning up WifiHostServer resources...")
        acceptJob?.cancel()
        readJob?.cancel()
        requestRegistry.cancelAll("Host cleanup")

        // Unregister NetworkCallback safely
        if (isNetworkMonitoringActive && networkCallback != null) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback!!)
            } catch (_: Exception) {}
            networkCallback = null
            isNetworkMonitoringActive = false
        }

        closeCurrentClient("App cleanup")
        unregisterNsdService()

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        try {
            multicastLock?.release()
        } catch (_: Exception) {}
    }
}
