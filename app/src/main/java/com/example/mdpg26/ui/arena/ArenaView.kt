package com.example.mdpg26.ui.arena

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.example.mdpg26.R
import com.example.mdpg26.arena.ArenaState
import com.example.mdpg26.arena.Facing
import com.example.mdpg26.arena.OBSTACLE_SIZE_GRIDS
import com.example.mdpg26.arena.Obstacle
import com.example.mdpg26.arena.RobotState
import kotlin.math.abs
import kotlin.math.floor

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

    /** Fired when a [Tool.PLACE_OBSTACLE] press-and-hold (see [placementCell]) is released over an
     *  empty, in-bounds cell. */
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

    private data class GridCell(val x: Int, val y: Int)

    // Press-and-hold placement with [Tool.PLACE_OBSTACLE]: while the finger is down (having
    // started on an empty cell), it tracks the cell under the finger — shown via [Guide] — and
    // releasing places the obstacle there. placementCell is null while the finger is off the grid
    // (release cancels).
    private var isPlacing = false
    private var placementCell: GridCell? = null

    /** Where an obstacle would land mid-gesture (placing or dragging): drives the guide lines
     *  crossing at its bottom-left corner, the highlighted axis ticks, and the coordinate badge.
     *  [valid] is false when it would overlap another obstacle. */
    private data class Guide(val x: Int, val y: Int, val size: Int, val valid: Boolean)

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
    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val guideFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.arena_obstacle_text)
        textAlign = Paint.Align.CENTER
        textSize = dp(12f)
        typeface = Typeface.DEFAULT_BOLD
    }

    // Reserves a small gutter on all four edges for the row/column coordinate ticks (0..20), so
    // the numbers don't overlap the grid itself. The top/right gutters only need to fit half a
    // label (the axis's highest tick is centered right on the grid's top/right edge); bottom is a
    // bit taller than the others so the column labels sit with clear room below the grid.
    private val leftGutterPx = dp(16f)
    private val topGutterPx = dp(10f)
    private val rightGutterPx = dp(10f)
    private val bottomGutterPx = dp(20f)
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
    }
    /** Axis tick matching the guide lines — see [drawAxisLabels]. */
    private val highlightedLabelTextPaint = Paint(labelTextPaint).apply {
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun gridWidthPx(viewWidth: Int): Float =
        (viewWidth - leftGutterPx - rightGutterPx).coerceAtLeast(0f)

    fun setState(newState: ArenaState) {
        arenaState = newState
        if (width > 0) cellSizePx = gridWidthPx(width) / arenaState.width
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val aspect = arenaState.height.toFloat() / arenaState.width.toFloat()
        val desiredHeight = (gridWidthPx(widthSize) * aspect + topGutterPx + bottomGutterPx).toInt()
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
        canvas.translate(leftGutterPx, topGutterPx)
        drawGrid(canvas)

        val draggedId = dragState?.obstacleId
        arenaState.obstacles.forEach { obstacle ->
            drawObstacle(canvas, obstacle, alpha = if (obstacle.id == draggedId) 80 else 255)
        }
        dragState?.let { drawDragGhost(canvas, it) }
        drawRobot(canvas, arenaState.robot)
        val guide = activeGuide()
        if (guide != null) {
            // A drag's ghost outline is drawn above (it also covers off-grid drops, which have
            // no guide); a placement has no existing obstacle, so draw its outline here.
            if (isPlacing) drawFootprintOutline(canvas, guide.x, guide.y, guide.size, guide.valid)
            drawGuideLines(canvas, guide)
        }
        canvas.restore()

        drawAxisLabels(canvas, guide)
        guide?.let { drawCoordinateBadge(canvas, it) }
    }

    /** Ruler-style axis ticks (0..width down the left gutter, 0..height along the bottom gutter)
     *  at grid-line positions rather than per-cell, so the x- and y-axes share a single "0" tick
     *  at the grid's bottom-left corner. The underlying data model keeps its own (top-left origin,
     *  y increasing down) convention — matching AMDTool/the RPi wire protocol, see
     *  [com.example.mdpg26.arena.Obstacle] — but this view renders it bottom-up, so 0 is drawn at
     *  the bottom of the grid. */
    private fun drawAxisLabels(canvas: Canvas, guide: Guide?) {
        val fm = labelTextPaint.fontMetrics
        val baselineOffset = -(fm.descent + fm.ascent) / 2f
        val gridHeightPx = arenaState.height * cellSizePx

        // While placing/dragging, the ticks the guide lines run through are highlighted so the
        // obstacle's (x, y) reads straight off the axes. The shared origin "0" lights up for either.
        if (guide != null) highlightedLabelTextPaint.color = guideColor(guide)
        fun paintFor(highlighted: Boolean) = if (highlighted) highlightedLabelTextPaint else labelTextPaint

        for (row in 0..arenaState.height) {
            val cy = topGutterPx + gridHeightPx - row * cellSizePx
            val highlighted = guide != null && (row == guide.y || (row == 0 && guide.x == 0))
            canvas.drawText(row.toString(), leftGutterPx / 2f, cy + baselineOffset, paintFor(highlighted))
        }
        // Anchored close to the grid (not centered in the gutter) so it sits well clear of the
        // view's true bottom edge. Starts at 1 — the shared origin's "0" is already drawn by the
        // row loop above.
        val colLabelY = topGutterPx + gridHeightPx + dp(11f) + baselineOffset
        for (col in 1..arenaState.width) {
            val cx = leftGutterPx + col * cellSizePx
            canvas.drawText(col.toString(), cx, colLabelY, paintFor(guide != null && col == guide.x))
        }
    }

    /** The in-progress placement or drag, if any. A tap-in-progress on an obstacle (not yet past
     *  touch slop) shows nothing, nor does a drag that's left the grid — dropping there deletes the
     *  obstacle, which the red ghost outline already conveys. */
    private fun activeGuide(): Guide? {
        placementCell?.let { cell ->
            return Guide(cell.x, cell.y, OBSTACLE_SIZE_GRIDS, valid = canPlaceAt(cell))
        }
        val drag = dragState?.takeIf { isDragging } ?: return null
        if (!arenaState.footprintInBounds(drag.currentX, drag.currentY, drag.size)) return null
        val overlaps = arenaState.overlapsAnyObstacle(drag.currentX, drag.currentY, drag.size, excludeId = drag.obstacleId)
        return Guide(drag.currentX, drag.currentY, drag.size, valid = !overlaps)
    }

    /** Green where the obstacle can go, red where it'd overlap another (release there is ignored). */
    private fun guideColor(guide: Guide): Int =
        if (guide.valid) dragValidPaint.color else dragInvalidPaint.color

    private fun canPlaceAt(cell: GridCell): Boolean =
        !arenaState.overlapsAnyObstacle(cell.x, cell.y, OBSTACLE_SIZE_GRIDS)

    /** Two full-span guide lines crossing at the guide's bottom-left corner (the obstacle's (x, y)
     *  — same row-0-at-bottom flip as [footprintRect]), lining up with the axis ticks. */
    private fun drawGuideLines(canvas: Canvas, guide: Guide) {
        val gridWidthPx = arenaState.width * cellSizePx
        val gridHeightPx = arenaState.height * cellSizePx
        val cornerX = guide.x * cellSizePx
        val cornerY = (arenaState.height - guide.y) * cellSizePx
        val color = guideColor(guide)

        guideLinePaint.color = color
        canvas.drawLine(0f, cornerY, gridWidthPx, cornerY, guideLinePaint)
        canvas.drawLine(cornerX, 0f, cornerX, gridHeightPx, guideLinePaint)

        guideFillPaint.color = color
        canvas.drawCircle(cornerX, cornerY, dp(3.5f), guideFillPaint)
    }

    /** "(x, y)" pill floating above the obstacle — clear of the finger, which sits on top of the
     *  obstacle itself — or below it when there's no room above. Drawn in view coordinates (not
     *  grid-translated) so it can spill into the gutters near the grid's edges. */
    private fun drawCoordinateBadge(canvas: Canvas, guide: Guide) {
        val text = context.getString(R.string.arena_coordinate_badge_fmt, guide.x, guide.y)
        val fm = badgeTextPaint.fontMetrics
        val badgeWidth = badgeTextPaint.measureText(text) + dp(16f)
        val badgeHeight = fm.descent - fm.ascent + dp(8f)
        val gap = dp(BADGE_FINGER_CLEARANCE_DP)

        val footprint = footprintRect(guide.x, guide.y, guide.size).apply { offset(leftGutterPx, topGutterPx) }
        val above = footprint.top - gap - badgeHeight
        val top = if (above >= 0f) above else footprint.bottom + gap
        val left = (footprint.centerX() - badgeWidth / 2f).coerceIn(0f, (width - badgeWidth).coerceAtLeast(0f))
        val badge = RectF(left, top, left + badgeWidth, top + badgeHeight)

        guideFillPaint.color = guideColor(guide)
        canvas.drawRoundRect(badge, badgeHeight / 2f, badgeHeight / 2f, guideFillPaint)
        canvas.drawText(text, badge.centerX(), badge.centerY() - (fm.descent + fm.ascent) / 2f, badgeTextPaint)
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
        val fits = arenaState.footprintInBounds(drag.currentX, drag.currentY, drag.size) &&
            !arenaState.overlapsAnyObstacle(drag.currentX, drag.currentY, drag.size, excludeId = drag.obstacleId)
        drawFootprintOutline(canvas, drag.currentX, drag.currentY, drag.size, fits)
    }

    private fun drawFootprintOutline(canvas: Canvas, gridX: Int, gridY: Int, size: Int, valid: Boolean) {
        val rect = footprintRect(gridX, gridY, size)
        val corner = cellSizePx * 0.2f
        canvas.drawRoundRect(rect, corner, corner, if (valid) dragValidPaint else dragInvalidPaint)
    }

    private fun drawRobot(canvas: Canvas, robot: RobotState) {
        // Same bottom-left-origin flip as footprintRect() — robot.x/y is the footprint's
        // bottom-left cell, matching Obstacle's convention.
        val footprintLeft = robot.x * cellSizePx
        val footprintTop = (arenaState.height - robot.y - robot.sizeInGrids) * cellSizePx
        val footprintSize = robot.sizeInGrids * cellSizePx
        val centerX = footprintLeft + footprintSize / 2f
        val centerY = footprintTop + footprintSize / 2f

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
        // floor() rather than toInt(): truncation would map a touch just outside the grid's
        // left/top edge (a fraction of a cell negative) onto row/column 0 instead of off-grid.
        val gridX = floor((event.x - leftGutterPx) / cellSizePx).toInt()
        val gridY = arenaState.height - 1 - floor((event.y - topGutterPx) / cellSizePx).toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.x
                downRawY = event.y
                downGridX = gridX
                downGridY = gridY
                isDragging = false
                isPlacing = false
                placementCell = null
                val hit = arenaState.obstacleAt(gridX, gridY)
                if (hit != null) {
                    dragState = DragState(
                        obstacleId = hit.id,
                        size = hit.size,
                        offsetX = gridX - hit.x,
                        offsetY = gridY - hit.y,
                        currentX = hit.x,
                        currentY = hit.y
                    )
                } else if (tool == Tool.PLACE_OBSTACLE &&
                    arenaState.footprintInBounds(gridX, gridY, OBSTACLE_SIZE_GRIDS)
                ) {
                    isPlacing = true
                    updatePlacementCell(gridX, gridY)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val dx = event.x - downRawX
                    val dy = event.y - downRawY
                    if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                        isDragging = true
                        // Guides only show once it's clearly a drag (see activeGuide()), so redraw
                        // now even if the obstacle hasn't left its starting cell yet.
                        if (dragState != null) invalidate()
                    }
                }
                dragState?.let { d ->
                    val newX = gridX - d.offsetX
                    val newY = gridY - d.offsetY
                    if (d.currentX != newX || d.currentY != newY) {
                        dragState = d.copy(currentX = newX, currentY = newY)
                        if (arenaState.footprintInBounds(newX, newY, d.size)) tickCellChange()
                        invalidate()
                    }
                }
                if (isPlacing) updatePlacementCell(gridX, gridY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleUp(gridX, gridY)
                clearGesture()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearGesture()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun clearGesture() {
        dragState = null
        isPlacing = false
        placementCell = null
        invalidate()
    }

    /** Moves the placement guides to the cell under the finger, hiding them while it's off-grid. */
    private fun updatePlacementCell(gridX: Int, gridY: Int) {
        val cell = GridCell(gridX, gridY)
            .takeIf { arenaState.footprintInBounds(gridX, gridY, OBSTACLE_SIZE_GRIDS) }
        if (cell == placementCell) return
        placementCell = cell
        if (cell != null) tickCellChange()
        invalidate()
    }

    /** Light tick each time a placed/dragged obstacle snaps to a new cell, so sliding between
     *  cells is felt as well as seen. */
    private fun tickCellChange() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun handleUp(gridX: Int, gridY: Int) {
        if (isPlacing) {
            // Placement is decided by where the finger is released, not where it went down. Off
            // the grid or over an existing obstacle cancels, rather than opening the face picker
            // for a placement the view model would only reject.
            placementCell?.takeIf(::canPlaceAt)?.let { onObstaclePlaceRequested?.invoke(it.x, it.y) }
            performClick()
            return
        }
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
                Tool.PLACE_ROBOT -> onRobotPlaceRequested?.invoke(downGridX, downGridY)
                // Empty in-bounds cells are handled by the press-and-hold placement above; a
                // PLACE_OBSTACLE press that gets here started off the grid, so there's nothing to place.
                Tool.PLACE_OBSTACLE, Tool.REMOVE_OBSTACLE, Tool.NONE -> Unit
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
        /** Gap between the obstacle and its coordinate badge — enough to clear a fingertip. */
        const val BADGE_FINGER_CLEARANCE_DP = 28f
    }
}

private fun Context.themeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}
