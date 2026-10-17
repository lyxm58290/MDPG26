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
    fun playPath_withoutPath_doesNothing() {
        val vm = ArenaViewModel()
        vm.playPath()
        assertNull(vm.replayProgress.value)
    }

    @Test
    fun replay_runsToEndAndHoldsUntilStopped() {
        val vm = ArenaViewModel()
        vm.loadPath(listOf("STRAIGHT,GOCM,20"))
        vm.playPath()
        assertEquals(PathReplayProgress(0, playing = true), vm.replayProgress.value)
        vm.advancePathReplay(200)
        assertEquals(PathReplayProgress(200, playing = true), vm.replayProgress.value)
        vm.advancePathReplay(10_000)
        assertEquals(PathReplayProgress(500, playing = false), vm.replayProgress.value)
        // Finished: further ticks change nothing, the robot stays at the end.
        vm.advancePathReplay(100)
        assertEquals(PathReplayProgress(500, playing = false), vm.replayProgress.value)
        vm.stopPath()
        assertNull(vm.replayProgress.value)
    }

    @Test
    fun replay_endsOnNewPathResetOrRobotMove() {
        val vm = ArenaViewModel()
        vm.loadPath(commands)
        vm.playPath()
        vm.loadPath(commands)
        assertNull(vm.replayProgress.value)

        vm.playPath()
        vm.moveRobot(2, 2)
        assertNull(vm.replayProgress.value)

        vm.playPath()
        vm.resetArena()
        assertNull(vm.replayProgress.value)
    }

    @Test
    fun replay_leavesRobotStateAlone() {
        val vm = ArenaViewModel()
        vm.loadPath(commands)
        val robot = vm.state.value.robot
        vm.playPath()
        vm.advancePathReplay(1_000)
        assertEquals(robot, vm.state.value.robot)
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
