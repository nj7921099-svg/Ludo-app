package com.example.network.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.example.network.model.PingMsg
import com.example.network.model.PongMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.UUID

/**
 * Dedicated Wi-Fi Host TCP Server and NSD Advertiser for Ludo-app.
 *
 * Responsibilities:
 * 1. Checks local Wi-Fi / Hotspot connectivity and resolves local IP.
 * 2. Starts TCP ServerSocket on central port [NetworkConstants.DEFAULT_PORT].
 * 3. Advertises service over Android NSD/mDNS (service: "LudoHost", type: "_ludo._tcp.").
 * 4. Manages client lifecycle: Accepts single Controller client cleanly, handles reconnects.
 * 5. Enforces state machine: Socket -> Verifying Handshake -> Ping/Pong -> CONNECTED.
 * 6. Line-based newline-delimited JSON message framing.
 * 7. Comprehensive connection diagnostics checklist.
 */
class WifiHostServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val port: Int = NetworkConstants.DEFAULT_PORT
) {
    private val tag = "WifiHostServer"

    private val _connectionState = MutableStateFlow(NetworkConnectionState(serverPort = port))
    val connectionState: StateFlow<NetworkConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<NetworkMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<NetworkMessage> = _incomingMessages.asSharedFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Sockets and I/O
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var socketWriter: PrintWriter? = null
    private var acceptJob: Job? = null
    private var readJob: Job? = null
    private var pingCheckJob: Job? = null

    // NSD Manager
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isNsdRegistered = false

    // Multicast lock for NSD / mDNS discovery on Wi-Fi
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    // Handshake & Ping verification state
    private var handshakeCompleted = false
    private var pingVerified = false
    private var pendingPingId: String? = null
    private var pingSentTimeMs: Long = 0L

    init {
        acquireMulticastLock()
        startServer()
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

    fun startServer() {
        scope.launch(Dispatchers.IO) {
            _connectionState.update { it.copy(state = HostConnectionState.STARTING_HOST) }
            appendLog("Starting Ludo Host Server on port $port...")

            val localIp = getLocalIpAddress()
            val wifiAvailable = isWifiConnected() || localIp != null

            if (!wifiAvailable || localIp == null) {
                appendLog("Network unavailable. Please connect to Controller's Wi-Fi hotspot.")
                _connectionState.update {
                    it.copy(
                        state = HostConnectionState.NETWORK_UNAVAILABLE,
                        isWifiAvailable = false,
                        localIp = null,
                        lastError = "No Wi-Fi or Hotspot connection detected"
                    )
                }
                updateDiagnostics()
                return@launch
            }

            _connectionState.update {
                it.copy(
                    isWifiAvailable = true,
                    localIp = localIp
                )
            }

            try {
                // Close existing server socket if any
                serverSocket?.close()
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }

                _connectionState.update {
                    it.copy(
                        state = HostConnectionState.HOST_READY,
                        serverPort = port
                    )
                }
                appendLog("Host Server listening on $localIp:$port")

                // Advertise via NSD (mDNS)
                registerNsdService(port)

                updateDiagnostics()

                // Begin accepting client connection
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
                    appendLog("Awaiting Controller client connection on port $port...")
                    val socket = server.accept()
                    val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
                    appendLog("Incoming TCP connection from $clientIp")

                    // Cleanly close previous client if any
                    closeCurrentClient("New client connected")

                    clientSocket = socket
                    socket.tcpNoDelay = true
                    socket.keepAlive = true

                    socketWriter = PrintWriter(
                        BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)),
                        true
                    )

                    _connectionState.update {
                        it.copy(
                            state = HostConnectionState.SOCKET_CONNECTED,
                            connectedClientIp = clientIp,
                            lastError = null,
                            disconnectReason = null
                        )
                    }
                    updateDiagnostics()

                    // Transition to VERIFYING
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

        // Timeout for verification: must complete handshake & ping within 8 seconds
        scope.launch(Dispatchers.IO) {
            delay(NetworkConstants.HANDSHAKE_TIMEOUT_MS)
            if (_connectionState.value.state == HostConnectionState.VERIFYING) {
                appendLog("Verification timeout. Handshake or Ping did not complete in time.")
                sendError("VERIFICATION_TIMEOUT", "Handshake and Ping/Pong verification timed out")
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
            sendError("MALFORMED_JSON", "Could not parse JSON packet")
            return
        }

        appendLog("[RX] ${message.type} -> $line")
        _connectionState.update { it.copy(lastReceivedMessage = "${message.type} (${System.currentTimeMillis()})") }

        when (message) {
            is HandshakeMsg -> {
                handleHandshake(message)
            }

            is HandshakeAckMsg -> {
                // If controller sends HandshakeAck
                appendLog("Controller sent HandshakeAck: status=${message.status}")
                if (message.status == "OK") {
                    handshakeCompleted = true
                    checkFullConnection()
                }
            }

            is PingMsg -> {
                // Client pinged Host -> immediately respond PONG
                sendMessage(PongMsg(requestId = message.requestId, originalTimestamp = message.timestamp))
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

    private fun handleHandshake(message: HandshakeMsg) {
        if (message.protocolVersion != NetworkConstants.PROTOCOL_VERSION) {
            val err = "Protocol version mismatch: Expected ${NetworkConstants.PROTOCOL_VERSION}, got ${message.protocolVersion}"
            appendLog("ERROR: $err")
            sendError("PROTOCOL_MISMATCH", err)
            closeCurrentClient(err)
            _connectionState.update { it.copy(state = HostConnectionState.ERROR, lastError = err) }
            updateDiagnostics()
            return
        }

        if (message.role != NetworkConstants.ROLE_CONTROLLER) {
            val err = "Role mismatch: Expected ${NetworkConstants.ROLE_CONTROLLER}, got ${message.role}"
            appendLog("ERROR: $err")
            sendError("ROLE_MISMATCH", err)
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
        sendMessage(ack)
        handshakeCompleted = true

        // Send a PING to complete 2-way verification
        sendVerificationPing()
    }

    private fun sendVerificationPing() {
        val pingId = UUID.randomUUID().toString()
        pendingPingId = pingId
        pingSentTimeMs = System.currentTimeMillis()
        sendMessage(PingMsg(requestId = pingId))
    }

    /**
     * Enforces the critical rule:
     * Only show CONNECTED after:
     * TCP socket established + valid HANDSHAKE received + HANDSHAKE_ACK completed + PING/PONG successful!
     */
    private fun checkFullConnection() {
        if (handshakeCompleted && pingVerified) {
            _connectionState.update {
                it.copy(
                    state = HostConnectionState.CONNECTED,
                    lastSuccessTime = System.currentTimeMillis(),
                    lastError = null,
                    disconnectReason = null
                )
            }
            appendLog("🟢 Connection Fully Verified & Active! (State: CONNECTED)")
            updateDiagnostics()
        }
    }

    fun testConnection() {
        if (_connectionState.value.state != HostConnectionState.CONNECTED) {
            appendLog("Cannot test connection: not currently connected.")
            return
        }
        appendLog("Testing connection with PING...")
        val pingId = UUID.randomUUID().toString()
        pingSentTimeMs = System.currentTimeMillis()
        sendMessage(PingMsg(requestId = pingId))
    }

    fun sendMessage(message: NetworkMessage): Boolean {
        return try {
            val writer = socketWriter ?: return false
            val jsonLine = message.toJsonString() + NetworkConstants.LINE_DELIMITER
            synchronized(writer) {
                writer.print(jsonLine)
                writer.flush()
            }
            _connectionState.update { it.copy(lastSentMessage = "${message.type} (${System.currentTimeMillis()})") }
            appendLog("[TX] ${message.type} -> ${message.toJsonString()}")
            true
        } catch (e: Exception) {
            appendLog("Failed to send ${message.type}: ${e.message}")
            false
        }
    }

    fun sendError(code: String, message: String) {
        sendMessage(ErrorMsg(code = code, message = message))
    }

    private fun onClientDisconnected(reason: String) {
        closeCurrentClient(reason)
        _connectionState.update {
            it.copy(
                state = HostConnectionState.DISCONNECTED,
                disconnectReason = reason,
                connectedClientIp = null,
                connectedClientDeviceName = null,
                lastPingRttMs = null
            )
        }
        appendLog("🔴 Not Connected: $reason. Ready for Controller reconnect.")
        updateDiagnostics()
    }

    private fun closeCurrentClient(reason: String) {
        handshakeCompleted = false
        pingVerified = false
        readJob?.cancel()
        readJob = null
        try {
            socketWriter?.close()
        } catch (_: Exception) {}
        socketWriter = null
        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        clientSocket = null
    }

    fun disconnectClient() {
        sendMessage(DisconnectMsg(reason = "Host initiated disconnect"))
        onClientDisconnected("Host disconnected")
    }

    fun restartServer() {
        appendLog("Restarting Ludo Host Server...")
        closeCurrentClient("Server restarted")
        unregisterNsdService()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        startServer()
    }

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
                appendLog("✓ NSD Service Registered: ${NsdServiceInfo.serviceName} (${NsdServiceInfo.serviceType}) on port $port")
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
                title = "Wi-Fi / Network Available",
                isSuccess = curr.isWifiAvailable,
                details = if (curr.isWifiAvailable) "IP: ${curr.localIp}" else "Connect to Controller hotspot"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Host Server Started",
                isSuccess = curr.isServerRunning,
                details = if (curr.isServerRunning) "Port: ${curr.serverPort}" else "Server not running"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Service Advertised (NSD)",
                isSuccess = curr.isNsdRegistered,
                details = if (curr.isNsdRegistered) "mDNS: ${NetworkConstants.NSD_SERVICE_NAME}" else "Unregistered"
            )
        )

        val hasSocket = curr.state in listOf(
            HostConnectionState.SOCKET_CONNECTED,
            HostConnectionState.VERIFYING,
            HostConnectionState.CONNECTED
        )
        items.add(
            DiagnosticItem(
                title = "TCP Connection",
                isSuccess = hasSocket,
                details = if (hasSocket) "Client IP: ${curr.connectedClientIp}" else "Awaiting client connection"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Handshake Verification",
                isSuccess = handshakeCompleted,
                details = if (handshakeCompleted) "Role: ${curr.connectedClientDeviceName ?: "Controller"}" else "Pending"
            )
        )

        items.add(
            DiagnosticItem(
                title = "Ping/Pong Verification",
                isSuccess = pingVerified,
                details = if (pingVerified) "RTT: ${curr.lastPingRttMs ?: 0}ms" else "Pending"
            )
        )

        _connectionState.update { it.copy(diagnostics = items) }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                // Prefer wlan0 / Wi-Fi interfaces
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
        acceptJob?.cancel()
        readJob?.cancel()
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
