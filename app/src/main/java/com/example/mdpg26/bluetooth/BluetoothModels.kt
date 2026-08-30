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

/**
 * Robot activity status pushed by the RPi as a `{"status":"..."}` JSON line over the serial link
 * (distinct from the plain-text [RobotCommands] the app sends out).
 */
enum class RobotStatus(val wireValue: String, val label: String) {
    EXPLORING("exploring", "Exploring"),
    FASTEST_PATH("fastest path", "Fastest Path"),
    TURNING_LEFT("turning left", "Turning Left"),
    TURNING_RIGHT("turning right", "Turning Right"),
    MOVING_FORWARD("moving forward", "Moving Forward"),
    REVERSING("reversing", "Reversing");

    companion object {
        fun fromWireValue(value: String): RobotStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * RPi image-recognition result for a placed obstacle (checklist C.9), parsed from a
 * `TARGET,<obstacleId>,<targetId>` line — [obstacleId] is the obstacle's own placement id (as
 * used in [com.example.mdpg26.arena.ArenaProtocol]'s `OBSTACLE,...` messages, not whatever is
 * currently displayed on it), and [targetId] is the recognized digit/letter to show instead.
 */
data class TargetDetection(val obstacleId: Int, val targetId: String) {
    companion object {
        fun parse(line: String): TargetDetection? {
            val parts = line.split(",").map { it.trim() }
            if (parts.size != 3 || parts[0] != "TARGET") return null
            val obstacleId = parts[1].toIntOrNull() ?: return null
            val targetId = parts[2]
            if (targetId.isEmpty()) return null
            return TargetDetection(obstacleId, targetId)
        }
    }
}

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
