package com.example.network.model

/**
 * Explicit Connection State Machine required by Ludo-app architecture.
 *
 * Rules:
 * The UI must NEVER display "Connected" merely because a TCP socket opened.
 * Only transition to CONNECTED after:
 * TCP socket established + valid HANDSHAKE received + HANDSHAKE_ACK completed + PING/PONG successful!
 */
enum class HostConnectionState {
    IDLE,
    NETWORK_UNAVAILABLE,
    STARTING_HOST,
    HOST_READY,
    DISCOVERED,
    CONNECTING,
    SOCKET_CONNECTED,
    VERIFYING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    ERROR
}

data class DiagnosticItem(
    val title: String,
    val isSuccess: Boolean,
    val details: String = ""
)

data class NetworkConnectionState(
    val state: HostConnectionState = HostConnectionState.IDLE,
    val isWifiAvailable: Boolean = false,
    val localIp: String? = null,
    val serverPort: Int = com.example.network.NetworkConstants.DEFAULT_PORT,
    val isNsdRegistered: Boolean = false,
    val connectedClientIp: String? = null,
    val connectedClientDeviceName: String? = null,
    val lastPingRttMs: Long? = null,
    val lastReceivedMessage: String? = null,
    val lastSentMessage: String? = null,
    val lastError: String? = null,
    val disconnectReason: String? = null,
    val reconnectAttempts: Int = 0,
    val lastSuccessTime: Long? = null,
    val diagnostics: List<DiagnosticItem> = emptyList()
) {
    val isFullyConnected: Boolean get() = state == HostConnectionState.CONNECTED
    val isServerRunning: Boolean get() = state != HostConnectionState.IDLE && 
            state != HostConnectionState.NETWORK_UNAVAILABLE && 
            state != HostConnectionState.STARTING_HOST

    val statusTitle: String get() = when (state) {
        HostConnectionState.CONNECTED -> "CONNECTED"
        HostConnectionState.VERIFYING -> "VERIFYING HANDSHAKE..."
        HostConnectionState.SOCKET_CONNECTED -> "SOCKET CONNECTED"
        HostConnectionState.CONNECTING -> "CONNECTING..."
        HostConnectionState.HOST_READY -> "HOST READY (WAITING FOR CONTROLLER)"
        HostConnectionState.DISCOVERED -> "CONTROLLER DISCOVERED"
        HostConnectionState.STARTING_HOST -> "STARTING HOST SERVER..."
        HostConnectionState.RECONNECTING -> "WAITING FOR RECONNECT..."
        HostConnectionState.DISCONNECTED -> "DISCONNECTED"
        HostConnectionState.NETWORK_UNAVAILABLE -> "NO WI-FI NETWORK"
        HostConnectionState.ERROR -> "CONNECTION ERROR"
        HostConnectionState.IDLE -> "IDLE"
    }

    val displayStatusMessage: String get() = when (state) {
        HostConnectionState.CONNECTED -> "Verified connection with Controller (${connectedClientDeviceName ?: connectedClientIp ?: "Client"})"
        HostConnectionState.VERIFYING -> "TCP Socket open. Verifying Handshake & Ping/Pong..."
        HostConnectionState.HOST_READY -> "Server listening on $localIp:$serverPort (NSD: LudoHost)"
        HostConnectionState.NETWORK_UNAVAILABLE -> "Please connect to the Controller's Wi-Fi hotspot"
        HostConnectionState.ERROR -> lastError ?: "Network error occurred"
        HostConnectionState.DISCONNECTED -> disconnectReason ?: "Controller disconnected. Server awaiting reconnect."
        else -> statusTitle
    }
}
