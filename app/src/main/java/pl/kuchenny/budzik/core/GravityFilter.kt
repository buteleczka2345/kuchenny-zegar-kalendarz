package pl.kuchenny.budzik.core

import kotlin.math.sqrt

/**
 * Odejmuje grawitację od odczytu akcelerometru — zostaje sam ruch telefonu.
 *
 * Ten sam filtr liczy siłę potrząśnięcia przy dzwonieniu i przy sprawdzaniu
 * na ekranie, więc sprawdzenie pokazuje dokładnie to, co zobaczy alarm.
 */
class GravityFilter(private val alpha: Float = 0.82f) {

    private val gravity = FloatArray(3)
    private var hasGravity = false

    fun reset() {
        hasGravity = false
    }

    /** Siła ruchu bez grawitacji w m/s². */
    fun linear(x: Float, y: Float, z: Float): Float {
        val values = floatArrayOf(x, y, z)
        for (axis in 0..2) {
            gravity[axis] = if (!hasGravity) {
                values[axis]
            } else {
                alpha * gravity[axis] + (1f - alpha) * values[axis]
            }
        }
        hasGravity = true
        val dx = x - gravity[0]
        val dy = y - gravity[1]
        val dz = z - gravity[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
