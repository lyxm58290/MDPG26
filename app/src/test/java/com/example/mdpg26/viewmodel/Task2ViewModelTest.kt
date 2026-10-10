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
}
