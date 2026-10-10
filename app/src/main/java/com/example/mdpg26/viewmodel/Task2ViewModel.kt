package com.example.mdpg26.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mdpg26.bluetooth.Task2Direction
import com.example.mdpg26.bluetooth.Task2Target
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Arrow recognized on each Task 2 obstacle so far; null = not seen yet (shown as "?"). */
data class Task2State(
    val obstacle1: Task2Direction? = null,
    val obstacle2: Task2Direction? = null
)

/**
 * Owns the Task 2 (Fastest Car) screen's state. Activity-scoped (like [ArenaViewModel]) so it
 * survives rotation and a trip back to the main screen mid-run.
 */
class Task2ViewModel : ViewModel() {

    private val _state = MutableStateFlow(Task2State())
    val state: StateFlow<Task2State> = _state.asStateFlow()

    private var targetsJob: Job? = null

    /**
     * Starts collecting [targets] (the shared [BluetoothViewModel.task2Targets]) for the lifetime
     * of this ViewModel rather than the fragment's view, so a TARGET arriving mid-rotation isn't
     * dropped. Safe to call again on every view creation; only the first call subscribes.
     */
    fun bindTargets(targets: Flow<Task2Target>) {
        if (targetsJob != null) return
        targetsJob = viewModelScope.launch { targets.collect(::applyTarget) }
    }

    fun applyTarget(target: Task2Target) {
        _state.update { st ->
            when (target.obstacleId) {
                1 -> st.copy(obstacle1 = target.direction)
                2 -> st.copy(obstacle2 = target.direction)
                else -> st
            }
        }
    }

    /** Resets both obstacles to "?" — from the Reset button, and on START so a retry run starts
     *  clean. Local only; nothing is sent to the robot. */
    fun clearTargets() {
        _state.value = Task2State()
    }
}
