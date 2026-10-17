package com.example.mdpg26.bluetooth

import com.example.mdpg26.arena.Facing
import com.example.mdpg26.arena.ObstaclePlacement
import com.example.mdpg26.viewmodel.ArenaViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebugObstaclesMessageTest {

    @Test
    fun parse_pipeSeparatedEntries() {
        assertEquals(
            DebugObstaclesMessage.Valid(
                listOf(
                    ObstaclePlacement(9, 17, Facing.SOUTH),
                    ObstaclePlacement(13, 7, Facing.EAST),
                    ObstaclePlacement(2, 6, Facing.NORTH)
                )
            ),
            DebugObstaclesMessage.parse("DEBUG:OBS|9,17,S| 13, 7, east |2,6,NORTH\r\n")
        )
    }

    @Test
    fun parse_commaAfterPrefixAndEmpty() {
        assertEquals(
            DebugObstaclesMessage.Valid(listOf(ObstaclePlacement(9, 17, Facing.SOUTH))),
            DebugObstaclesMessage.parse("debug:obs,9,17,s")
        )
        assertEquals(DebugObstaclesMessage.Valid(emptyList()), DebugObstaclesMessage.parse("DEBUG:OBS"))
    }

    @Test
    fun parse_badEntry_isInvalidNamingIt() {
        val result = DebugObstaclesMessage.parse("DEBUG:OBS|9,17,S|10,x,N")
        assertTrue(result is DebugObstaclesMessage.Invalid)
        assertTrue((result as DebugObstaclesMessage.Invalid).reason.contains("entry 2"))
        listOf("DEBUG:OBS|9,17", "DEBUG:OBS|9,17,Q", "DEBUG:OBS|9,17,S,1").forEach {
            assertTrue(it, DebugObstaclesMessage.parse(it) is DebugObstaclesMessage.Invalid)
        }
    }

    @Test
    fun parse_otherLines_areNotDebugObstacles() {
        listOf("", "DEBUG", "DEBUG:OBSTACLE|1,1,N", "OBSTACLE,1,9,17,S", "PATH|STRAIGHT,GOCM,10").forEach {
            assertNull("[$it]", DebugObstaclesMessage.parse(it))
        }
    }

    @Test
    fun replaceObstacles_numbersInOrderAndReplaces() {
        val vm = ArenaViewModel()
        vm.addObstacle(0, 19, Facing.NORTH)
        assertNull(vm.replaceObstacles(listOf(ObstaclePlacement(9, 17, Facing.SOUTH), ObstaclePlacement(2, 6, Facing.NORTH))))
        assertEquals(listOf(1 to (9 to 17), 2 to (2 to 6)), vm.state.value.obstacles.map { it.id to (it.x to it.y) })
        // Placing by hand afterwards carries on from the next id.
        assertEquals(3, vm.addObstacle(5, 5, Facing.EAST)!!.id)
    }

    @Test
    fun replaceObstacles_isAllOrNothing() {
        val vm = ArenaViewModel()
        vm.addObstacle(0, 19, Facing.NORTH)
        val before = vm.state.value
        assertNotNull(vm.replaceObstacles(listOf(ObstaclePlacement(9, 17, Facing.SOUTH), ObstaclePlacement(20, 3, Facing.NORTH))))
        assertNotNull(vm.replaceObstacles(listOf(ObstaclePlacement(9, 17, Facing.SOUTH), ObstaclePlacement(9, 17, Facing.EAST))))
        assertEquals(before, vm.state.value)
    }

    /** test_inputs/obstacles_sample.txt lays out the same arena as test_inputs/README.md. */
    @Test
    fun obstaclesSample_matchesMissionScenario() {
        val message = DebugObstaclesMessage.parse(File("../test_inputs/obstacles_sample.txt").readText())
        assertEquals(
            DebugObstaclesMessage.Valid(
                listOf(
                    ObstaclePlacement(9, 17, Facing.SOUTH),
                    ObstaclePlacement(17, 18, Facing.SOUTH),
                    ObstaclePlacement(10, 11, Facing.SOUTH),
                    ObstaclePlacement(13, 7, Facing.EAST),
                    ObstaclePlacement(15, 2, Facing.WEST),
                    ObstaclePlacement(2, 6, Facing.NORTH)
                )
            ),
            message
        )
    }
}
