package com.example.mdpg26.ui.arena

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.mdpg26.R

/**
 * Grass verge, a red/white curb, then a thin asphalt edge (so the white stripes read against a
 * light grid surface too) — the same racetrack border as the Task 2
 * schematic ([com.example.mdpg26.ui.task2.Task2ArenaView]), as a background for a padded
 * container so the Wheel tab's arena grid sits "on the track". Pad the host by [borderPx].
 */
class TrackBorderDrawable(context: Context) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val grassPx = 4f * density
    private val curbPx = 5f * density
    private val asphaltPx = 2f * density
    private val cornerPx = 14f * density
    val borderPx: Int = (grassPx + curbPx + asphaltPx).toInt()

    private val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.track_grass)
    }
    private val asphaltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.track_asphalt)
    }
    private val curbRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = curbPx
        color = ContextCompat.getColor(context, R.color.track_curb_red)
    }
    private val curbWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = curbPx
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val outerRect = RectF()
    private val asphaltRect = RectF()
    private val curbRect = RectF()
    private val curbPath = Path()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        outerRect.set(bounds)
        asphaltRect.set(outerRect)
        asphaltRect.inset(grassPx, grassPx)
        val inset = grassPx + curbPx / 2f
        curbRect.set(outerRect.left + inset, outerRect.top + inset, outerRect.right - inset, outerRect.bottom - inset)
        curbPath.reset()
        curbPath.addRoundRect(curbRect, cornerPx - inset, cornerPx - inset, Path.Direction.CW)
        val dash = 3f * curbPx
        curbRedPaint.pathEffect = DashPathEffect(floatArrayOf(dash, dash), 0f)
        curbWhitePaint.pathEffect = DashPathEffect(floatArrayOf(dash, dash), dash)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(outerRect, cornerPx, cornerPx, grassPaint)
        canvas.drawRoundRect(asphaltRect, cornerPx - grassPx, cornerPx - grassPx, asphaltPaint)
        canvas.drawPath(curbPath, curbRedPaint)
        canvas.drawPath(curbPath, curbWhitePaint)
    }

    override fun setAlpha(alpha: Int) {
        grassPaint.alpha = alpha
        asphaltPaint.alpha = alpha
        curbRedPaint.alpha = alpha
        curbWhitePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        grassPaint.colorFilter = colorFilter
        asphaltPaint.colorFilter = colorFilter
        curbRedPaint.colorFilter = colorFilter
        curbWhitePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
