package pl.kuchenny.budzik.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import pl.kuchenny.budzik.core.GravityFilter

/**
 * Sprawdzanie potrząśnięcia na ekranie ustawień: podaje siłę każdego ruchu,
 * liczoną tak samo jak przy dzwonieniu budzika ([MotionDismiss]).
 */
class ShakeTester(
    context: Context,
    private val onReading: (acceleration: Float) -> Unit
) : SensorEventListener {

    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val filter = GravityFilter()

    /** Zwraca `false`, gdy telefon nie ma akcelerometru. */
    fun start(): Boolean {
        val sensor = accelerometer ?: return false
        filter.reset()
        return sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) == true
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        onReading(filter.linear(event.values[0], event.values[1], event.values[2]))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
