package com.example.mdpg26.bluetooth

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the Bluetooth Classic (SPP/RFCOMM) link to the RPi or the AMDTool test rig.
 * Kept as an interface so the arena/control screens can later depend on this without caring
 * about the Android Bluetooth API details.
 */
interface BluetoothController {
    val connectionState: StateFlow<ConnectionUiState>
    val pairedDevices: StateFlow<List<BtDevice>>
    val scannedDevices: StateFlow<List<BtDevice>>
    val messages: StateFlow<List<TerminalMessage>>
    val isScanning: StateFlow<Boolean>
    val lastDevice: StateFlow<BtDevice?>
    val errors: SharedFlow<String>

    fun isBluetoothSupported(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun refreshPairedDevices()
    fun startDiscovery()
    fun stopDiscovery()
    fun connect(address: String)
    fun disconnect()
    fun sendMessage(text: String)
    fun clearMessages()
    fun release()
}
