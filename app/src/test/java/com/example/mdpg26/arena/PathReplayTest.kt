package com.example.mdpg26.arena

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PathReplayTest {

    private fun resourceLines(name: String): List<String> =
        javaClass.classLoader!!.getResource(name)!!.readText().lines()

    private fun headingDiff(a: Double, b: Double): Double = abs(((a - b + 540.0) % 360.0) - 180.0)

    private fun assertPose(where: String, x: Double, y: Double, heading: Double, actual: AlgoPose) {
        assertTrue("$where x: expected $x, got ${actual.xCm}", abs(actual.xCm - x) <= CM_TOLERANCE)
        assertTrue("$where y: expected $y, got ${actual.yCm}", abs(actual.yCm - y) <= CM_TOLERANCE)
        assertTrue("$where heading: expected $heading, got ${actual.headingDeg}", headingDiff(actual.headingDeg, heading) <= DEG_TOLERANCE)
    }

    /** mission_stm_golden.csv is visualize_plan.simulate() run on mission_stm.txt (both copied
     *  from visualise/visualise/), plus the heading at each point — see its header. */
    @Test
    fun missionStm_matchesPythonSimulate() {
        val start = PathReplayer.startPose(RobotState(0, 0, Facing.NORTH))
        val replay = PathReplayer.simulate(resourceLines("mission_stm.txt"), start)

        val golden = resourceLines("mission_stm_golden.csv").filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(",") }
        val goldenPoses = golden.filter { it[0] == "P" }
        val goldenRecognise = golden.filter { it[0] == "R" }

        assertEquals(goldenPoses.size, replay.poses.size)
        goldenPoses.zip(replay.poses).forEachIndexed { i, (g, pose) ->
            assertPose("pose $i", g[1].toDouble(), g[2].toDouble(), g[3].toDouble(), pose)
        }

        assertEquals(goldenRecognise.map { it[1] }, replay.recognitions.map { it.obstacleId })
        goldenRecognise.zip(replay.recognitions).forEach { (g, marker) ->
            assertPose("RECOGNISE ${g[1]}", g[2].toDouble(), g[3].toDouble(), g[4].toDouble(), marker.pose)
            assertEquals(marker.pose, replay.poses[marker.poseIndex])
        }

        // 29 TURN/STRAIGHT lines; the other 6 of the 35 PATH entries are RECOGNISE markers.
        assertEquals(29, replay.motionCommandCount)
        assertTrue(replay.skipped.isEmpty())
    }

    @Test
    fun startPose_matchesFromGrid() {
        assertPose("N", 9.5, 4.0, 90.0, PathReplayer.startPose(RobotState(0, 0, Facing.NORTH)))
        assertPose("E", 4.0, 9.5, 0.0, PathReplayer.startPose(RobotState(0, 0, Facing.EAST)))
        assertPose("S", 9.5, 19.0, 270.0, PathReplayer.startPose(RobotState(0, 0, Facing.SOUTH)))
        assertPose("W", 19.0, 9.5, 180.0, PathReplayer.startPose(RobotState(0, 0, Facing.WEST)))
        assertPose("N at (5,7)", 59.5, 74.0, 90.0, PathReplayer.startPose(RobotState(5, 7, Facing.NORTH)))
    }

    @Test
    fun malformedAndUnknownLines_areSkippedNotFatal() {
        val start = AlgoPose(50.0, 50.0, 90.0)
        val bad = listOf(
            "TURN,LEFT,R=20,A=abc",
            "TURN,UP,R=20,A=10",
            "TURN,LEFT,R=20,A=99999999999",
            "TURN,LEFT,R=20,A=100000",
            "STRAIGHT,GOCM,",
            "STRAIGHT,GOCM,99999999999",
            "FOO",
            "TARGET,1,11"
        )
        val replay = PathReplayer.simulate(listOf("", "HELLO", "# --- leg S -> 5 ---") + bad, start)
        assertEquals(bad, replay.skipped)
        assertEquals(listOf(start), replay.poses)
        assertEquals(0, replay.motionCommandCount)
    }

    @Test
    fun straightAndRawMoves() {
        val replay = PathReplayer.simulate(
            listOf("STRAIGHT,GOCM,20", "RAW,REVERSE,5", "raw,forward,10", "# RECOGNISE obstacle 7"),
            AlgoPose(50.0, 50.0, 90.0)
        )
        assertEquals(4, replay.poses.size)
        assertPose("end", 50.0, 75.0, 90.0, replay.poses.last())
        assertEquals("7", replay.recognitions.single().obstacleId)
        assertEquals(3, replay.recognitions.single().poseIndex)
    }

    @Test
    fun arcsAreSampledRoughlyEveryFiveDegrees() {
        // 90° forward left on R=20 about (50, 50) facing North: quarter circle to (30, 70) facing West.
        val replay = PathReplayer.simulate(listOf("TURN,LEFT,R=20,A=90"), AlgoPose(50.0, 50.0, 90.0))
        assertEquals(1 + 18, replay.poses.size)
        assertPose("end", 30.0, 70.0, 180.0, replay.poses.last())
        replay.poses.forEach { p -> assertEquals(20.0, Math.hypot(p.xCm - 30.0, p.yCm - 50.0), 1e-9) }
    }

    @Test
    fun appDegrees_convertsAlgoHeadings() {
        assertEquals(0.0, PathReplayer.appDegrees(90.0), 1e-9)
        assertEquals(90.0, PathReplayer.appDegrees(0.0), 1e-9)
        assertEquals(180.0, PathReplayer.appDegrees(270.0), 1e-9)
        assertEquals(270.0, PathReplayer.appDegrees(180.0), 1e-9)
        assertEquals(115.0, PathReplayer.appDegrees(335.0), 1e-9)
    }

    @Test
    fun bodyCentre_isOffsetAlongHeading() {
        val (x, y) = PathReplayer.bodyCentre(AlgoPose(9.5, 4.0, 90.0))
        assertEquals(9.5, x, 1e-9)
        assertEquals(11.5, y, 1e-9)
    }

    private companion object {
        const val CM_TOLERANCE = 0.5
        const val DEG_TOLERANCE = 0.5
    }
}
