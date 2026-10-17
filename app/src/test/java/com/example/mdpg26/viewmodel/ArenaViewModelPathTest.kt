package com.example.mdpg26.viewmodel

import com.example.mdpg26.arena.Facing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArenaViewModelPathTest {

    private val commands = listOf("STRAIGHT,GOCM,20", "# RECOGNISE obstacle 1", "TURN,LEFT,R=20,A=90")

    @Test
    fun loadPath_replaysFromRobotStart() {
        val vm = ArenaViewModel()
        val replay = vm.loadPath(commands)
        assertEquals(2, replay.motionCommandCount)
        assertEquals(1, replay.recognitions.size)
        assertNotNull(vm.plannedPath.value)
        val stored = vm.plannedPath.value!!
        assertEquals(commands, stored.commands)
        // from_grid(0, 0, NORTH) = (9.5, 4.0); 20 cm north of that.
        assertEquals(9.5, stored.replay.poses[1].xCm, 1e-9)
        assertEquals(24.0, stored.replay.poses[1].yCm, 1e-9)
    }

    @Test
    fun newPath_replacesOld() {
        val vm = ArenaViewModel()
        vm.loadPath(commands)
        vm.loadPath(listOf("STRAIGHT,GOCM,10"))
        assertEquals(listOf("STRAIGHT,GOCM,10"), vm.plannedPath.value!!.commands)
    }

    @Test
    fun resetArena_clearsPath() {
        val vm = ArenaViewModel()
        vm.loadPath(commands)
        vm.resetArena()
        assertNull(vm.plannedPath.value)
    }

    @Test
    fun movingRobot_replansFromNewStart() {
        val vm = ArenaViewModel()
        vm.loadPath(commands)
        vm.updateRobotPosition(5, 7, Facing.EAST)
        val start = vm.plannedPath.value!!.replay.poses.first()
        assertEquals(54.0, start.xCm, 1e-9)
        assertEquals(79.5, start.yCm, 1e-9)
        assertEquals(0.0, start.headingDeg, 1e-9)
    }
}
