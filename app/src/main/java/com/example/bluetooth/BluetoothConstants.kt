package com.example.bluetooth

import java.util.UUID

/**
 * Constants for Bluetooth communication between App 1 (Host / Game Engine)
 * and App 2 (Controller / Companion).
 */
object BluetoothConstants {
    /**
     * Dedicated application service UUID for Ludo Host & Controller communication.
     */
    val APP_SERVICE_UUID: UUID = UUID.fromString("4a6a5780-60b6-4b82-bc10-72f12fa8398e")

    /**
     * Standard Serial Port Profile (SPP) UUID, widely supported across Android devices.
     */
    val STANDARD_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Name advertised on the RFCOMM server socket.
     */
    const val SERVICE_NAME = "LudoHostBluetoothService"

    /**
     * Sender identifier for App 1.
     */
    const val SENDER_APP_HOST = "APP1_HOST"

    /**
     * Expected sender identifier for App 2.
     */
    const val SENDER_APP_CONTROLLER = "APP2_CONTROLLER"

    /**
     * Current protocol version.
     */
    const val PROTOCOL_VERSION = 1

    /**
     * Delimiter used to separate streaming JSON frames over RFCOMM socket.
     */
    const val MESSAGE_DELIMITER = "\n"

    /**
     * Allowed box count limits.
     */
    const val MIN_BOXES = 2
    const val MAX_BOXES = 6
    const val DEFAULT_BOX_COUNT = 4

    /**
     * Allowed random number limits.
     */
    const val MIN_RANDOM_VALUE = 1
    const val MAX_RANDOM_VALUE = 6
}
