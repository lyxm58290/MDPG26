package com.example.mdpg26.ui.task2

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.mdpg26.R
import com.example.mdpg26.bluetooth.Task2Direction
import com.example.mdpg26.viewmodel.Task2State
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Schematic (not to scale — real distances are only known during the run) of the Task 2 arena,
 * left to right on one horizontal centreline: the carpark (U open to the right), obstacle 1 (small
 * square) and obstacle 2 (tall, thin). The robot travels right on screen, so its LEFT is screen-up
 * and its RIGHT is screen-down.
 *
 * Each obstacle's carpark-facing (left) face carries a card showing "?" until a TARGET arrives
 * for it; then the card shows a large arrow (up = LEFT, down = RIGHT) and a squarish rounded-
 * rectangle lane is drawn looping around that obstacle on the indicated side — obstacle 1's loop
 * rejoins the centreline toward obstacle 2; obstacle 2's loop is the turnaround (a full U-shaped
 * end cap) and heads back left toward the carpark. Drawn end to end, the two loops read as a
 * horizontal figure-8/infinity shape, crossing the centreline where obstacle 1's loop ends and
 * obstacle 2's begins. The moment a TARGET arrives for an obstacle, a small robot marker
 * animates once along that obstacle's newly-revealed loop (see [setState]); the dashed lane and
 * arrowhead stay afterward as the permanent indicator. Render-only: state comes in via
 * [setState] — there's no live robot-position feed for Task 2, only the TARGET messages.
 */
class Task2ArenaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var state = Task2State()

    // Layout, recomputed in onSizeChanged. Element sizes are multiples of [unit], sized to fit
    // the view's height; the horizontal gaps then stretch to fill its width.
    private var unit = 0f
    private var centerY = 0f
    private val floorRect = RectF()
    private val trackRect = RectF()
    private val carparkRect = RectF()
    private val obstacle1Rect = RectF()
    private val obstacle2Rect = RectF()
    private val card1Rect = RectF()
    private val card2Rect = RectF()

    // One-shot "robot drove this loop" animation, played when a TARGET reveals a new obstacle's
    // direction. See setState()/animateAlong().
    private var robotAnimator: ValueAnimator? = null
    private var animRobotVisible = false
    private val animRobotPos = PointF()
    private var animRobotAngleDeg = 0f

    // Track surface is styled like a real racetrack (asphalt + grass + curbs) regardless of the
    // app's light/dark theme, so these colors are fixed rather than pulled from the theme.
    private val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.track_grass)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.track_asphalt)
    }
    private val curbRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        color = ContextCompat.getColor(context, R.color.track_curb_red)
    }
    private val curbWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val carparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val obstaclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.arena_obstacle_fill)
    }
    private val obstacleLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = ContextCompat.getColor(context, R.color.arena_obstacle_text)
    }
    private val pendingCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val pendingCardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.arena_target_face)
    }
    private val questionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = ContextCompat.getColor(context, R.color.black)
    }
    private val detectedCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.arena_target_detected_text)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.arena_robot_fill)
    }
    private val pathHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.arena_robot_fill)
    }
    private val robotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.arena_robot_fill)
    }
    private val robotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.arena_robot_outline)
    }

    private val scratchPath = Path()
    private val curbPath = Path()

    fun setState(newState: Task2State) {
        if (newState == state) return
        val old = state
        state = newState
        invalidate()

        // Play the "robot drove this loop" animation only for a direction that just became
        // known (null -> non-null), not on reset (Reset clears both back to null).
        if (unit <= 0f) return
        if (old.obstacle1 == null && newState.obstacle1 != null) {
            animateAlong(buildBypassPath(Path(), carparkRect.right, card1Rect, obstacle1Rect, newState.obstacle1))
        } else if (old.obstacle2 == null && newState.obstacle2 != null) {
            val exit1X = obstacle1Rect.right + PATH_EXIT_UNITS * unit
            animateAlong(buildWrapAroundPath(Path(), exit1X, card2Rect, obstacle2Rect, newState.obstacle2))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout(w.toFloat(), h.toFloat())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        robotAnimator?.cancel()
    }

    private fun computeLayout(w: Float, h: Float) {
        val u = min(w / WIDTH_UNITS, h / HEIGHT_UNITS)
        unit = u
        centerY = h / 2f
        floorRect.set(0f, 0f, w, h)
        val grassMargin = GRASS_MARGIN_UNITS * u
        trackRect.set(
            floorRect.left + grassMargin, floorRect.top + grassMargin,
            floorRect.right - grassMargin, floorRect.bottom - grassMargin
        )

        // Element sizes are fixed in units (so the height stays tight); any spare width goes into
        // the gaps rather than empty margins, since the schematic isn't to scale anyway.
        val spare = (w - CONTENT_WIDTH_UNITS * u).coerceAtLeast(0f)
        var x = 0.5f * u + spare * 0.05f
        carparkRect.set(x, centerY - 1.6f * u, x + 2.6f * u, centerY + 1.6f * u)
        x = carparkRect.right + 2.2f * u + spare * 0.45f
        card1Rect.set(x, centerY - 1.0f * u, x + 1.2f * u, centerY + 1.0f * u)
        obstacle1Rect.set(card1Rect.right, centerY - 1.0f * u, card1Rect.right + 2.0f * u, centerY + 1.0f * u)
        x = obstacle1Rect.right + 2.6f * u + spare * 0.45f
        card2Rect.set(x, centerY - 1.2f * u, x + 1.2f * u, centerY + 1.2f * u)
        obstacle2Rect.set(card2Rect.right, centerY - 2.3f * u, card2Rect.right + 1.1f * u, centerY + 2.3f * u)

        val curbStripe = max(0.22f * u, dp(5f))
        val curbDashLen = 0.5f * u
        curbRedPaint.strokeWidth = curbStripe
        curbRedPaint.pathEffect = DashPathEffect(floatArrayOf(curbDashLen, curbDashLen), 0f)
        curbWhitePaint.strokeWidth = curbStripe
        curbWhitePaint.pathEffect = DashPathEffect(floatArrayOf(curbDashLen, curbDashLen), curbDashLen)

        carparkPaint.strokeWidth = max(0.12f * u, dp(3f))
        pendingCardStrokePaint.strokeWidth = max(0.08f * u, dp(2f))
        pathPaint.strokeWidth = max(0.1f * u, dp(3f))
        pathPaint.pathEffect = DashPathEffect(floatArrayOf(0.35f * u, 0.25f * u), 0f)
        robotOutlinePaint.strokeWidth = max(0.04f * u, dp(1.5f))
        labelPaint.textSize = 0.45f * u
        questionPaint.textSize = 1.1f * u
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return

        val corner = dp(16f)
        canvas.drawRoundRect(floorRect, corner, corner, grassPaint)
        val trackCorner = dp(12f)
        canvas.drawRoundRect(trackRect, trackCorner, trackCorner, trackPaint)
        curbPath.reset()
        curbPath.addRoundRect(trackRect, trackCorner, trackCorner, Path.Direction.CW)
        canvas.drawPath(curbPath, curbRedPaint)
        canvas.drawPath(curbPath, curbWhitePaint)

        // Loops first so the obstacles and cards sit on top of them.
        val exit1X = obstacle1Rect.right + PATH_EXIT_UNITS * unit
        state.obstacle1?.let { direction ->
            buildBypassPath(scratchPath, carparkRect.right, card1Rect, obstacle1Rect, direction)
            canvas.drawPath(scratchPath, pathPaint)
            drawPathHead(canvas, exit1X, centerY, pointsRight = true)
        }
        state.obstacle2?.let { direction ->
            buildWrapAroundPath(scratchPath, exit1X, card2Rect, obstacle2Rect, direction)
            canvas.drawPath(scratchPath, pathPaint)
            val returnY = sideY(card2Rect, obstacle2Rect, direction.opposite(), LOOP2_CLEARANCE_UNITS)
            val endX = card2Rect.centerX() + PATH_HEAD_UNITS * unit
            drawPathHead(canvas, endX, returnY, pointsRight = false)
        }

        drawCarpark(canvas)
        drawObstacle(canvas, obstacle1Rect, "1", labelSize = 1.0f * unit)
        drawObstacle(canvas, obstacle2Rect, "2", labelSize = 0.8f * unit)
        drawFaceCard(canvas, card1Rect, state.obstacle1)
        drawFaceCard(canvas, card2Rect, state.obstacle2)

        if (animRobotVisible) drawAnimatedRobot(canvas)
    }

    private fun drawCarpark(canvas: Canvas) {
        val r = carparkRect
        scratchPath.reset()
        scratchPath.moveTo(r.right, r.top)
        scratchPath.lineTo(r.left, r.top)
        scratchPath.lineTo(r.left, r.bottom)
        scratchPath.lineTo(r.right, r.bottom)
        canvas.drawPath(scratchPath, carparkPaint)

        // Robot parked inside, nose toward the opening, so screen-up/down reads as its left/right.
        val size = 1.1f * unit
        val cx = r.centerX()
        scratchPath.reset()
        scratchPath.moveTo(cx + size / 2f, centerY)
        scratchPath.lineTo(cx - size / 2f, centerY - size / 2f)
        scratchPath.lineTo(cx - size / 2f, centerY + size / 2f)
        scratchPath.close()
        canvas.drawPath(scratchPath, robotPaint)
        canvas.drawPath(scratchPath, robotOutlinePaint)

        canvas.drawText(
            context.getString(R.string.task2_carpark_label),
            r.centerX(),
            r.bottom + 0.75f * unit,
            labelPaint
        )
    }

    private fun drawObstacle(canvas: Canvas, rect: RectF, label: String, labelSize: Float) {
        val corner = 0.15f * unit
        canvas.drawRoundRect(rect, corner, corner, obstaclePaint)
        obstacleLabelPaint.textSize = labelSize
        drawCenteredText(canvas, label, rect.centerX(), rect.centerY(), obstacleLabelPaint)
    }

    /** The obstacle's carpark-facing side: "?" until detected, then a large arrow (up = LEFT). */
    private fun drawFaceCard(canvas: Canvas, card: RectF, direction: Task2Direction?) {
        val corner = 0.2f * unit
        if (direction == null) {
            canvas.drawRoundRect(card, corner, corner, pendingCardPaint)
            val inset = pendingCardStrokePaint.strokeWidth / 2f
            canvas.drawRoundRect(
                card.left + inset, card.top + inset, card.right - inset, card.bottom - inset,
                corner, corner, pendingCardStrokePaint
            )
            drawCenteredText(canvas, "?", card.centerX(), card.centerY(), questionPaint)
            return
        }

        canvas.drawRoundRect(card, corner, corner, detectedCardPaint)
        val length = card.height() * 0.8f
        val headLength = length * 0.45f
        val headHalfWidth = card.width() * 0.4f
        val shaftHalfWidth = card.width() * 0.15f
        val cx = card.centerX()
        val tipY = card.centerY() - length / 2f
        val baseY = card.centerY() + length / 2f
        scratchPath.reset()
        scratchPath.moveTo(cx, tipY)
        scratchPath.lineTo(cx + headHalfWidth, tipY + headLength)
        scratchPath.lineTo(cx + shaftHalfWidth, tipY + headLength)
        scratchPath.lineTo(cx + shaftHalfWidth, baseY)
        scratchPath.lineTo(cx - shaftHalfWidth, baseY)
        scratchPath.lineTo(cx - shaftHalfWidth, tipY + headLength)
        scratchPath.lineTo(cx - headHalfWidth, tipY + headLength)
        scratchPath.close()

        canvas.save()
        if (direction == Task2Direction.RIGHT) canvas.rotate(180f, cx, card.centerY())
        canvas.drawPath(scratchPath, arrowPaint)
        canvas.restore()
    }

    /**
     * Squarish rounded-rectangle lane from [startX] on the centreline, out to the [direction]
     * side of [card] + [obstacle], along past them, and back to the centreline beyond — i.e. one
     * "wing" of the figure-8, open at both ends. Writes into (and returns) [path] so the same
     * geometry can both be drawn (via the view's reused scratch path) and measured for the
     * drive-through animation (via a fresh [Path]).
     */
    private fun buildBypassPath(path: Path, startX: Float, card: RectF, obstacle: RectF, direction: Task2Direction): Path {
        val u = unit
        val cr = CORNER_RADIUS_UNITS * u
        val dir = direction.sign()
        val laneY = sideY(card, obstacle, direction, LOOP1_CLEARANCE_UNITS)
        val turnX = card.left - PATH_EXIT_UNITS * u
        val exitX = obstacle.right + PATH_EXIT_UNITS * u

        path.reset()
        path.moveTo(startX, centerY)
        path.lineTo(turnX - cr, centerY)
        path.quadTo(turnX, centerY, turnX, centerY + dir * cr)
        path.lineTo(turnX, laneY - dir * cr)
        path.quadTo(turnX, laneY, turnX + cr, laneY)
        path.lineTo(exitX - cr, laneY)
        path.quadTo(exitX, laneY, exitX, laneY - dir * cr)
        path.lineTo(exitX, centerY)
        return path
    }

    /**
     * Squarish rounded-rectangle loop for the turnaround obstacle: out from [startX] along the
     * [direction] side of [card] + [obstacle], a U-shaped end cap around its far face, and back
     * along the opposite side to an arrowhead lined up with the card's own arrow — the other
     * "wing" of the figure-8, closed at the far end.
     */
    private fun buildWrapAroundPath(path: Path, startX: Float, card: RectF, obstacle: RectF, direction: Task2Direction): Path {
        val u = unit
        val cr = CORNER_RADIUS_UNITS * u
        val dir = direction.sign()
        val laneY = sideY(card, obstacle, direction, LOOP2_CLEARANCE_UNITS)
        val returnY = sideY(card, obstacle, direction.opposite(), LOOP2_CLEARANCE_UNITS)
        val turnX = card.left - PATH_EXIT_UNITS * u
        val endCapX = obstacle.right + LOOP2_END_UNITS * u
        val endX = card.centerX() + PATH_HEAD_UNITS * u

        path.reset()
        path.moveTo(startX, centerY)
        path.lineTo(turnX - cr, centerY)
        path.quadTo(turnX, centerY, turnX, centerY + dir * cr)
        path.lineTo(turnX, laneY - dir * cr)
        path.quadTo(turnX, laneY, turnX + cr, laneY)
        path.lineTo(endCapX - cr, laneY)
        path.quadTo(endCapX, laneY, endCapX, laneY - dir * cr)
        path.lineTo(endCapX, returnY + dir * cr)
        path.quadTo(endCapX, returnY, endCapX - cr, returnY)
        path.lineTo(endX, returnY)
        return path
    }

    /** y of the lane running alongside [card] + [obstacle] (LEFT = above, RIGHT = below). */
    private fun sideY(card: RectF, obstacle: RectF, direction: Task2Direction, clearanceUnits: Float): Float =
        when (direction) {
            Task2Direction.LEFT -> min(card.top, obstacle.top) - clearanceUnits * unit
            Task2Direction.RIGHT -> max(card.bottom, obstacle.bottom) + clearanceUnits * unit
        }

    private fun Task2Direction.sign(): Float = if (this == Task2Direction.LEFT) -1f else 1f

    private fun Task2Direction.opposite(): Task2Direction =
        if (this == Task2Direction.LEFT) Task2Direction.RIGHT else Task2Direction.LEFT

    /** Small arrowhead with its tip [PATH_HEAD_UNITS] past (x, y), showing the direction of travel. */
    private fun drawPathHead(canvas: Canvas, x: Float, y: Float, pointsRight: Boolean) {
        val head = PATH_HEAD_UNITS * unit
        val sign = if (pointsRight) 1f else -1f
        scratchPath.reset()
        scratchPath.moveTo(x + sign * head, y)
        scratchPath.lineTo(x - sign * head * 0.4f, y - head * 0.8f)
        scratchPath.lineTo(x - sign * head * 0.4f, y + head * 0.8f)
        scratchPath.close()
        canvas.drawPath(scratchPath, pathHeadPaint)
    }

    /** Starts a one-shot animation moving [animRobotPos] along [path], from start to end. */
    private fun animateAlong(path: Path) {
        val measure = PathMeasure(path, false)
        val length = measure.length
        if (length <= 0f) return

        robotAnimator?.cancel()
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        animRobotVisible = true
        robotAnimator = ValueAnimator.ofFloat(0f, length).apply {
            duration = ANIM_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                measure.getPosTan(anim.animatedValue as Float, pos, tan)
                animRobotPos.set(pos[0], pos[1])
                animRobotAngleDeg = Math.toDegrees(atan2(tan[1].toDouble(), tan[0].toDouble())).toFloat()
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animRobotVisible = false
                    invalidate()
                }
            })
            start()
        }
    }

    private fun drawAnimatedRobot(canvas: Canvas) {
        val size = 0.9f * unit
        canvas.save()
        canvas.translate(animRobotPos.x, animRobotPos.y)
        canvas.rotate(animRobotAngleDeg)
        scratchPath.reset()
        scratchPath.moveTo(size / 2f, 0f)
        scratchPath.lineTo(-size / 2f, -size / 2f)
        scratchPath.lineTo(-size / 2f, size / 2f)
        scratchPath.close()
        canvas.drawPath(scratchPath, robotPaint)
        canvas.drawPath(scratchPath, robotOutlinePaint)
        canvas.restore()
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.descent + fm.ascent) / 2f, paint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        // Minimum horizontal budget, in units: margin 0.5 + carpark 2.6 + gap 2.2 + card 1.2 +
        // obstacle1 2.0 + gap 2.6 + card 1.2 + obstacle2 1.1 + loop-2 end cap/arrowhead 1.5 = 14.9.
        const val CONTENT_WIDTH_UNITS = 14.9f
        const val WIDTH_UNITS = 15.5f
        // Vertical: obstacle 2's loop (the taller of the two) sits at centreY +/- (2.3 half-height
        // + 0.8 clearance) = 3.1, plus a little room for stroke widths. The CARPARK label fits
        // well inside obstacle 1's smaller loop extent.
        const val HEIGHT_UNITS = 6.8f
        const val PATH_EXIT_UNITS = 1.0f
        const val PATH_HEAD_UNITS = 0.3f
        // Corner radius for the squarish rounded-rectangle loops.
        const val CORNER_RADIUS_UNITS = 0.35f
        // How far obstacle 1's loop sits above/below the centreline (small, since it's just a
        // bypass — the loop is a detour, not a hairpin).
        const val LOOP1_CLEARANCE_UNITS = 1.4f
        // How far obstacle 2's loop sits above/below the centreline, and how far past its far
        // face the loop's end cap sits — both fit the 1.5-unit right margin.
        const val LOOP2_CLEARANCE_UNITS = 0.8f
        const val LOOP2_END_UNITS = 1.1f
        const val ANIM_DURATION_MS = 900L
        // Width of the grass border framing the asphalt track surface.
        const val GRASS_MARGIN_UNITS = 0.35f
    }
}
