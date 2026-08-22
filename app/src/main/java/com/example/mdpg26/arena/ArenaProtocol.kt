package com.example.mdpg26.arena

/**
 * String formats sent out over the Bluetooth link for obstacle placement (C.6) and target-face
 * annotation (C.7). The checklist leaves these formats up to us — chosen here to read clearly
 * in a raw text log and to stay close to AMDTool's own ADDOBSTACLE/REMOVEOBSTACLE vocabulary
 * (scripts/defaultSimpleFormat.cs) so the format is familiar to anyone who has used that tool.
 *
 * OBSTACLE,<id>,<x>,<y>          - obstacle <id> is now at grid (x, y). Used both when an
 *                                   obstacle is first placed and after a completed drag-move
 *                                   (checklist C.6: "once positioning is completed ... the
 *                                   (x,y) coordinates and number ... is transmitted").
 * OBSTACLE_REMOVE,<id>           - obstacle <id> was deleted (via the remove tool or by being
 *                                   dragged outside the arena).
 * TARGET_FACE,<id>,<x>,<y>,<face> - obstacle <id> at (x, y) had its target-image face set/changed
 *                                   to <face> (N/S/E/W, matching the checklist's own convention
 *                                   for the ROBOT,<x>,<y>,<direction> message).
 *
 * All coordinates use the arena's own convention: origin at the top-left, x increasing right,
 * y increasing down (see ArenaModels.kt / AMDTool's scripts).
 */
object ArenaProtocol {
    fun obstaclePlaced(obstacle: Obstacle): String =
        "OBSTACLE,${obstacle.id},${obstacle.x},${obstacle.y}"

    fun obstacleRemoved(obstacle: Obstacle): String =
        "OBSTACLE_REMOVE,${obstacle.id}"

    fun targetFaceSet(obstacle: Obstacle): String =
        "TARGET_FACE,${obstacle.id},${obstacle.x},${obstacle.y},${obstacle.targetFace?.letter ?: "-"}"
}
