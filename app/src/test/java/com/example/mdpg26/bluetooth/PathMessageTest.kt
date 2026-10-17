package com.example.mdpg26.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathMessageTest {

    @Test
    fun parse_splitsCommandsOnPipe() {
        assertEquals(
            PathMessage(listOf("TURN,RIGHT,R=35,A=115", "STRAIGHT,GOCM,-20", "# RECOGNISE obstacle 5")),
            PathMessage.parse("PATH|TURN,RIGHT,R=35,A=115|STRAIGHT,GOCM,-20|# RECOGNISE obstacle 5\r\n")
        )
    }

    @Test
    fun parse_dropsBlankEntriesAndTrims() {
        assertEquals(
            PathMessage(listOf("STRAIGHT,GOCM,10", "TURN,LEFT,R=20,A=57")),
            PathMessage.parse("  PATH| STRAIGHT,GOCM,10 ||TURN,LEFT,R=20,A=57|")
        )
    }

    @Test
    fun parse_rejectsNonPathAndEmptyPath() {
        listOf("", "PATH", "PATH|", "PATH| | ", "path|STRAIGHT,GOCM,10", "ROBOT,1,1,N", "TARGET,1,11").forEach {
            assertNull("[$it]", PathMessage.parse(it))
        }
    }

    @Test
    fun isPathLine_spotsPartialPathBuffers() {
        assertTrue(PathMessage.isPathLine(StringBuilder("PATH|TURN,RIGHT,R=35,A=1")))
        assertTrue(PathMessage.isPathLine("\nPATH|"))
        assertFalse(PathMessage.isPathLine("PAT"))
        assertFalse(PathMessage.isPathLine("ROBOT,1,1,N"))
    }
}
