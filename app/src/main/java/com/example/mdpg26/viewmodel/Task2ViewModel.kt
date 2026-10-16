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

/** The on-screen Task 2 lap timer, driven by the RPi's START echo and DONE,TASK2. */
sealed interface LapTimerState {
    data object Idle : LapTimerState

    /** Counting up live from [startedAt], the START echo's log timestamp (epoch ms). */
    data class Running(val startedAt: Long) : LapTimerState

    /** Frozen on the run time reported with DONE,TASK2 — the same value as the log line. */
    data class Finished(val durationMs: Long) : LapTimerState
}

/**
 * Owns the Task 2 (Fastest Car) screen's state. Activity-scoped (like [ArenaViewModel]) so it
 * survives rotation and a trip back to the main screen mid-run.
 */
class Task2ViewModel : ViewModel() {

    private val _state = MutableStateFlow(Task2State())
    val state: StateFlow<Task2State> = _state.asStateFlow()

    private val _lapTimer = MutableStateFlow<LapTimerState>(LapTimerState.Idle)
    val lapTimer: StateFlow<LapTimerState> = _lapTimer.asStateFlow()

    private var targetsJob: Job? = null
    private var startJob: Job? = null
    private var doneJob: Job? = null

    /**
     * Starts collecting [targets] (the shared [BluetoothViewModel.task2Targets]) for the lifetime
     * of this ViewModel rather than the fragment's view, so a TARGET arriving mid-rotation isn't
     * dropped. Safe to call again on every view creation; only the first call subscribes.
     */
    fun bindTargets(targets: Flow<Task2Target>) {
        if (targetsJob != null) return
        targetsJob = viewModelScope.launch { targets.collect(::applyTarget) }
    }

    /**
     * Starts collecting [starts] (the shared [BluetoothViewModel.task2Started]) for the lifetime
     * of this ViewModel, starting the lap timer on each. Same subscribe-once rule as [bindTargets].
     */
    fun bindStart(starts: Flow<Long>) {
        if (startJob != null) return
        startJob = viewModelScope.launch { starts.collect(::onRunStarted) }
    }

    /**
     * Starts collecting [runTimes] (the shared [BluetoothViewModel.task2Done], in ms) for the
     * lifetime of this ViewModel, stopping the lap timer on that run time and passing each run's
     * log line to [log]. Same subscribe-once rule as [bindTargets].
     */
    fun bindDone(runTimes: Flow<Long>, log: (String) -> Unit) {
        if (doneJob != null) return
        doneJob = viewModelScope.launch {
            runTimes.collect {
                onRunFinished(it)
                log(formatRunTime(it))
            }
        }
    }

    fun onRunStarted(startedAt: Long) {
        _lapTimer.value = LapTimerState.Running(startedAt)
    }

    fun onRunFinished(durationMs: Long) {
        _lapTimer.value = LapTimerState.Finished(durationMs)
    }

    /** Back to 0:00.000 — from the Reset button. Local only. */
    fun resetLapTimer() {
        _lapTimer.value = LapTimerState.Idle
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

/** e.g. 12345 -> "Task 2 time: 12.345 s (START -> DONE,TASK2)". */
internal fun formatRunTime(elapsedMs: Long): String =
    "Task 2 time: %d.%03d s (START -> DONE,TASK2)".format(elapsedMs / 1000, elapsedMs % 1000)

/** Lap-timer digits, M:SS.mmm, e.g. 72345 -> "1:12.345". */
internal fun formatLapTime(elapsedMs: Long): String {
    val ms = elapsedMs.coerceAtLeast(0)
    return "%d:%02d.%03d".format(ms / 60_000, ms / 1000 % 60, ms % 1000)
}
