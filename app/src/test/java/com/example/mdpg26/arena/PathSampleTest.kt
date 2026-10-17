package com.example.mdpg26.arena

import com.example.mdpg26.bluetooth.PathMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** test_inputs/path_sample.txt (the line to paste into AMD Tool) replays to the same path as
 *  mission_stm.txt, with RECOGNISE ids shifted to the tablet's 1-based obstacle ids. */
class PathSampleTest {

    @Test
    fun pathSample_replaysLikeMissionStm() {
        // Unit tests run from the app module directory.
        val line = File("../test_inputs/path_sample.txt").readText()
        val path = PathMessage.parse(line)!!
        assertEquals(35, path.commands.size)

        val start = PathReplayer.startPose(RobotState(0, 0, Facing.NORTH))
        val sample = PathReplayer.simulate(path.commands, start)
        val mission = PathReplayer.simulate(
            javaClass.classLoader!!.getResource("mission_stm.txt")!!.readText().lines(), start
        )
        assertEquals(mission.poses, sample.poses)
        assertEquals(
            mission.recognitions.map { (it.obstacleId.toInt() + 1).toString() },
            sample.recognitions.map { it.obstacleId }
        )
        assertEquals(listOf("6", "1", "3", "5", "4", "2"), sample.recognitions.map { it.obstacleId })
    }
}
