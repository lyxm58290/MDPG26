package com.example.mdpg26.arena

/**
 * Facing / target-face direction. Matches the official checklist's N/S/E/W convention and
 * AMDTool's rotation convention: 0° = North (top of arena), increasing clockwise.
 */
enum class Facing(val letter: String, val degrees: Float) {
    NORTH("N", 0f),
    EAST("E", 90f),
    SOUTH("S", 180f),
    WEST("W", 270f);

    /** Cycles N -> E -> S -> W -> N, used for tap-to-set-target-face (checklist C.7). */
    fun next(): Facing = entries[(ordinal + 1) % entries.size]
}

/**
 * A numbered obstacle block. (x, y) is a grid cell with the origin at the TOP-LEFT of the
 * arena, x increasing right, y increasing down — matching AMDTool's own coordinate convention
 * (see scripts/defaultJson.cs) so the same mental model applies when testing against it.
 */
data class Obstacle(
    val id: Int,
    val x: Int,
    val y: Int,
    val targetFace: Facing? = null
)

/** The robot's footprint is sizeInGrids x sizeInGrids, centered on (x, y). */
data class RobotState(
    val x: Int,
    val y: Int,
    val facing: Facing = Facing.NORTH,
    val sizeInGrids: Int = 3
)

/** Default arena size matches AMDTool's own default (Settings > Default Arena Settings). */
data class ArenaState(
    val width: Int = 15,
    val height: Int = 20,
    val obstacles: List<Obstacle> = emptyList(),
    val robot: RobotState = RobotState(x = 2, y = 17)
) {
    fun obstacleAt(x: Int, y: Int): Obstacle? = obstacles.firstOrNull { it.x == x && it.y == y }
    fun isInBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height
}
