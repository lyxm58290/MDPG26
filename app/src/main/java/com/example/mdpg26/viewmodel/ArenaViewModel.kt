package com.example.mdpg26.viewmodel

import androidx.lifecycle.ViewModel
import com.example.mdpg26.arena.ArenaState
import com.example.mdpg26.arena.Facing
import com.example.mdpg26.arena.OBSTACLE_SIZE_GRIDS
import com.example.mdpg26.arena.Obstacle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Outcome of a completed drag — the fragment turns this into the right Bluetooth message. */
sealed class MoveOutcome {
    data class Moved(val obstacle: Obstacle) : MoveOutcome()
    data class Removed(val obstacle: Obstacle) : MoveOutcome()
    data object Rejected : MoveOutcome()
}

/**
 * Owns the canonical arena state (obstacles + robot). Pure and Bluetooth-agnostic by design:
 * [com.example.mdpg26.ui.arena.ArenaFragment] observes [state], forwards raw gestures from
 * [com.example.mdpg26.ui.arena.ArenaView] into these mutators, and sends the resulting
 * [com.example.mdpg26.arena.ArenaProtocol] string over the shared [BluetoothViewModel] — keeping
 * this class easy to reason about (and test) independently of the transport.
 */
class ArenaViewModel : ViewModel() {

    private val _state = MutableStateFlow(ArenaState())
    val state: StateFlow<ArenaState> = _state.asStateFlow()

    private var nextObstacleId = 1

    /**
     * Places a new obstacle whose top-left corner is (x, y), facing [imageFace]. Returns null if
     * its 4x4 footprint would go out of bounds or overlap an existing obstacle.
     */
    fun addObstacle(x: Int, y: Int, imageFace: Facing): Obstacle? {
        val current = _state.value
        if (!current.footprintInBounds(x, y, OBSTACLE_SIZE_GRIDS)) return null
        if (current.overlapsAnyObstacle(x, y, OBSTACLE_SIZE_GRIDS)) return null
        val obstacle = Obstacle(id = nextObstacleId++, x = x, y = y, imageFace = imageFace)
        _state.update { it.copy(obstacles = it.obstacles + obstacle) }
        return obstacle
    }

    /** Deletes an obstacle outright (remove tool). Returns the removed obstacle, if it existed. */
    fun removeObstacle(id: Int): Obstacle? {
        val existing = _state.value.obstacles.firstOrNull { it.id == id } ?: return null
        _state.update { st -> st.copy(obstacles = st.obstacles.filterNot { it.id == id }) }
        return existing
    }

    /**
     * Completes a drag. Dropping so the footprint no longer fully fits inside the arena deletes
     * the obstacle (checklist C.6); dropping on top of another obstacle is rejected (snaps back);
     * otherwise the obstacle's top-left corner moves to (newX, newY).
     */
    fun moveObstacle(id: Int, newX: Int, newY: Int): MoveOutcome {
        val current = _state.value
        val existing = current.obstacles.firstOrNull { it.id == id } ?: return MoveOutcome.Rejected

        if (!current.footprintInBounds(newX, newY, existing.size)) {
            _state.update { st -> st.copy(obstacles = st.obstacles.filterNot { it.id == id }) }
            return MoveOutcome.Removed(existing)
        }
        if (current.overlapsAnyObstacle(newX, newY, existing.size, excludeId = id)) {
            return MoveOutcome.Rejected
        }

        val updated = existing.copy(x = newX, y = newY)
        _state.update { st -> st.copy(obstacles = st.obstacles.map { if (it.id == id) updated else it }) }
        return MoveOutcome.Moved(updated)
    }

    /** Sets (or corrects) which face carries the target image (checklist C.7). */
    fun setObstacleFace(id: Int, face: Facing): Obstacle? {
        val existing = _state.value.obstacles.firstOrNull { it.id == id } ?: return null
        val updated = existing.copy(imageFace = face)
        _state.update { st -> st.copy(obstacles = st.obstacles.map { if (it.id == id) updated else it }) }
        return updated
    }

    /**
     * Records the RPi's recognized target-image result for obstacle [id] (checklist C.9), from an
     * incoming `TARGET,<id>,<targetId>` message. No-op if [id] no longer names a placed obstacle
     * (e.g. it was removed before the RPi's result arrived).
     */
    fun setObstacleTarget(id: Int, targetId: String): Obstacle? {
        val existing = _state.value.obstacles.firstOrNull { it.id == id } ?: return null
        val updated = existing.copy(targetId = targetId)
        _state.update { st -> st.copy(obstacles = st.obstacles.map { if (it.id == id) updated else it }) }
        return updated
    }

    /** Repositions the robot's center cell. Local UI setup only — no ROBOT,... message exists
     *  for the app to send; that format is reserved for incoming RPi position updates (C.10). */
    fun moveRobot(x: Int, y: Int) {
        val current = _state.value
        val half = current.robot.sizeInGrids / 2
        val inBounds = x - half >= 0 && x + half <= current.width - 1 &&
            y - half >= 0 && y + half <= current.height - 1
        if (!inBounds) return
        _state.update { st -> st.copy(robot = st.robot.copy(x = x, y = y)) }
    }

    fun rotateRobot(clockwise: Boolean) {
        _state.update { st ->
            val current = st.robot.facing
            val next = if (clockwise) current.next() else current.next().next().next()
            st.copy(robot = st.robot.copy(facing = next))
        }
    }
}
