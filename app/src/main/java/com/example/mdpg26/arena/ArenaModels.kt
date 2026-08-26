package com.example.mdpg26.arena

/**
 * Facing / image-face direction. 0° = North (top of the arena / top of an obstacle square, as
 * viewed on screen), increasing clockwise — East = right, South = bottom, West = left. Matches
 * both the official checklist's N/S/E/W convention and AMDTool's rotation convention.
 */
enum class Facing(val letter: String, val degrees: Float) {
    NORTH("N", 0f),
    EAST("E", 90f),
    SOUTH("S", 180f),
    WEST("W", 270f);

    fun next(): Facing = entries[(ordinal + 1) % entries.size]
}

/**
 * A numbered obstacle block: 40cm x 40cm, i.e. a 4x4 footprint of 10cm grid cells. (x, y) is the
 * TOP-LEFT grid cell of that footprint, origin at the arena's top-left, x increasing right, y
 * increasing down (matching AMDTool's own coordinate convention — see scripts/defaultJson.cs).
 * [imageFace] is required at placement time (asked via a dialog) rather than defaulting to unset.
 */
data class Obstacle(
    val id: Int,
    val x: Int,
    val y: Int,
    val imageFace: Facing,
    val size: Int = OBSTACLE_SIZE_GRIDS
) {
    val cells: IntRange get() = x until x + size
    val rows: IntRange get() = y until y + size
}

/** The robot's footprint is sizeInGrids x sizeInGrids (30cm x 30cm = 3x3), centered on (x, y). */
data class RobotState(
    val x: Int,
    val y: Int,
    val facing: Facing = Facing.NORTH,
    val sizeInGrids: Int = ROBOT_SIZE_GRIDS
)

/** Arena is 20x20 grid cells, each cell 10cm (a 200cm x 200cm arena). */
data class ArenaState(
    val width: Int = ARENA_SIZE_GRIDS,
    val height: Int = ARENA_SIZE_GRIDS,
    val obstacles: List<Obstacle> = emptyList(),
    val robot: RobotState = RobotState(x = 2, y = ARENA_SIZE_GRIDS - 3)
) {
    /** Finds whichever obstacle's footprint contains the given cell, if any. */
    fun obstacleAt(x: Int, y: Int): Obstacle? = obstacles.firstOrNull { x in it.cells && y in it.rows }

    fun footprintInBounds(x: Int, y: Int, size: Int): Boolean =
        x >= 0 && y >= 0 && x + size <= width && y + size <= height

    /** True if a [size]x[size] footprint at (x, y) would overlap any obstacle other than [excludeId]. */
    fun overlapsAnyObstacle(x: Int, y: Int, size: Int, excludeId: Int? = null): Boolean =
        obstacles.any { obs ->
            obs.id != excludeId &&
                x < obs.x + obs.size && x + size > obs.x &&
                y < obs.y + obs.size && y + size > obs.y
        }
}

const val ARENA_SIZE_GRIDS = 20
const val OBSTACLE_SIZE_GRIDS = 4
const val ROBOT_SIZE_GRIDS = 3
