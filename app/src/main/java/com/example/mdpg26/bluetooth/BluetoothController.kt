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
    val isListening: StateFlow<Boolean>
    val robotStatus: StateFlow<RobotStatus?>
    val errors: SharedFlow<String>

    fun isBluetoothSupported(): Boolean
    fun isBluetoothEnabled(): Boolean
    fun refreshPairedDevices()
    fun startDiscovery()
    fun stopDiscovery()
    fun connect(address: String)

    /**
     * Makes the app itself discoverable as an SPP server and waits for an incoming connection.
     * Covers AMDTool's "As client" workflow (AMDTool's Bluetooth menu scans for and connects TO
     * the phone) — the mirror image of [connect], which is the app connecting out to the RPi.
     */
    fun startListening()
    fun stopListening()
    fun disconnect()
    fun sendMessage(text: String)
    fun clearMessages()
    fun release()
}
