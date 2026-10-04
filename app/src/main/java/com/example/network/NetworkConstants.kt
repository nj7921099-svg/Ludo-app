package com.example.network

object NetworkConstants {
    // Single Authoritative TCP Port
    const val LUDO_TCP_PORT = 8888
    const val DEFAULT_PORT = LUDO_TCP_PORT

    // Network Service Discovery (NSD / mDNS)
    const val NSD_SERVICE_NAME = "LudoHost"
    const val NSD_SERVICE_TYPE = "_ludo._tcp."

    // Protocol Version & Roles
    const val PROTOCOL_VERSION = 1
    const val ROLE_HOST = "LUDO_HOST"
    const val ROLE_CONTROLLER = "CONTROLLER"

    // Timeouts and Delays
    const val SOCKET_TIMEOUT_MS = 15000
    const val HANDSHAKE_TIMEOUT_MS = 8000L
    const val PING_INTERVAL_MS = 10000L
    const val NUMBER_TEST_TIMEOUT_MS = 5000L

    // Game Configuration Defaults
    const val DEFAULT_BOX_COUNT = 4
    const val MIN_BOXES = 2
    const val MAX_BOXES = 6
    const val MIN_RANDOM_VALUE = 1
    const val MAX_RANDOM_VALUE = 6

    // Line delimiter for framing
    const val LINE_DELIMITER = "\n"
}
