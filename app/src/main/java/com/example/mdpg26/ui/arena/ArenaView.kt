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
import com.example.mdpg26.arena.RobotState
import kotlin.math.abs

/**
 * Renders the 2D exploration arena (checklist C.5) and turns raw touch gestures into semantic
 * callbacks for placing, moving, removing and annotating obstacles (checklist C.6, C.7).
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

    enum class Tool { NONE, PLACE_OBSTACLE, REMOVE_OBSTACLE }

    var tool: Tool = Tool.NONE

    /** Fired on ACTION_UP over an empty cell while [Tool.PLACE_OBSTACLE] is active. */
    var onObstaclePlaceRequested: ((x: Int, y: Int) -> Unit)? = null

    /** Fired on ACTION_UP over an obstacle while [Tool.REMOVE_OBSTACLE] is active. */
    var onObstacleRemoveRequested: ((id: Int) -> Unit)? = null

    /** Fired when a drag that started on an obstacle ends (tool == NONE). */
    var onObstacleMoveRequested: ((id: Int, newX: Int, newY: Int) -> Unit)? = null

    /** Fired on a plain tap (no drag) on an existing obstacle (tool == NONE) — cycles its face. */
    var onObstacleTapRequested: ((id: Int) -> Unit)? = null

    private var arenaState: ArenaState = ArenaState()
    private var cellSizePx = 0f

    private data class DragState(val obstacleId: Int, val currentX: Int, val currentY: Int)
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
        strokeWidth = dp(4f)
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

    private fun cellRect(gridX: Int, gridY: Int): RectF {
        val left = gridX * cellSizePx
        val top = gridY * cellSizePx
        val inset = cellSizePx * 0.06f
        return RectF(left + inset, top + inset, left + cellSizePx - inset, top + cellSizePx - inset)
    }

    private fun drawObstacle(canvas: Canvas, obstacle: com.example.mdpg26.arena.Obstacle, alpha: Int) {
        val rect = cellRect(obstacle.x, obstacle.y)
        val corner = cellSizePx * 0.12f

        obstaclePaint.alpha = alpha
        canvas.drawRoundRect(rect, corner, corner, obstaclePaint)

        obstacleTextPaint.alpha = alpha
        obstacleTextPaint.textSize = cellSizePx * 0.5f
        val fm = obstacleTextPaint.fontMetrics
        val textY = rect.centerY() - (fm.descent + fm.ascent) / 2f
        canvas.drawText(obstacle.id.toString(), rect.centerX(), textY, obstacleTextPaint)

        obstacle.targetFace?.let { face ->
            targetFacePaint.alpha = alpha
            val inset = targetFacePaint.strokeWidth / 2f
            when (face) {
                Facing.NORTH -> canvas.drawLine(rect.left, rect.top + inset, rect.right, rect.top + inset, targetFacePaint)
                Facing.SOUTH -> canvas.drawLine(rect.left, rect.bottom - inset, rect.right, rect.bottom - inset, targetFacePaint)
                Facing.WEST -> canvas.drawLine(rect.left + inset, rect.top, rect.left + inset, rect.bottom, targetFacePaint)
                Facing.EAST -> canvas.drawLine(rect.right - inset, rect.top, rect.right - inset, rect.bottom, targetFacePaint)
            }
        }
    }

    private fun drawDragGhost(canvas: Canvas, drag: DragState) {
        val rect = cellRect(drag.currentX, drag.currentY)
        val corner = cellSizePx * 0.12f
        val paint = if (arenaState.isInBounds(drag.currentX, drag.currentY)) dragValidPaint else dragInvalidPaint
        canvas.drawRoundRect(rect, corner, corner, paint)
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
                if (tool == Tool.NONE) {
                    arenaState.obstacleAt(gridX, gridY)?.let { hit ->
                        dragState = DragState(hit.id, hit.x, hit.y)
                    }
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
                    if (d.currentX != gridX || d.currentY != gridY) {
                        dragState = d.copy(currentX = gridX, currentY = gridY)
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
        when (tool) {
            Tool.PLACE_OBSTACLE -> {
                if (!isDragging) onObstaclePlaceRequested?.invoke(downGridX, downGridY)
            }
            Tool.REMOVE_OBSTACLE -> {
                if (!isDragging) {
                    arenaState.obstacleAt(downGridX, downGridY)?.let { onObstacleRemoveRequested?.invoke(it.id) }
                }
            }
            Tool.NONE -> {
                if (drag != null) {
                    if (isDragging) {
                        onObstacleMoveRequested?.invoke(drag.obstacleId, gridX, gridY)
                    } else {
                        onObstacleTapRequested?.invoke(drag.obstacleId)
                    }
                }
            }
        }
        performClick()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private fun Context.themeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}
