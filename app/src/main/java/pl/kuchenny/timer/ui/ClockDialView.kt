package pl.kuchenny.timer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import pl.kuchenny.timer.core.TimerLimits
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tarcza zegara na pełny ekran.
 *
 * Pośrodku pokazuje odliczany czas ([centerValue] i [centerCaption]), a nie
 * czas ustawiany — dzięki temu widać, ile zostało do zadzwonienia.
 * Dotknięcie palcem od razu ustawia wartość; ekran nie przewija się pod palcem,
 * bo tarcza nie leży w żadnym ScrollView.
 */
internal class ClockDialView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.background
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.track
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.muted
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.mint
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.text
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
        isSubpixelText = true
    }
    private val selectedLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.background
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
        typeface = Typeface.DEFAULT_BOLD
        isSubpixelText = true
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.text
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isSubpixelText = true
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.mint
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isSubpixelText = true
    }

    private var centerTouch = false
    private var gestureChanged = false
    private var lastTouchAngle = 0f
    private var dragMinutesValue = 0f

    var hours: Int = 0
        private set
    var minutes: Int = 0
        private set

    /** `true` — przesuwanie ustawia minuty, `false` — godziny. */
    var editingMinutes: Boolean = true
        private set

    /** Trwa przeciąganie palcem po tarczy. */
    var isUserInteracting: Boolean = false
        private set

    /** Duży czas w środku tarczy: pozostałe odliczanie albo ustawiony czas. */
    var centerValue: String = "15:00"
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Podpis pod czasem, np. „DO KOŃCA” albo „MINUT”. */
    var centerCaption: String = "MINUT"
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var onTimePreview: ((Int, Int) -> Unit)? = null
    var onTimeSettled: ((Int, Int) -> Unit)? = null

    init {
        isClickable = true
        updateContentDescription()
    }

    fun setEditingMinutes(editingMinutes: Boolean) {
        if (this.editingMinutes != editingMinutes) {
            this.editingMinutes = editingMinutes
            updateContentDescription()
            invalidate()
        }
    }

    fun setTime(hours: Int, minutes: Int) {
        // Nie walczymy z palcem użytkownika.
        if (isUserInteracting) return
        this.hours = hours.coerceIn(0, 23)
        this.minutes = minutes.coerceIn(0, 59)
        updateContentDescription()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.46f
        canvas.drawCircle(centerX, centerY, radius, facePaint)
        canvas.drawCircle(centerX, centerY, radius, ringPaint)

        if (editingMinutes) {
            drawMinuteFace(canvas, centerX, centerY, radius)
        } else {
            drawHourFace(canvas, centerX, centerY, radius)
        }
        drawCenter(canvas, centerX, centerY, radius)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val centerX = width / 2f
        val centerY = height / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isUserInteracting = true
                gestureChanged = false
                // Sam środek z cyframi nie zmienia czasu — tam tylko patrzymy.
                centerTouch = distance(event.x, event.y, centerX, centerY) <
                    min(width, height) * CENTER_TOUCH_RADIUS
                if (!centerTouch) {
                    updateFromTouch(event.x, event.y, centerX, centerY)
                    dragMinutesValue = (hours * 60 + minutes).toFloat()
                    lastTouchAngle = angleOf(event.x, event.y, centerX, centerY)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!centerTouch) {
                    if (editingMinutes) {
                        turnMinutes(event.x, event.y, centerX, centerY)
                    } else {
                        updateFromTouch(event.x, event.y, centerX, centerY)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isUserInteracting = false
                if (!centerTouch && gestureChanged) onTimeSettled?.invoke(hours, minutes)
                gestureChanged = false
                centerTouch = false
                updateContentDescription()
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isUserInteracting = false
                gestureChanged = false
                centerTouch = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawMinuteFace(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        for (minute in 0 until 60) {
            val angle = angleFor(minute / 60f)
            val outerRadius = radius * 0.84f
            val innerRadius = if (minute % 5 == 0) radius * 0.74f else radius * 0.79f
            canvas.drawLine(
                pointX(centerX, innerRadius, angle),
                pointY(centerY, innerRadius, angle),
                pointX(centerX, outerRadius, angle),
                pointY(centerY, outerRadius, angle),
                tickPaint
            )
            if (minute % 5 == 0) {
                drawSelectableNumber(
                    canvas,
                    minute.toString().padStart(2, '0'),
                    pointX(centerX, radius * 0.64f, angle),
                    pointY(centerY, radius * 0.64f, angle),
                    minutes == minute,
                    radius * 0.13f
                )
            }
        }
    }

    private fun drawHourFace(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        for (position in 0 until 12) {
            val angle = angleFor(position / 12f)
            val outerHour = if (position == 0) 12 else position
            drawSelectableNumber(
                canvas,
                outerHour.toString(),
                pointX(centerX, radius * 0.75f, angle),
                pointY(centerY, radius * 0.75f, angle),
                hours == outerHour,
                radius * 0.13f
            )

            val innerHour = if (position == 0) 0 else 12 + position
            drawSelectableNumber(
                canvas,
                innerHour.toString().padStart(2, '0'),
                pointX(centerX, radius * 0.47f, angle),
                pointY(centerY, radius * 0.47f, angle),
                hours == innerHour,
                radius * 0.12f
            )
        }
    }

    private fun drawSelectableNumber(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        selected: Boolean,
        selectionRadius: Float
    ) {
        val paint = if (selected) selectedLabelPaint else labelPaint
        if (selected) canvas.drawCircle(x, y, selectionRadius, selectedPaint)
        canvas.drawText(value, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun drawCenter(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val value = centerValue
        valuePaint.textSize = when {
            value.length <= 5 -> radius * 0.30f
            value.length <= 6 -> radius * 0.25f
            else -> radius * 0.20f
        }
        val valueBaseline = centerY + valuePaint.textSize * 0.22f
        canvas.drawText(value, centerX, valueBaseline, valuePaint)

        captionPaint.textSize = radius * 0.085f
        canvas.drawText(centerCaption, centerX, centerY + radius * 0.34f, captionPaint)
    }

    private fun updateFromTouch(x: Float, y: Float, centerX: Float, centerY: Float) {
        val angle = angleOf(x, y, centerX, centerY)
        if (editingMinutes) {
            val newMinutes = (angle / 6f).roundToInt() % 60
            if (newMinutes != minutes) {
                minutes = newMinutes
                gestureChanged = true
                notifyTimeChanged()
            }
        } else {
            val position = (angle / 30f).roundToInt() % 12
            val innerRing = distance(x, y, centerX, centerY) < min(width, height) * HOUR_RING_SPLIT
            val newHours = when {
                !innerRing && position == 0 -> 12
                !innerRing -> position
                position == 0 -> 0
                else -> 12 + position
            }
            if (newHours != hours) {
                hours = newHours
                gestureChanged = true
                notifyTimeChanged()
            }
        }
        invalidate()
    }

    /**
     * Przesuwanie w trybie minut jako pokrętło: po pełnym obrocie czas idzie dalej,
     * więc z 0:59 robi się 1:00, a z 1:59 — 2:00, aż do 23:59.
     */
    private fun turnMinutes(x: Float, y: Float, centerX: Float, centerY: Float) {
        val angle = angleOf(x, y, centerX, centerY)
        var delta = angle - lastTouchAngle
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        lastTouchAngle = angle

        dragMinutesValue = (dragMinutesValue + delta / 6f)
            .coerceIn(0f, TimerLimits.MAX_MINUTES.toFloat())
        val total = dragMinutesValue.roundToInt()
        val newHours = total / 60
        val newMinutes = total % 60
        if (newHours != hours || newMinutes != minutes) {
            hours = newHours
            minutes = newMinutes
            gestureChanged = true
            notifyTimeChanged()
        }
        invalidate()
    }

    private fun angleOf(x: Float, y: Float, centerX: Float, centerY: Float): Float {
        val dx = x - centerX
        val dy = y - centerY
        return ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0)
            .toFloat()
    }

    private fun notifyTimeChanged() {
        // Delikatne stuknięcie przy każdym kroku — jak w mechanicznym zegarze.
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        updateContentDescription()
        onTimePreview?.invoke(hours, minutes)
    }

    private fun updateContentDescription() {
        contentDescription = "Ustawiono ${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}. " +
            if (editingMinutes) {
                "Przesuń palcem po tarczy, aby wybrać minuty."
            } else {
                "Przesuń palcem po tarczy, aby wybrać godziny."
            }
    }

    private fun angleFor(fraction: Float): Float = fraction * 360f - 90f

    private fun pointX(center: Float, radius: Float, angle: Float): Float =
        center + radius * cos(Math.toRadians(angle.toDouble())).toFloat()

    private fun pointY(center: Float, radius: Float, angle: Float): Float =
        center + radius * sin(Math.toRadians(angle.toDouble())).toFloat()

    private fun distance(x: Float, y: Float, centerX: Float, centerY: Float): Float {
        val dx = x - centerX
        val dy = y - centerY
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        /** Obszar z cyframi w środku — dotknięcie go nie zmienia czasu. */
        const val CENTER_TOUCH_RADIUS = 0.30f
        const val HOUR_RING_SPLIT = 0.61f
    }
}
