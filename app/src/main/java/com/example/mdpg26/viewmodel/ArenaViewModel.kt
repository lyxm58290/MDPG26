package com.example.mdpg26.viewmodel

import androidx.lifecycle.ViewModel
import com.example.mdpg26.arena.ArenaState
import com.example.mdpg26.arena.Facing
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
 * [ArenaProtocol] string over the shared [BluetoothViewModel] — keeping this class easy to
 * reason about (and test) independently of the transport.
 */
class ArenaViewModel : ViewModel() {

    private val _state = MutableStateFlow(ArenaState())
    val state: StateFlow<ArenaState> = _state.asStateFlow()

    private var nextObstacleId = 1

    /** Places a new obstacle at (x, y). Returns null if out of bounds or already occupied. */
    fun addObstacle(x: Int, y: Int): Obstacle? {
        val current = _state.value
        if (!current.isInBounds(x, y) || current.obstacleAt(x, y) != null) return null
        val obstacle = Obstacle(id = nextObstacleId++, x = x, y = y)
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
     * Completes a drag. Dropping outside the arena bounds deletes the obstacle (checklist C.6);
     * dropping on another obstacle is rejected (snaps back); otherwise the obstacle moves.
     */
    fun moveObstacle(id: Int, newX: Int, newY: Int): MoveOutcome {
        val current = _state.value
        val existing = current.obstacles.firstOrNull { it.id == id } ?: return MoveOutcome.Rejected

        if (!current.isInBounds(newX, newY)) {
            _state.update { st -> st.copy(obstacles = st.obstacles.filterNot { it.id == id }) }
            return MoveOutcome.Removed(existing)
        }

        val occupant = current.obstacleAt(newX, newY)
        if (occupant != null && occupant.id != id) return MoveOutcome.Rejected

        val updated = existing.copy(x = newX, y = newY)
        _state.update { st -> st.copy(obstacles = st.obstacles.map { if (it.id == id) updated else it }) }
        return MoveOutcome.Moved(updated)
    }

    /** Tap-to-annotate: cycles N -> E -> S -> W -> N each tap (checklist C.7). */
    fun cycleTargetFace(id: Int): Obstacle? {
        val current = _state.value
        val existing = current.obstacles.firstOrNull { it.id == id } ?: return null
        val updated = existing.copy(targetFace = existing.targetFace?.next() ?: Facing.NORTH)
        _state.update { st -> st.copy(obstacles = st.obstacles.map { if (it.id == id) updated else it }) }
        return updated
    }
}
