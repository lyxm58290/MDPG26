package com.example.mdpg26.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.mdpg26.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

/**
 * Bluetooth Classic SPP controller, following the Android <-> RPi Bluetooth Connection Guide:
 * secure RFCOMM to the standard SPP UUID first, insecure and reflection-channel-1 as fallbacks,
 * newline-delimited UTF-8 framing, and an automatic backoff reconnect loop so the app never hangs
 * when the link drops (checklist C.1, C.2 groundwork + C.8).
 */
class AndroidBluetoothController(
    private val context: Context
) : BluetoothController {

    private val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null
    private var reconnectJob: Job? = null
    private var userInitiatedDisconnect = false
    private var currentAddress: String? = null

    private val _connectionState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Disconnected)
    override val connectionState: StateFlow<ConnectionUiState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BtDevice>>(emptyList())
    override val pairedDevices: StateFlow<List<BtDevice>> = _pairedDevices.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BtDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BtDevice>> = _scannedDevices.asStateFlow()

    private val _messages = MutableStateFlow<List<TerminalMessage>>(emptyList())
    override val messages: StateFlow<List<TerminalMessage>> = _messages.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastDevice = MutableStateFlow<BtDevice?>(loadLastDevice())
    override val lastDevice: StateFlow<BtDevice?> = _lastDevice.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errors: SharedFlow<String> = _errors

    private var receiverRegistered = false
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val found = device.toBtDevice()
                    _scannedDevices.update { current ->
                        if (current.any { it.address == found.address }) current else current + found
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _isScanning.value = false
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _isScanning.value = true
            }
        }
    }

    init {
        ensureReceiverRegistered()
    }

    private fun ensureReceiverRegistered() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, discoveryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun isBluetoothSupported(): Boolean = adapter != null

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    override fun refreshPairedDevices() {
        if (!hasConnectPermission()) return
        val bonded = try {
            adapter?.bondedDevices
        } catch (e: SecurityException) {
            null
        } ?: return
        _pairedDevices.value = bonded.map { it.toBtDevice() }.sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        val a = adapter ?: run { _errors.tryEmit(context.getString(R.string.error_bluetooth_unsupported)); return }
        if (!hasScanPermission()) {
            _errors.tryEmit(context.getString(R.string.error_permission_denied))
            return
        }
        if (!a.isEnabled) return
        _scannedDevices.value = emptyList()
        try {
            if (a.isDiscovering) a.cancelDiscovery()
            a.startDiscovery()
            _isScanning.value = true
        } catch (e: SecurityException) {
            Log.w(TAG, "startDiscovery failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        try {
            if (adapter?.isDiscovering == true) adapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            Log.w(TAG, "stopDiscovery failed: ${e.message}")
        }
        _isScanning.value = false
    }

    override fun connect(address: String) {
        userInitiatedDisconnect = false
        currentAddress = address
        reconnectJob?.cancel()
        readJob?.cancel()
        scope.launch {
            val connected = tryConnectOnce(address)
            if (!connected && !userInitiatedDisconnect) {
                scheduleReconnect()
            }
        }
    }

    override fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        readJob?.cancel()
        closeSocketQuietly()
        _connectionState.value = ConnectionUiState.Disconnected
        addSystemMessage("Disconnected")
    }

    override fun sendMessage(text: String) {
        if (text.isBlank()) return
        val currentState = _connectionState.value
        if (currentState !is ConnectionUiState.Connected) {
            _errors.tryEmit(context.getString(R.string.error_send_failed))
            return
        }
        scope.launch {
            val s = socket
            if (s == null) {
                _errors.tryEmit(context.getString(R.string.error_send_failed))
                return@launch
            }
            try {
                s.outputStream.write((text + "\n").toByteArray(Charsets.UTF_8))
                s.outputStream.flush()
                addMessage(text, MessageDirection.SENT)
            } catch (e: IOException) {
                Log.w(TAG, "Send failed: ${e.message}")
                _errors.tryEmit(context.getString(R.string.error_send_failed))
                onLinkLost()
            }
        }
    }

    override fun clearMessages() {
        _messages.value = emptyList()
    }

    override fun release() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        readJob?.cancel()
        closeSocketQuietly()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: IllegalArgumentException) {
                // already unregistered
            }
            receiverRegistered = false
        }
        scope.cancel()
    }

    // ---------------------------------------------------------------------------------------
    // Internal connection machinery
    // ---------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun tryConnectOnce(address: String): Boolean {
        val displayName = _pairedDevices.value.firstOrNull { it.address == address }?.name
            ?: _scannedDevices.value.firstOrNull { it.address == address }?.name
            ?: _lastDevice.value?.takeIf { it.address == address }?.name
            ?: address
        _connectionState.value = ConnectionUiState.Connecting(displayName)

        if (!hasConnectPermission()) {
            _errors.tryEmit(context.getString(R.string.error_permission_denied))
            _connectionState.value = ConnectionUiState.Disconnected
            return false
        }
        val a = adapter
        if (a == null) {
            _errors.tryEmit(context.getString(R.string.error_bluetooth_unsupported))
            _connectionState.value = ConnectionUiState.Disconnected
            return false
        }
        if (!a.isEnabled) {
            _errors.tryEmit(context.getString(R.string.error_bluetooth_off))
            _connectionState.value = ConnectionUiState.Disconnected
            return false
        }

        try {
            if (a.isDiscovering) a.cancelDiscovery()
        } catch (e: SecurityException) {
            // ignore, discovery cancellation is best-effort
        }

        val device = try {
            a.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            _errors.tryEmit("Invalid device address")
            _connectionState.value = ConnectionUiState.Disconnected
            return false
        }

        val newSocket = openSocket(device)
        if (newSocket == null) {
            _errors.tryEmit(context.getString(R.string.error_connect_failed_fmt, displayName))
            _connectionState.value = ConnectionUiState.Disconnected
            return false
        }

        socket = newSocket
        val name = device.safeName() ?: displayName
        currentAddress = address
        saveLastDevice(BtDevice(name, address, bonded = true))
        _connectionState.value = ConnectionUiState.Connected(name, address)
        addSystemMessage("Connected to $name")
        startReadLoop(newSocket)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket? {
        val creators: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "secure" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            "insecure" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            "reflection-channel-1" to {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType!!)
                method.invoke(device, 1) as BluetoothSocket
            }
        )
        for ((label, create) in creators) {
            var candidate: BluetoothSocket? = null
            try {
                candidate = create()
                candidate.connect()
                return candidate
            } catch (e: Exception) {
                Log.w(TAG, "Connect attempt via $label failed: ${e.message}")
                try {
                    candidate?.close()
                } catch (closeError: IOException) {
                    // ignore
                }
            }
        }
        return null
    }

    private fun startReadLoop(activeSocket: BluetoothSocket) {
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            val sb = StringBuilder()
            try {
                while (isActive) {
                    val bytesRead = activeSocket.inputStream.read(buffer)
                    if (bytesRead < 0) break
                    sb.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                    var newlineIndex = sb.indexOf("\n")
                    while (newlineIndex >= 0) {
                        val line = sb.substring(0, newlineIndex).trim()
                        sb.delete(0, newlineIndex + 1)
                        if (line.isNotEmpty()) addMessage(line, MessageDirection.RECEIVED)
                        newlineIndex = sb.indexOf("\n")
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Read loop ended: ${e.message}")
            }
            if (isActive) onLinkLost()
        }
    }

    private fun onLinkLost() {
        closeSocketQuietly()
        if (userInitiatedDisconnect) {
            _connectionState.value = ConnectionUiState.Disconnected
            return
        }
        addSystemMessage("Connection lost — reconnecting…")
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val address = currentAddress
        if (address == null) {
            _connectionState.value = ConnectionUiState.Disconnected
            return
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            while (isActive && !userInitiatedDisconnect) {
                attempt++
                val name = _lastDevice.value?.name ?: address
                _connectionState.value = ConnectionUiState.Reconnecting(attempt, name)
                delay(backoffMillis(attempt))
                if (!isActive || userInitiatedDisconnect) break
                val connected = tryConnectOnce(address)
                if (connected) return@launch
            }
        }
    }

    private fun backoffMillis(attempt: Int): Long {
        val base = 1000.0 * 2.0.pow(min(attempt - 1, 4))
        return min(base, 16000.0).toLong()
    }

    private fun closeSocketQuietly() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // ignore
        }
        socket = null
    }

    private fun addMessage(text: String, direction: MessageDirection) {
        _messages.update { it + TerminalMessage(text, direction) }
    }

    private fun addSystemMessage(text: String) {
        addMessage(text, MessageDirection.SYSTEM)
    }

    private fun hasConnectPermission() =
        ContextCompat.checkSelfPermission(context, connectPermissionName) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission() =
        ContextCompat.checkSelfPermission(context, scanPermissionName) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private val connectPermissionName: String
        get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.Manifest.permission.BLUETOOTH_CONNECT
        } else {
            android.Manifest.permission.BLUETOOTH
        }

    private val scanPermissionName: String
        get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.Manifest.permission.BLUETOOTH_SCAN
        } else {
            android.Manifest.permission.BLUETOOTH_ADMIN
        }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toBtDevice(): BtDevice {
        val deviceName = if (hasConnectPermission()) (name ?: "Unknown device") else "Unknown device"
        val isBonded = bondState == BluetoothDevice.BOND_BONDED
        return BtDevice(deviceName, address, isBonded)
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String? = if (hasConnectPermission()) name else null

    private fun saveLastDevice(device: BtDevice) {
        _lastDevice.value = device
        prefs.edit {
            putString(KEY_LAST_NAME, device.name)
            putString(KEY_LAST_ADDRESS, device.address)
        }
    }

    private fun loadLastDevice(): BtDevice? {
        val address = prefs.getString(KEY_LAST_ADDRESS, null) ?: return null
        val name = prefs.getString(KEY_LAST_NAME, address) ?: address
        return BtDevice(name, address, bonded = true)
    }

    private companion object {
        const val TAG = "BluetoothController"
        const val PREFS_NAME = "bluetooth_prefs"
        const val KEY_LAST_ADDRESS = "last_device_address"
        const val KEY_LAST_NAME = "last_device_name"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(name: String): T? {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        getParcelableExtra(name)
    }
}
