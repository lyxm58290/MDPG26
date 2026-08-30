package com.example.mdpg26.ui.arena

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.example.mdpg26.R
import com.example.mdpg26.arena.ArenaState
import com.example.mdpg26.arena.Facing
import com.example.mdpg26.arena.Obstacle
import com.example.mdpg26.arena.RobotState
import kotlin.math.abs

/**
 * Renders the 2D exploration arena (checklist C.5) and turns raw touch gestures into semantic
 * callbacks for placing/moving/removing obstacles, annotating their target face (C.6, C.7), and
 * placing the robot.
 *
 * This view owns no durable state — it just renders whatever [ArenaState] it's given via
 * [setState] and reports gestures upward; [ArenaFragment] decides whether to accept them via
 * [com.example.mdpg26.viewmodel.ArenaViewModel] and pushes the resulting state back down.
 */
class ArenaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Tool { NONE, PLACE_OBSTACLE, REMOVE_OBSTACLE, PLACE_ROBOT }

    var tool: Tool = Tool.NONE

    /** Fired on ACTION_UP over an empty, in-bounds cell while [Tool.PLACE_OBSTACLE] is active. */
    var onObstaclePlaceRequested: ((x: Int, y: Int) -> Unit)? = null

    /** Fired on ACTION_UP over an obstacle while [Tool.REMOVE_OBSTACLE] is active. */
    var onObstacleRemoveRequested: ((id: Int) -> Unit)? = null

    /** Fired when a drag that started on an obstacle ends (tool == NONE). */
    var onObstacleMoveRequested: ((id: Int, newX: Int, newY: Int) -> Unit)? = null

    /** Fired on a plain tap (no drag) on an existing obstacle (tool == NONE) — edit its face. */
    var onObstacleTapRequested: ((id: Int) -> Unit)? = null

    /** Fired on ACTION_UP over a valid cell while [Tool.PLACE_ROBOT] is active. */
    var onRobotPlaceRequested: ((x: Int, y: Int) -> Unit)? = null

    private var arenaState: ArenaState = ArenaState()
    private var cellSizePx = 0f

    private data class DragState(
        val obstacleId: Int,
        val size: Int,
        val offsetX: Int,
        val offsetY: Int,
        val currentX: Int,
        val currentY: Int
    )
    private var dragState: DragState? = null

    private var downGridX = -1
    private var downGridY = -1
    private var downRawX = 0f
    private var downRawY = 0f
    private var isDragging = false
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop

    private val cellFillColor = context.themeColor(R.attr.arenaCellFill)
    private val gridLineColor = context.themeColor(R.attr.arenaGridLine)

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val obstaclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.arena_obstacle_fill)
    }
    private val obstacleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.arena_obstacle_text)
        textAlign = Paint.Align.CENTER
    }
    private val targetFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.arena_target_face)
        strokeWidth = dp(5f)
    }
    private val robotFootprintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.arena_robot_footprint)
    }
    private val robotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.arena_robot_fill)
    }
    private val robotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = ContextCompat.getColor(context, R.color.arena_robot_outline)
    }
    private val dragValidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = ContextCompat.getColor(context, R.color.arena_drag_ghost_valid)
    }
    private val dragInvalidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = ContextCompat.getColor(context, R.color.arena_drag_ghost_invalid)
    }

    fun setState(newState: ArenaState) {
        arenaState = newState
        if (width > 0) cellSizePx = width.toFloat() / arenaState.width
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val aspect = arenaState.height.toFloat() / arenaState.width.toFloat()
        val desiredHeight = (widthSize * aspect).toInt()
        setMeasuredDimension(widthSize, desiredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSizePx = if (arenaState.width > 0) w.toFloat() / arenaState.width else 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellSizePx <= 0f) return

        drawGrid(canvas)

        val draggedId = dragState?.obstacleId
        arenaState.obstacles.forEach { obstacle ->
            drawObstacle(canvas, obstacle, alpha = if (obstacle.id == draggedId) 80 else 255)
        }
        dragState?.let { drawDragGhost(canvas, it) }
        drawRobot(canvas, arenaState.robot)
    }

    private fun drawGrid(canvas: Canvas) {
        cellPaint.color = cellFillColor
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), cellPaint)
        gridLinePaint.color = gridLineColor
        for (col in 0..arenaState.width) {
            val x = col * cellSizePx
            canvas.drawLine(x, 0f, x, height.toFloat(), gridLinePaint)
        }
        for (row in 0..arenaState.height) {
            val y = row * cellSizePx
            canvas.drawLine(0f, y, width.toFloat(), y, gridLinePaint)
        }
    }

    private fun footprintRect(gridX: Int, gridY: Int, size: Int): RectF {
        val left = gridX * cellSizePx
        val top = gridY * cellSizePx
        val span = size * cellSizePx
        val inset = cellSizePx * 0.06f
        return RectF(left + inset, top + inset, left + span - inset, top + span - inset)
    }

    private fun drawObstacle(canvas: Canvas, obstacle: Obstacle, alpha: Int) {
        val rect = footprintRect(obstacle.x, obstacle.y, obstacle.size)
        val corner = cellSizePx * 0.2f

        obstaclePaint.alpha = alpha
        canvas.drawRoundRect(rect, corner, corner, obstaclePaint)

        val displayText = obstacle.targetId ?: obstacle.id.toString()
        val textScale = if (obstacle.targetId != null) TARGET_TEXT_SCALE else OBSTACLE_ID_TEXT_SCALE
        obstacleTextPaint.alpha = alpha
        obstacleTextPaint.textSize = cellSizePx * obstacle.size * textScale
        val fm = obstacleTextPaint.fontMetrics
        val textY = rect.centerY() - (fm.descent + fm.ascent) / 2f
        canvas.drawText(displayText, rect.centerX(), textY, obstacleTextPaint)

        targetFacePaint.alpha = alpha
        val inset = targetFacePaint.strokeWidth / 2f
        when (obstacle.imageFace) {
            Facing.NORTH -> canvas.drawLine(rect.left, rect.top + inset, rect.right, rect.top + inset, targetFacePaint)
            Facing.SOUTH -> canvas.drawLine(rect.left, rect.bottom - inset, rect.right, rect.bottom - inset, targetFacePaint)
            Facing.WEST -> canvas.drawLine(rect.left + inset, rect.top, rect.left + inset, rect.bottom, targetFacePaint)
            Facing.EAST -> canvas.drawLine(rect.right - inset, rect.top, rect.right - inset, rect.bottom, targetFacePaint)
        }
    }

    private fun drawDragGhost(canvas: Canvas, drag: DragState) {
        val rect = footprintRect(drag.currentX, drag.currentY, drag.size)
        val corner = cellSizePx * 0.2f
        val fits = arenaState.footprintInBounds(drag.currentX, drag.currentY, drag.size) &&
            !arenaState.overlapsAnyObstacle(drag.currentX, drag.currentY, drag.size, excludeId = drag.obstacleId)
        canvas.drawRoundRect(rect, corner, corner, if (fits) dragValidPaint else dragInvalidPaint)
    }

    private fun drawRobot(canvas: Canvas, robot: RobotState) {
        val half = robot.sizeInGrids / 2f
        val centerX = (robot.x + 0.5f) * cellSizePx
        val centerY = (robot.y + 0.5f) * cellSizePx
        val footprintLeft = centerX - half * cellSizePx
        val footprintTop = centerY - half * cellSizePx
        val footprintSize = robot.sizeInGrids * cellSizePx

        canvas.drawRect(
            footprintLeft, footprintTop,
            footprintLeft + footprintSize, footprintTop + footprintSize,
            robotFootprintPaint
        )

        canvas.save()
        canvas.rotate(robot.facing.degrees, centerX, centerY)
        val margin = footprintSize * 0.18f
        val path = Path().apply {
            moveTo(centerX, footprintTop + margin)
            lineTo(footprintLeft + footprintSize - margin, footprintTop + footprintSize - margin)
            lineTo(footprintLeft + margin, footprintTop + footprintSize - margin)
            close()
        }
        canvas.drawPath(path, robotFillPaint)
        canvas.drawPath(path, robotOutlinePaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cellSizePx <= 0f) return false
        val gridX = (event.x / cellSizePx).toInt()
        val gridY = (event.y / cellSizePx).toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.x
                downRawY = event.y
                downGridX = gridX
                downGridY = gridY
                isDragging = false
                arenaState.obstacleAt(gridX, gridY)?.let { hit ->
                    dragState = DragState(
                        obstacleId = hit.id,
                        size = hit.size,
                        offsetX = gridX - hit.x,
                        offsetY = gridY - hit.y,
                        currentX = hit.x,
                        currentY = hit.y
                    )
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = event.x - downRawX
                    val dy = event.y - downRawY
                    if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) isDragging = true
                }
                dragState?.let { d ->
                    val newX = gridX - d.offsetX
                    val newY = gridY - d.offsetY
                    if (d.currentX != newX || d.currentY != newY) {
                        dragState = d.copy(currentX = newX, currentY = newY)
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleUp(gridX, gridY)
                dragState = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragState = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleUp(gridX: Int, gridY: Int) {
        val drag = dragState
        // A drag that started on an obstacle always moves it, regardless of which tool button is
        // selected — otherwise dragging silently does nothing unless the user has deselected every
        // tool first, which isn't discoverable from the UI.
        if (isDragging) {
            if (drag != null) onObstacleMoveRequested?.invoke(drag.obstacleId, drag.currentX, drag.currentY)
            performClick()
            return
        }
        // Plain tap (no drag). A tap landing on an existing obstacle always targets that obstacle
        // — Remove tool deletes it, everything else edits its target face — regardless of which
        // tool is selected; otherwise e.g. tapping an obstacle with Place tool active would try to
        // place a new one on top of it and silently fail the overlap check. Only taps on empty
        // cells stay tool-specific.
        if (drag != null) {
            if (tool == Tool.REMOVE_OBSTACLE) {
                onObstacleRemoveRequested?.invoke(drag.obstacleId)
            } else {
                onObstacleTapRequested?.invoke(drag.obstacleId)
            }
        } else {
            when (tool) {
                Tool.PLACE_OBSTACLE -> onObstaclePlaceRequested?.invoke(downGridX, downGridY)
                Tool.PLACE_ROBOT -> onRobotPlaceRequested?.invoke(downGridX, downGridY)
                Tool.REMOVE_OBSTACLE, Tool.NONE -> Unit
            }
        }
        performClick()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        // Obstacle id (placeholder, pre-detection) is kept small and unobtrusive; the RPi's
        // recognized target result (checklist C.9) is the meaningful value, so it's shown larger.
        const val OBSTACLE_ID_TEXT_SCALE = 0.22f
        const val TARGET_TEXT_SCALE = 0.42f
    }
}

private fun Context.themeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}
