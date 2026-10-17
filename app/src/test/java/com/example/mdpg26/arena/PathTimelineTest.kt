package com.example.mdpg26.arena

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathTimelineTest {

    private val start = AlgoPose(50.0, 50.0, 90.0)

    @Test
    fun straightMove_takesTwentyFiveMsPerCm() {
        val timeline = PathTimeline(PathReplayer.simulate(listOf("STRAIGHT,GOCM,20"), start))
        assertEquals(500L, timeline.totalMs)
        val mid = timeline.frameAt(250).pose
        assertEquals(50.0, mid.xCm, 1e-9)
        assertEquals(60.0, mid.yCm, 1e-9)
        assertEquals(start, timeline.frameAt(0).pose)
        assertEquals(70.0, timeline.frameAt(500).pose.yCm, 1e-9)
        assertEquals(70.0, timeline.frameAt(10_000).pose.yCm, 1e-9)
    }

    @Test
    fun recognise_pausesAndPulsesThatObstacle() {
        val timeline = PathTimeline(
            PathReplayer.simulate(listOf("STRAIGHT,GOCM,20", "# RECOGNISE obstacle 3", "STRAIGHT,GOCM,20"), start)
        )
        assertEquals(500L + 500L + 500L, timeline.totalMs)
        val stop = timeline.frameAt(750)
        assertEquals("3", stop.pulse?.obstacleId)
        assertEquals(0.5f, stop.pulse!!.phase, 1e-6f)
        assertEquals(70.0, stop.pose.yCm, 1e-9)
        assertNull(timeline.frameAt(400).pulse)
        assertNull(timeline.frameAt(1100).pulse)
    }

    @Test
    fun heading_turnsTheShortWayRound() {
        val replay = PathReplay(
            poses = listOf(AlgoPose(0.0, 0.0, 350.0), AlgoPose(10.0, 0.0, 10.0)),
            recognitions = emptyList(),
            motionCommandCount = 1,
            skipped = emptyList()
        )
        val heading = PathTimeline(replay).frameAt(125).pose.headingDeg
        assertEquals(0.0, ((heading + 180.0) % 360.0) - 180.0, 1e-9)
    }

    @Test
    fun missionStm_runsAtTwoAndAHalfSecondsPerMetrePlusStops() {
        val lines = javaClass.classLoader!!.getResource("mission_stm.txt")!!.readText().lines()
        val replay = PathReplayer.simulate(lines, PathReplayer.startPose(RobotState(0, 0, Facing.NORTH)))
        val timeline = PathTimeline(replay)
        val lengthCm = replay.poses.zipWithNext { a, b -> Math.hypot(b.xCm - a.xCm, b.yCm - a.yCm) }.sum()
        assertEquals(lengthCm * 25.0 + 6 * 500.0, timeline.totalMs.toDouble(), 1.0)

        var lastIndex = 0
        var stops = 0
        var wasPulsing = false
        for (t in 0..timeline.totalMs step 10) {
            val frame = timeline.frameAt(t)
            assertTrue(frame.passedIndex >= lastIndex)
            lastIndex = frame.passedIndex
            if (frame.pulse != null && !wasPulsing) stops++
            wasPulsing = frame.pulse != null
        }
        assertEquals(6, stops)
        assertEquals(replay.poses.last(), timeline.frameAt(timeline.totalMs).pose)
    }
}
