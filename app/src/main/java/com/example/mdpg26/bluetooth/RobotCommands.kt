package com.example.mdpg26.bluetooth

/**
 * Fixed command vocabulary sent to the robot over the Bluetooth link (checklist C.3).
 *
 * These exact, lower-case strings match AMDTool's default "Received Commands" mapping
 * (Settings > Received Commands in AMDtool.exe) so the same buttons drive AMDTool's virtual
 * robot during testing and the real robot on the day, without needing two code paths.
 */
object RobotCommands {
    const val FORWARD = "f"
    const val REVERSE = "r"
    const val STRAFE_LEFT = "sl"
    const val STRAFE_RIGHT = "sr"
    const val ROTATE_LEFT = "tl"
    const val ROTATE_RIGHT = "tr"

    const val BEGIN_EXPLORE = "beginExplore"
    const val BEGIN_FASTEST = "beginFastest"
    const val SEND_ARENA = "sendArena"
}
