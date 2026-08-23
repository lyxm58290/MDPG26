package com.example.mdpg26.arena

/**
 * String formats sent out over the Bluetooth link for obstacle placement/move (C.6) and
 * target-face annotation (C.7), combined into one message per the fixed field order below.
 *
 * OBSTACLE,<id>,<x>,<y>,<imageFace>   - obstacle <id>'s top-left grid cell is (x, y) and the
 *                                        face carrying the target image is <imageFace>. Sent on
 *                                        first placement, after a completed drag-move, and
 *                                        whenever the face is changed on an existing obstacle.
 * OBSTACLE_REMOVE,<id>                - obstacle <id> was deleted (remove tool, or dragged off
 *                                        the arena).
 *
 * <imageFace> is one of N/E/S/W, viewed the same way as on screen: N = top of the obstacle
 * square, E = right, S = bottom, W = left.
 *
 * Coordinates use the arena's own convention: origin at the top-left, x increasing right, y
 * increasing down (see ArenaModels.kt / AMDTool's scripts).
 */
object ArenaProtocol {
    fun obstaclePlaced(obstacle: Obstacle): String =
        "OBSTACLE,${obstacle.id},${obstacle.x},${obstacle.y},${obstacle.imageFace.letter}"

    fun obstacleRemoved(obstacle: Obstacle): String =
        "OBSTACLE_REMOVE,${obstacle.id}"
}
