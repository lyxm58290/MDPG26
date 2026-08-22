package com.example.mdpg26.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.mdpg26.bluetooth.AndroidBluetoothController
import com.example.mdpg26.bluetooth.BluetoothController

/**
 * Activity-scoped ViewModel wrapping [BluetoothController] so the connection survives
 * navigation between the Terminal / Control / Arena tabs.
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val controller: BluetoothController = AndroidBluetoothController(application)

    val connectionState = controller.connectionState
    val pairedDevices = controller.pairedDevices
    val scannedDevices = controller.scannedDevices
    val messages = controller.messages
    val isScanning = controller.isScanning
    val lastDevice = controller.lastDevice
    val errors = controller.errors

    fun isBluetoothSupported() = controller.isBluetoothSupported()
    fun isBluetoothEnabled() = controller.isBluetoothEnabled()
    fun refreshPairedDevices() = controller.refreshPairedDevices()
    fun startDiscovery() = controller.startDiscovery()
    fun stopDiscovery() = controller.stopDiscovery()
    fun connect(address: String) = controller.connect(address)
    fun disconnect() = controller.disconnect()
    fun sendMessage(text: String) = controller.sendMessage(text)
    fun clearMessages() = controller.clearMessages()

    override fun onCleared() {
        super.onCleared()
        controller.release()
    }
}
