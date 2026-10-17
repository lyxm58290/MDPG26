package com.example.mdpg26.arena

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A robot pose in the algo team's frame (visualise/model.py): cm, origin at the arena's
 * bottom-left, y up, heading in degrees with 0 = East increasing counter-clockwise, normalised to
 * [0, 360). (x, y) is the car's rear-axle midpoint — the point the STM drives — not its centre.
 */
data class AlgoPose(val xCm: Double, val yCm: Double, val headingDeg: Double)

/** A `# RECOGNISE obstacle <id>` marker: the car photographs [obstacleId] at [pose], which is
 *  [PathReplay.poses]`[poseIndex]`. */
data class RecognisePoint(val obstacleId: String, val pose: AlgoPose, val poseIndex: Int)

/**
 * The result of replaying a planner command list (mission_stm.txt lines). [poses] starts at the
 * start pose and has one entry per straight move plus one every [PathReplayer.ARC_SAMPLE_DEG]
 * degrees or so along each arc, so drawing them as a polyline curves rather than cutting corners.
 * [skipped] holds every line that wasn't understood (and so didn't move the car).
 */
data class PathReplay(
    val poses: List<AlgoPose>,
    val recognitions: List<RecognisePoint>,
    val motionCommandCount: Int,
    val skipped: List<String>
)

/**
 * Kotlin port of `parse_stm` + `simulate` in visualise/visualize_plan.py (lines 36-110): replays
 * the planner's TURN/STRAIGHT/RAW commands with the same circular-arc model the firmware drives.
 * Pure Kotlin so it can be unit-tested against the Python output (see PathReplayTest).
 */
object PathReplayer {
    // Fixed per side, like the Python: the firmware always drives these radii, whatever R= says.
    const val LEFT_RADIUS_CM = 20.0
    const val RIGHT_RADIUS_CM = 35.0
    const val ARC_SAMPLE_DEG = 5

    // Car geometry (visualise/model.py:7-11): 23 x 19 cm, rear axle 4 cm in from the back.
    const val CAR_LENGTH_CM = 23.0
    const val CAR_WIDTH_CM = 19.0
    const val AXLE_TO_REAR_CM = 4.0
    const val AXLE_TO_CENTRE_CM = CAR_LENGTH_CM / 2 - AXLE_TO_REAR_CM

    // Anything bigger is a corrupt line, not a real turn — and would mean millions of samples.
    private const val MAX_TURN_DEG = 720

    private val recogniseRegex = Regex("""^#\s*RECOGNISE\s+obstacle\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val turnRegex = Regex("""^TURN,(LEFT|RIGHT),R=(\d+),A=(-?\d+)""")
    private val straightRegex = Regex("""^STRAIGHT,GOCM,(-?\d+)""")
    private val rawRegex = Regex("""^RAW,(FORWARD|REVERSE),(\d+)""")

    /**
     * The algo's start pose for the app's robot, as `Robot.from_grid` computes it
     * (visualise/model.py:84-92): the car's footprint is pushed flush against the bottom-left
     * corner of grid cell (x, y). Grid (0, 0) facing North is (9.5, 4.0) cm at 90°.
     */
    fun startPose(robot: RobotState): AlgoPose {
        val th = Math.toRadians(algoDegrees(robot.facing))
        val fx = cos(th)
        val fy = sin(th)
        val half = CAR_WIDTH_CM / 2
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        for (along in doubleArrayOf(-AXLE_TO_REAR_CM, CAR_LENGTH_CM - AXLE_TO_REAR_CM)) {
            for (across in doubleArrayOf(-half, half)) {
                // right-hand vector is (fy, -fx)
                minX = minOf(minX, along * fx + across * fy)
                minY = minOf(minY, along * fy - across * fx)
            }
        }
        return AlgoPose(robot.x * GRID_CM - minX, robot.y * GRID_CM - minY, normalise(algoDegrees(robot.facing)))
    }

    /** Replays [commands] (one STM line each, comments included) from [start]. Never throws. */
    fun simulate(commands: List<String>, start: AlgoPose): PathReplay {
        var x = start.xCm
        var y = start.yCm
        var th = Math.toRadians(start.headingDeg)
        val poses = mutableListOf(pose(x, y, th))
        val recognitions = mutableListOf<RecognisePoint>()
        val skipped = mutableListOf<String>()
        var motions = 0

        for (raw in commands) {
            val s = raw.trim()
            if (s.isEmpty()) continue
            if (s.startsWith("#")) {
                // Leg headers ("# --- leg A -> B ---") are just comments.
                recogniseRegex.find(s)?.let { m ->
                    recognitions += RecognisePoint(m.groupValues[1], poses.last(), poses.lastIndex)
                }
                continue
            }
            val u = s.uppercase()
            if (u == "HELLO") continue

            val turn = turnRegex.find(u)
            val straightCm = straightRegex.find(u)?.groupValues?.get(1)?.toIntOrNull()
                ?: rawRegex.find(u)?.let { m ->
                    m.groupValues[2].toIntOrNull()?.let { if (m.groupValues[1] == "FORWARD") it else -it }
                }
            when {
                turn != null -> {
                    val a = turn.groupValues[3].toIntOrNull()
                    if (a == null || abs(a) > MAX_TURN_DEG) {
                        skipped += s
                        continue
                    }
                    val left = turn.groupValues[1] == "LEFT"
                    // Same sampling as the Python, so the poses line up one-for-one with it.
                    val n = max(2, abs(a) / ARC_SAMPLE_DEG)
                    for (i in 1..n) {
                        val (xi, yi, thi) = advanceArc(x, y, th, left, a.toDouble() * i / n)
                        poses += pose(xi, yi, thi)
                    }
                    val end = advanceArc(x, y, th, left, a.toDouble())
                    x = end.first
                    y = end.second
                    th = end.third
                    motions++
                }
                straightCm != null -> {
                    x += straightCm * cos(th)
                    y += straightCm * sin(th)
                    poses += pose(x, y, th)
                    motions++
                }
                else -> skipped += s
            }
        }
        return PathReplay(poses, recognitions, motions, skipped)
    }

    /** App heading (0° = North, clockwise, as [Facing.degrees]) for an algo heading. */
    fun appDegrees(algoDeg: Double): Double = normalise(90.0 - algoDeg)

    /** The car's body centre for a rear-axle [pose], in the algo frame (cm). */
    fun bodyCentre(pose: AlgoPose): Pair<Double, Double> {
        val th = Math.toRadians(pose.headingDeg)
        return pose.xCm + AXLE_TO_CENTRE_CM * cos(th) to pose.yCm + AXLE_TO_CENTRE_CM * sin(th)
    }

    /** `_advance_arc`: an arc about the rear-axle midpoint; negative degrees = reversing. */
    private fun advanceArc(x: Double, y: Double, th: Double, left: Boolean, signedDeg: Double): Triple<Double, Double, Double> {
        val mag = Math.toRadians(abs(signedDeg))
        if (mag == 0.0) return Triple(x, y, th)
        val fwd = signedDeg >= 0
        val r = if (left) LEFT_RADIUS_CM else RIGHT_RADIUS_CM
        val dtheta = if (left == fwd) mag else -mag
        val direction = if (fwd) 1.0 else -1.0
        val newTh = th + dtheta
        val radius = direction * (r * mag) / dtheta
        return Triple(
            x + radius * (sin(newTh) - sin(th)),
            y - radius * (cos(newTh) - cos(th)),
            newTh
        )
    }

    private fun algoDegrees(facing: Facing): Double = 90.0 - facing.degrees

    private fun pose(x: Double, y: Double, thRad: Double) = AlgoPose(x, y, normalise(Math.toDegrees(thRad)))

    private fun normalise(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    private const val GRID_CM = 10.0
}

/** A PATH the tablet has received: the raw commands and their replay from the robot's start. */
data class PlannedPath(val commands: List<String>, val replay: PathReplay) {
    /** The replay animation's timing (see [PathTimeline]); not part of equality. */
    val timeline: PathTimeline by lazy { PathTimeline(replay) }
}
