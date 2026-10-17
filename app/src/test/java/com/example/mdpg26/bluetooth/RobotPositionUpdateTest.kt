package com.example.mdpg26.bluetooth

import com.example.mdpg26.arena.ArenaProtocol
import com.example.mdpg26.arena.Facing
import com.example.mdpg26.arena.RobotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RobotPositionUpdateTest {

    @Test
    fun letters_stillParse() {
        assertEquals(RobotPositionUpdate(5, 5, Facing.EAST), RobotPositionUpdate.parse("ROBOT,5,5,E"))
        assertEquals(RobotPositionUpdate(0, 17, Facing.NORTH), RobotPositionUpdate.parse("ROBOT, 0, 17, N\r\n"))
        assertEquals(RobotPositionUpdate(1, 2, Facing.SOUTH), RobotPositionUpdate.parse("ROBOT,1,2,S"))
        assertEquals(RobotPositionUpdate(3, 4, Facing.WEST), RobotPositionUpdate.parse("ROBOT,3,4,W"))
    }

    @Test
    fun fullWords_parse() {
        assertEquals(RobotPositionUpdate(5, 5, Facing.EAST), RobotPositionUpdate.parse("ROBOT,5,5,EAST"))
        assertEquals(RobotPositionUpdate(0, 17, Facing.NORTH), RobotPositionUpdate.parse("ROBOT,0,17,NORTH\n"))
        assertEquals(RobotPositionUpdate(1, 2, Facing.SOUTH), RobotPositionUpdate.parse("ROBOT,1,2,SOUTH"))
        assertEquals(RobotPositionUpdate(3, 4, Facing.WEST), RobotPositionUpdate.parse("ROBOT,3,4,WEST"))
    }

    @Test
    fun whatTheAppSends_parsesBackToTheSameRobot() {
        Facing.entries.forEach { facing ->
            val sent = ArenaProtocol.robotLocation(RobotState(7, 9, facing))
            assertEquals("ROBOT,7,9,${facing.name}", sent)
            assertEquals(RobotPositionUpdate(7, 9, facing), RobotPositionUpdate.parse(sent))
        }
    }

    @Test
    fun malformed_returnsNull() {
        listOf("ROBOT,5,5", "ROBOT,5,5,X", "ROBOT,5,5,NORTHEAST", "ROBOT,a,5,N", "ROBOT,5,5,N,1", "ROBOTS,5,5,N")
            .forEach { assertNull("[$it]", RobotPositionUpdate.parse(it)) }
    }
}
