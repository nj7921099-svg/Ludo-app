package com.example.bluetooth.model

enum class BluetoothStatusType {
    BLUETOOTH_OFF,
    PERMISSIONS_REQUIRED,
    DISCONNECTED,
    LISTENING,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class BluetoothConnectionState(
    val status: BluetoothStatusType = BluetoothStatusType.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val statusMessage: String = "Bluetooth initialized",
    val isServerListening: Boolean = false,
    val isScanning: Boolean = false,
    val lastError: String? = null,
    val protocolVersion: Int = 1
)

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean = false
)
