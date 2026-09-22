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

    /** Fired on a plain tap (no drag) on the robot's own footprint (tool == NONE) — rotate it. */
    var onRobotTapRequested: (() -> Unit)? = null

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
    private val gridLabelColor = context.themeColor(R.attr.arenaGridLabel)

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val gridLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gridLabelColor
        textAlign = Paint.Align.LEFT
    }
    private val obstaclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.arena_obstacle_fill)
    }
    private val obstacleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val obstacleIdTextColor = ContextCompat.getColor(context, R.color.arena_obstacle_text)
    private val targetDetectedTextColor = ContextCompat.getColor(context, R.color.arena_target_detected_text)
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

    // Reserves a small gutter on the left/bottom edges for the row/column coordinate labels
    // (0..19), so the numbers don't overlap the grid itself. Bottom is a bit taller than left so
    // the column labels sit with clear room above the view's true bottom edge (and away from the
    // enclosing card's rounded corners) rather than packed right against it.
    private val leftGutterPx = dp(16f)
    private val bottomGutterPx = dp(20f)
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
    }

    private fun gridWidthPx(viewWidth: Int): Float = (viewWidth - leftGutterPx).coerceAtLeast(0f)

    fun setState(newState: ArenaState) {
        arenaState = newState
        if (width > 0) cellSizePx = gridWidthPx(width) / arenaState.width
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val aspect = arenaState.height.toFloat() / arenaState.width.toFloat()
        val desiredHeight = (gridWidthPx(widthSize) * aspect + bottomGutterPx).toInt()
        setMeasuredDimension(widthSize, desiredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSizePx = if (arenaState.width > 0) gridWidthPx(w) / arenaState.width else 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellSizePx <= 0f) return

        canvas.save()
        canvas.translate(leftGutterPx, 0f)
        drawGrid(canvas)

        val draggedId = dragState?.obstacleId
        arenaState.obstacles.forEach { obstacle ->
            drawObstacle(canvas, obstacle, alpha = if (obstacle.id == draggedId) 80 else 255)
        }
        dragState?.let { drawDragGhost(canvas, it) }
        drawRobot(canvas, arenaState.robot)
        canvas.restore()

        drawAxisLabels(canvas)
    }

    /** Row numbers down the left gutter, column numbers along the bottom gutter. The underlying
     *  data model keeps its own (top-left origin, y increasing down) convention — matching
     *  AMDTool/the RPi wire protocol, see [com.example.mdpg26.arena.Obstacle] — but this view
     *  renders it bottom-up, so row 0 is drawn (and labeled) at the bottom of the grid. */
    private fun drawAxisLabels(canvas: Canvas) {
        val fm = labelTextPaint.fontMetrics
        val baselineOffset = -(fm.descent + fm.ascent) / 2f
        val gridHeightPx = arenaState.height * cellSizePx

        for (row in 0 until arenaState.height) {
            val cy = (arenaState.height - row - 1) * cellSizePx + cellSizePx / 2f
            canvas.drawText(row.toString(), leftGutterPx / 2f, cy + baselineOffset, labelTextPaint)
        }
        // Anchored close to the grid (not centered in the gutter) so it sits well clear of the
        // view's true bottom edge and the enclosing card's rounded bottom corners.
        val colLabelY = gridHeightPx + dp(11f) + baselineOffset
        for (col in 0 until arenaState.width) {
            val cx = leftGutterPx + col * cellSizePx + cellSizePx / 2f
            canvas.drawText(col.toString(), cx, colLabelY, labelTextPaint)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val gridWidthPx = arenaState.width * cellSizePx
        val gridHeightPx = arenaState.height * cellSizePx
        cellPaint.color = cellFillColor
        canvas.drawRect(0f, 0f, gridWidthPx, gridHeightPx, cellPaint)
        gridLinePaint.color = gridLineColor
        for (col in 0..arenaState.width) {
            val x = col * cellSizePx
            canvas.drawLine(x, 0f, x, gridHeightPx, gridLinePaint)
        }
        for (row in 0..arenaState.height) {
            val y = row * cellSizePx
            canvas.drawLine(0f, y, gridWidthPx, y, gridLinePaint)
        }
        drawGridLabels(canvas)
    }

    /**
     * Column indices along the top edge (anchored to each column's top-left) and row indices
     * along the left edge (anchored to each row's bottom-left) — offset to opposite corners so
     * the two label sets don't collide in the shared (0, 0) cell.
     */
    private fun drawGridLabels(canvas: Canvas) {
        gridLabelPaint.textSize = cellSizePx * GRID_LABEL_TEXT_SCALE
        val padding = cellSizePx * 0.08f
        val fm = gridLabelPaint.fontMetrics

        for (col in 0 until arenaState.width) {
            val x = col * cellSizePx + padding
            val y = padding - fm.ascent
            canvas.drawText(col.toString(), x, y, gridLabelPaint)
        }
        for (row in 0 until arenaState.height) {
            val x = padding
            val y = (row + 1) * cellSizePx - padding - fm.descent
            canvas.drawText(row.toString(), x, y, gridLabelPaint)
        }
    }

    /** Maps a footprint's data-space (top-left origin, y-down) row/col to the screen rect, flipped
     *  so row 0 renders at the bottom of the grid — see [drawAxisLabels]. */
    private fun footprintRect(gridX: Int, gridY: Int, size: Int): RectF {
        val left = gridX * cellSizePx
        val top = (arenaState.height - gridY - size) * cellSizePx
        val span = size * cellSizePx
        val inset = cellSizePx * 0.06f
        return RectF(left + inset, top + inset, left + span - inset, top + span - inset)
    }

    private fun drawObstacle(canvas: Canvas, obstacle: Obstacle, alpha: Int) {
        val rect = footprintRect(obstacle.x, obstacle.y, obstacle.size)
        val corner = cellSizePx * 0.2f

        obstaclePaint.alpha = alpha
        canvas.drawRoundRect(rect, corner, corner, obstaclePaint)

        val targetDetected = obstacle.targetId != null
        val displayText = obstacle.targetId ?: obstacle.id.toString()
        obstacleTextPaint.color = if (targetDetected) targetDetectedTextColor else obstacleIdTextColor
        obstacleTextPaint.alpha = alpha
        obstacleTextPaint.textSize = cellSizePx * obstacle.size * TARGET_TEXT_SCALE
        val fm = obstacleTextPaint.fontMetrics
        val textY = rect.centerY() - (fm.descent + fm.ascent) / 2f
        canvas.drawText(displayText, rect.centerX(), textY, obstacleTextPaint)

        targetFacePaint.alpha = alpha
        val inset = targetFacePaint.strokeWidth / 2f
        // NORTH/SOUTH/EAST/WEST here mean "top/bottom/right/left of the square as drawn on
        // screen" (see Facing's kdoc) — a purely screen-relative annotation, unrelated to which
        // data row is rendered where, so this doesn't change with the arena's row-0-at-bottom flip.
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
        val centerY = (arenaState.height - robot.y - 0.5f) * cellSizePx
        val footprintLeft = centerX - half * cellSizePx
        val footprintTop = centerY - half * cellSizePx
        val footprintSize = robot.sizeInGrids * cellSizePx

        canvas.drawRect(
            footprintLeft, footprintTop,
            footprintLeft + footprintSize, footprintTop + footprintSize,
            robotFootprintPaint
        )

        canvas.save()
        // Facing is screen-relative (NORTH = top of the footprint, as drawn) — same convention as
        // the obstacle face indicator — so this rotation doesn't change with the row-0-at-bottom
        // flip; only centerY (this footprint's position) needed that.
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
        val gridX = ((event.x - leftGutterPx) / cellSizePx).toInt()
        val gridY = arenaState.height - 1 - (event.y / cellSizePx).toInt()

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
        } else if (tool == Tool.NONE && arenaState.robot.contains(downGridX, downGridY)) {
            // A tap landing on the robot (no tool selected) rotates it in place — mirrors how a
            // tap on an obstacle edits its face regardless of a dedicated "edit" tool.
            onRobotTapRequested?.invoke()
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
        const val TARGET_TEXT_SCALE = 0.42f
        const val GRID_LABEL_TEXT_SCALE = 0.28f
    }
}

private fun Context.themeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}
