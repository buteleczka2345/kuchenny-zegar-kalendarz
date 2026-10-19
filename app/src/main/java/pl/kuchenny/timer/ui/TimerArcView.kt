package pl.kuchenny.timer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Pierścień postępu wokół tarczy timera. */
internal class TimerArcView(context: Context) : View(context) {

    private val bounds = RectF()
    private val arcStroke = 7f * resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.track
        style = Paint.Style.STROKE
        strokeWidth = arcStroke
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.mint
        style = Paint.Style.STROKE
        strokeWidth = arcStroke
        strokeCap = Paint.Cap.ROUND
    }

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Koło, a nie elipsa — nawet gdy widok jest wyższy niż szerszy.
        val size = min(width, height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        bounds.set(left + arcStroke, top + arcStroke, left + size - arcStroke, top + size - arcStroke)
        canvas.drawArc(bounds, -90f, 360f, false, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(bounds, -90f, 360f * progress, false, progressPaint)
        }
    }
}
