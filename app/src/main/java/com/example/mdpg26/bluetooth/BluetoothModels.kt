package com.example.mdpg26.bluetooth

/** UI-facing connection state for the Bluetooth Classic SPP link to the RPi / AMDTool. */
sealed class ConnectionUiState {
    data object Disconnected : ConnectionUiState()
    data class Connecting(val deviceName: String) : ConnectionUiState()
    data class Connected(val deviceName: String, val deviceAddress: String) : ConnectionUiState()
    data class Reconnecting(val attempt: Int, val deviceName: String) : ConnectionUiState()
}

/** Lightweight, UI-safe wrapper around [android.bluetooth.BluetoothDevice]. */
data class BtDevice(
    val name: String,
    val address: String,
    val bonded: Boolean
)

enum class MessageDirection { SENT, RECEIVED, SYSTEM }

data class TerminalMessage(
    val text: String,
    val direction: MessageDirection,
    val timestamp: Long = System.currentTimeMillis(),
    val id: Long = idCounter.getAndIncrement()
) {
    private companion object {
        val idCounter = java.util.concurrent.atomic.AtomicLong(0)
    }
}
