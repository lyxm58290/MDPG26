package com.example.mdpg26.bluetooth

import com.example.mdpg26.arena.Facing

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
 * A `TARGET,<obstacleId>,<value>` line from the RPi. Task 1 and Task 2 share the TARGET prefix,
 * so one parser decides which it is from the third field: LEFT/RIGHT is a Task 2 arrow
 * ([Task2Target]); anything else is a Task 1 image id ([TargetDetection]).
 */
sealed interface TargetMessage {
    companion object {
        /** Returns null for anything malformed (wrong field count, non-numeric id, empty value)
         *  or for a LEFT/RIGHT arrow on an obstacle id Task 2 doesn't have. Never throws. */
        fun parse(line: String): TargetMessage? {
            val parts = line.trim().split(",").map { it.trim() }
            if (parts.size != 3 || parts[0] != "TARGET") return null
            val obstacleId = parts[1].toIntOrNull() ?: return null
            val value = parts[2]
            if (value.isEmpty()) return null
            Task2Direction.fromWireValue(value)?.let { direction ->
                return if (obstacleId in Task2Target.OBSTACLE_IDS) Task2Target(obstacleId, direction) else null
            }
            return TargetDetection(obstacleId, value)
        }
    }
}

/**
 * Task 1: RPi image-recognition result for a placed obstacle (checklist C.9), parsed from a
 * `TARGET,<obstacleId>,<targetId>` line by [TargetMessage.parse] — [obstacleId] is the obstacle's
 * own placement id (as used in [com.example.mdpg26.arena.ArenaProtocol]'s `OBSTACLE,...`
 * messages, not whatever is currently displayed on it), and [targetId] is the recognized
 * digit/letter to show instead.
 */
data class TargetDetection(val obstacleId: Int, val targetId: String) : TargetMessage

/** Which way a Task 2 obstacle's arrow points, from the robot's own point of view. */
enum class Task2Direction {
    LEFT, RIGHT;

    companion object {
        fun fromWireValue(value: String): Task2Direction? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}

/**
 * Task 2 (Fastest Car): the arrow recognized on obstacle [obstacleId]'s carpark-facing side,
 * parsed from a `TARGET,<obstacleId>,LEFT|RIGHT` line by [TargetMessage.parse]. [obstacleId] is
 * 1 for the obstacle nearest the carpark and 2 for the far one.
 */
data class Task2Target(val obstacleId: Int, val direction: Task2Direction) : TargetMessage {
    companion object {
        val OBSTACLE_IDS = 1..2
    }
}

/**
 * RPi-reported robot position/heading update (checklist C.10), parsed from a
 * `ROBOT,<x>,<y>,<direction>` line — <x>/<y> are grid coordinates and <direction> is one of
 * N/E/S/W (see [Facing.letter]).
 */
data class RobotPositionUpdate(val x: Int, val y: Int, val facing: Facing) {
    companion object {
        fun parse(line: String): RobotPositionUpdate? {
            val parts = line.split(",").map { it.trim() }
            if (parts.size != 4 || parts[0] != "ROBOT") return null
            val x = parts[1].toIntOrNull() ?: return null
            val y = parts[2].toIntOrNull() ?: return null
            val facing = Facing.fromLetter(parts[3]) ?: return null
            return RobotPositionUpdate(x, y, facing)
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
