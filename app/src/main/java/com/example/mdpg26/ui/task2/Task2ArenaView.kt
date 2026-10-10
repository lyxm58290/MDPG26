package com.example.mdpg26.ui.task2

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.example.mdpg26.R
import com.example.mdpg26.bluetooth.Task2Direction
import com.example.mdpg26.viewmodel.Task2State
import kotlin.math.max
import kotlin.math.min

/**
 * Schematic (not to scale — real distances are only known during the run) of the Task 2 arena,
 * left to right on one horizontal centreline: the carpark (U open to the right), obstacle 1 (small
 * square) and obstacle 2 (tall, thin). The robot travels right on screen, so its LEFT is screen-up
 * and its RIGHT is screen-down.
 *
 * Each obstacle's carpark-facing (left) face carries a card showing "?" until a TARGET arrives
 * for it; then the card shows a large arrow (up = LEFT, down = RIGHT) and a dashed path is drawn
 * passing that obstacle on the indicated side. Obstacle 1's path rejoins the centreline toward
 * obstacle 2; obstacle 2 is the turnaround, so its path wraps around the far face and heads back
 * left toward the carpark. Render-only: state comes in via [setState].
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
    private val carparkRect = RectF()
    private val obstacle1Rect = RectF()
    private val obstacle2Rect = RectF()
    private val card1Rect = RectF()
    private val card2Rect = RectF()

    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.themeColor(R.attr.arenaCellFill)
    }
    private val carparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = context.themeColor(com.google.android.material.R.attr.colorOnSurface)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f
        color = context.themeColor(com.google.android.material.R.attr.colorOnSurface)
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
        color = context.themeColor(com.google.android.material.R.attr.colorSurface)
    }
    private val pendingCardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.arena_target_face)
    }
    private val questionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = context.themeColor(com.google.android.material.R.attr.colorOnSurface)
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

    fun setState(newState: Task2State) {
        if (newState == state) return
        state = newState
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout(w.toFloat(), h.toFloat())
    }

    private fun computeLayout(w: Float, h: Float) {
        val u = min(w / WIDTH_UNITS, h / HEIGHT_UNITS)
        unit = u
        centerY = h / 2f
        floorRect.set(0f, 0f, w, h)

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

        carparkPaint.strokeWidth = max(0.12f * u, dp(3f))
        pendingCardStrokePaint.strokeWidth = max(0.08f * u, dp(2f))
        pathPaint.strokeWidth = max(0.08f * u, dp(2.5f))
        pathPaint.pathEffect = DashPathEffect(floatArrayOf(0.35f * u, 0.25f * u), 0f)
        robotOutlinePaint.strokeWidth = max(0.04f * u, dp(1.5f))
        labelPaint.textSize = 0.45f * u
        questionPaint.textSize = 1.1f * u
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (unit <= 0f) return

        val corner = dp(16f)
        canvas.drawRoundRect(floorRect, corner, corner, floorPaint)

        // Paths first so the obstacles and cards sit on top of them.
        val exit1X = obstacle1Rect.right + PATH_EXIT_UNITS * unit
        state.obstacle1?.let { drawBypass(canvas, carparkRect.right, card1Rect, obstacle1Rect, it) }
        state.obstacle2?.let { drawWrapAround(canvas, exit1X, card2Rect, obstacle2Rect, it) }

        drawCarpark(canvas)
        drawObstacle(canvas, obstacle1Rect, "1", labelSize = 1.0f * unit)
        drawObstacle(canvas, obstacle2Rect, "2", labelSize = 0.8f * unit)
        drawFaceCard(canvas, card1Rect, state.obstacle1)
        drawFaceCard(canvas, card2Rect, state.obstacle2)
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
     * Dashed robot path from [startX] on the centreline, around [card] + [obstacle] on the
     * [direction] side (LEFT = above, RIGHT = below), and back to the centreline beyond it.
     */
    private fun drawBypass(canvas: Canvas, startX: Float, card: RectF, obstacle: RectF, direction: Task2Direction) {
        val u = unit
        val right = obstacle.right
        val sideY = sideY(card, obstacle, direction)
        val exitX = right + PATH_EXIT_UNITS * u

        startLeadIn(startX, card, sideY)
        scratchPath.lineTo(right, sideY)
        scratchPath.cubicTo(right + 0.4f * u, sideY, exitX - 0.6f * u, centerY, exitX, centerY)
        canvas.drawPath(scratchPath, pathPaint)
        drawPathHead(canvas, exitX, centerY, pointsRight = true)
    }

    /**
     * Dashed robot path for the turnaround obstacle: from [startX] on the centreline, along the
     * [direction] side of [card] + [obstacle], around its far face, and back along the opposite
     * side, ending pointed left (back toward the carpark) directly above/below the card's arrow.
     */
    private fun drawWrapAround(canvas: Canvas, startX: Float, card: RectF, obstacle: RectF, direction: Task2Direction) {
        val u = unit
        val right = obstacle.right
        val sideY = sideY(card, obstacle, direction)
        val returnY = sideY(card, obstacle, if (direction == Task2Direction.LEFT) Task2Direction.RIGHT else Task2Direction.LEFT)
        // Arrowhead tip lines up with the centre of the card's own LEFT/RIGHT arrow.
        val endX = card.centerX() + PATH_HEAD_UNITS * u
        // Both control points pushed out by k bulge the curve 0.75k past the far face.
        val k = WRAP_BULGE_UNITS / 0.75f * u

        startLeadIn(startX, card, sideY)
        scratchPath.lineTo(right, sideY)
        scratchPath.cubicTo(right + k, sideY, right + k, returnY, right, returnY)
        scratchPath.lineTo(endX, returnY)
        canvas.drawPath(scratchPath, pathPaint)
        drawPathHead(canvas, endX, returnY, pointsRight = false)
    }

    /** y of the path running alongside [card] + [obstacle] (LEFT = above, RIGHT = below). */
    private fun sideY(card: RectF, obstacle: RectF, direction: Task2Direction): Float = when (direction) {
        Task2Direction.LEFT -> min(card.top, obstacle.top) - PATH_CLEARANCE_UNITS * unit
        Task2Direction.RIGHT -> max(card.bottom, obstacle.bottom) + PATH_CLEARANCE_UNITS * unit
    }

    /** Resets [scratchPath] to: straight along the centreline from [startX], then an S-curve out
     *  to [sideY], arriving level with [card]'s left edge. */
    private fun startLeadIn(startX: Float, card: RectF, sideY: Float) {
        val u = unit
        val left = card.left
        val approachX = left - PATH_EXIT_UNITS * u
        scratchPath.reset()
        scratchPath.moveTo(startX, centerY)
        scratchPath.lineTo(approachX, centerY)
        scratchPath.cubicTo(approachX + 0.6f * u, centerY, left - 0.4f * u, sideY, left, sideY)
    }

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

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.descent + fm.ascent) / 2f, paint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        // Minimum horizontal budget, in units: margin 0.5 + carpark 2.6 + gap 2.2 + card 1.2 +
        // obstacle1 2.0 + gap 2.6 + card 1.2 + obstacle2 1.1 + exit path/arrowhead 1.5 = 14.9.
        const val CONTENT_WIDTH_UNITS = 14.9f
        const val WIDTH_UNITS = 15.5f
        // Vertical: obstacle 2 is 4.6 tall and its bypass path clears it by 0.7 each side (6.0),
        // plus a little room for stroke widths. The CARPARK label (bottom at ~2.5) fits inside.
        const val HEIGHT_UNITS = 6.6f
        const val PATH_CLEARANCE_UNITS = 0.7f
        const val PATH_EXIT_UNITS = 1.0f
        const val PATH_HEAD_UNITS = 0.3f
        // How far obstacle 2's wrap-around loop swings past its far face (fits the 1.5-unit
        // right margin).
        const val WRAP_BULGE_UNITS = 1.0f
    }
}

private fun Context.themeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}
