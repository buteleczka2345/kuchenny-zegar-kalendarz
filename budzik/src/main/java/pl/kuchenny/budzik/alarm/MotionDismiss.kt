package pl.kuchenny.budzik.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import pl.kuchenny.budzik.core.GravityFilter

/**
 * Wyłączanie budzika ruchem: potrząśnięciem albo odwróceniem telefonu ekranem do dołu.
 */
class MotionDismiss(
    context: Context,
    private val shakeEnabled: () -> Boolean,
    /** Próg przyspieszenia w m/s² — ustawiana siła potrząśnięcia. */
    private val shakeThreshold: () -> Float,
    private val flipEnabled: () -> Boolean,
    private val onDismiss: () -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val filter = GravityFilter()
    private var lastShakeAt = 0L
    private var faceDownSince = 0L
    private var fired = false

    fun start() {
        fired = false
        filter.reset()
        lastShakeAt = 0L
        faceDownSince = 0L
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (fired || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val now = System.currentTimeMillis()

        val acceleration = filter.linear(event.values[0], event.values[1], event.values[2])

        if (flipEnabled() && event.values[2] < FLIP_THRESHOLD) {
            if (faceDownSince == 0L) {
                faceDownSince = now
            } else if (now - faceDownSince >= FLIP_HOLD_MS) {
                dismiss()
                return
            }
        } else {
            faceDownSince = 0L
        }

        if (shakeEnabled()) {
            if (acceleration >= shakeThreshold() && now - lastShakeAt >= SHAKE_DEBOUNCE_MS) {
                lastShakeAt = now
                dismiss()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun dismiss() {
        if (fired) return
        fired = true
        onDismiss()
    }

    private companion object {
        const val FLIP_THRESHOLD = -7f
        const val FLIP_HOLD_MS = 600L
        const val SHAKE_DEBOUNCE_MS = 1_500L
    }
}
