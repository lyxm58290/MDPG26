package com.example.mdpg26.arena

import kotlin.math.ceil
import kotlin.math.hypot

/** Where the replay animation is at a given moment. [passedIndex] is the last pose of
 *  [PathReplay.poses] fully travelled; [pulse] is set while stopped at a RECOGNISE marker, with
 *  [Pulse.phase] running 0..1 over the stop. */
data class ReplayFrame(val pose: AlgoPose, val passedIndex: Int, val pulse: Pulse?) {
    data class Pulse(val obstacleId: String, val phase: Float)
}

/**
 * Maps elapsed animation time onto [replay]'s poses: the car moves at [msPerCm] (the default
 * 25 ms/cm is 2.5 s per metre of path) and stops for [recognisePauseMs] at each RECOGNISE
 * marker. Between two poses the position moves in a straight line and the heading turns the
 * short way round; the arcs are already sampled every ~5°, so that's indistinguishable from
 * driving the arc itself.
 */
class PathTimeline(
    private val replay: PathReplay,
    private val msPerCm: Double = DEFAULT_MS_PER_CM,
    private val recognisePauseMs: Double = DEFAULT_RECOGNISE_PAUSE_MS
) {
    /** Either a move from pose [fromIndex] to the next, or (with [marker]) a photo stop there. */
    private class Step(val startMs: Double, val durationMs: Double, val fromIndex: Int, val marker: RecognisePoint?)

    private val steps: List<Step>

    /** The whole animation, stops included. */
    val totalMs: Long

    init {
        val poses = replay.poses
        val list = mutableListOf<Step>()
        var t = 0.0
        for (i in poses.indices) {
            replay.recognitions.filter { it.poseIndex == i }.forEach { marker ->
                list += Step(t, recognisePauseMs, i, marker)
                t += recognisePauseMs
            }
            if (i < poses.lastIndex) {
                val a = poses[i]
                val b = poses[i + 1]
                val d = hypot(b.xCm - a.xCm, b.yCm - a.yCm) * msPerCm
                list += Step(t, d, i, null)
                t += d
            }
        }
        steps = list
        totalMs = ceil(t).toLong()
    }

    fun frameAt(elapsedMs: Long): ReplayFrame {
        val poses = replay.poses
        val t = elapsedMs.toDouble()
        val step = steps.firstOrNull { t >= it.startMs && t < it.startMs + it.durationMs }
            ?: return if (t <= 0.0) ReplayFrame(poses.first(), 0, null) else ReplayFrame(poses.last(), poses.lastIndex, null)
        val f = (t - step.startMs) / step.durationMs
        step.marker?.let { marker ->
            return ReplayFrame(marker.pose, marker.poseIndex, ReplayFrame.Pulse(marker.obstacleId, f.toFloat()))
        }
        val a = poses[step.fromIndex]
        val b = poses[step.fromIndex + 1]
        val dh = ((b.headingDeg - a.headingDeg + 540.0) % 360.0) - 180.0
        val heading = ((a.headingDeg + dh * f) % 360.0 + 360.0) % 360.0
        return ReplayFrame(
            AlgoPose(a.xCm + (b.xCm - a.xCm) * f, a.yCm + (b.yCm - a.yCm) * f, heading),
            step.fromIndex,
            null
        )
    }

    companion object {
        const val DEFAULT_MS_PER_CM = 25.0
        const val DEFAULT_RECOGNISE_PAUSE_MS = 500.0
    }
}
