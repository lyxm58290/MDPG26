package com.example.mdpg26.viewmodel

import com.example.mdpg26.bluetooth.Task2Direction
import com.example.mdpg26.bluetooth.Task2Target
import org.junit.Assert.assertEquals
import org.junit.Test

class Task2ViewModelTest {

    @Test
    fun startsWithBothObstaclesUnknown() {
        assertEquals(Task2State(null, null), Task2ViewModel().state.value)
    }

    @Test
    fun applyTarget_setsOnlyThatObstacle() {
        val vm = Task2ViewModel()
        vm.applyTarget(Task2Target(1, Task2Direction.LEFT))
        assertEquals(Task2State(Task2Direction.LEFT, null), vm.state.value)
        vm.applyTarget(Task2Target(2, Task2Direction.RIGHT))
        assertEquals(Task2State(Task2Direction.LEFT, Task2Direction.RIGHT), vm.state.value)
    }

    @Test
    fun applyTarget_latestResultWins() {
        val vm = Task2ViewModel()
        vm.applyTarget(Task2Target(1, Task2Direction.LEFT))
        vm.applyTarget(Task2Target(1, Task2Direction.RIGHT))
        assertEquals(Task2Direction.RIGHT, vm.state.value.obstacle1)
    }

    @Test
    fun clearTargets_resetsBothToUnknown() {
        val vm = Task2ViewModel()
        vm.applyTarget(Task2Target(1, Task2Direction.LEFT))
        vm.applyTarget(Task2Target(2, Task2Direction.RIGHT))
        vm.clearTargets()
        assertEquals(Task2State(), vm.state.value)
    }

    @Test
    fun formatRunTime_showsSecondsToTheMillisecond() {
        assertEquals("Task 2 time: 12.345 s (START -> DONE,TASK2)", formatRunTime(12_345))
        assertEquals("Task 2 time: 0.050 s (START -> DONE,TASK2)", formatRunTime(50))
    }

    @Test
    fun lapTimer_idleThenRunningThenFinishedThenIdleOnReset() {
        val vm = Task2ViewModel()
        assertEquals(LapTimerState.Idle, vm.lapTimer.value)
        vm.onRunStarted(1_000L)
        assertEquals(LapTimerState.Running(1_000L), vm.lapTimer.value)
        vm.onRunFinished(12_345L)
        assertEquals(LapTimerState.Finished(12_345L), vm.lapTimer.value)
        vm.resetLapTimer()
        assertEquals(LapTimerState.Idle, vm.lapTimer.value)
    }

    @Test
    fun lapTimer_repeatedStartRestartsFromLatestEcho() {
        val vm = Task2ViewModel()
        vm.onRunStarted(1_000L)
        vm.onRunStarted(5_000L)
        assertEquals(LapTimerState.Running(5_000L), vm.lapTimer.value)
    }

    @Test
    fun clearTargets_leavesLapTimerAlone() {
        val vm = Task2ViewModel()
        vm.onRunFinished(9_000L)
        vm.clearTargets()
        assertEquals(LapTimerState.Finished(9_000L), vm.lapTimer.value)
    }

    @Test
    fun formatLapTime_isMinutesSecondsMillis() {
        assertEquals("0:00.000", formatLapTime(0))
        assertEquals("0:12.345", formatLapTime(12_345))
        assertEquals("1:12.345", formatLapTime(72_345))
        assertEquals("10:00.007", formatLapTime(600_007))
    }
}
