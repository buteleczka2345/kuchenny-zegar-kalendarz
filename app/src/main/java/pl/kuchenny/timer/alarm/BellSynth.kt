package pl.kuchenny.timer.alarm

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synteza dzwonka kuchennego.
 *
 * Czysta matematyka, więc da się sprawdzić testem bez urządzenia.
 */
object BellSynth {

    const val SAMPLE_RATE = 44_100
    const val BELL_DURATION_MS = 700L

    private val MIX = doubleArrayOf(0.54, 0.29, 0.17)
    private const val MAX_AMPLITUDE = Short.MAX_VALUE * 0.82

    /**
     * @param frequencies częstotliwość podstawowa i jej składowe
     * @param decayPerSecond tempo wybrzmiewania; większa wartość = ostrzejszy dzwonek
     */
    fun pcm16(
        frequencies: DoubleArray,
        durationMs: Long,
        decayPerSecond: Double,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        val sampleCount = (sampleRate * durationMs / 1_000L).toInt().coerceAtLeast(1)
        val samples = ShortArray(sampleCount)
        for (index in samples.indices) {
            val time = index.toDouble() / sampleRate
            val envelope = exp(-time * decayPerSecond) * (1.0 - exp(-time * 90.0))
            var value = 0.0
            for (partial in frequencies.indices) {
                val weight = MIX.getOrElse(partial) { 0.0 }
                value += weight * sin(2.0 * PI * frequencies[partial] * time)
            }
            samples[index] = (value * envelope * MAX_AMPLITUDE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return samples
    }
}

/** Zestaw ośmiu dzwonków kuchennych. */
object AlarmSounds {

    private val FUNDAMENTALS = doubleArrayOf(
        1_046.5, 880.0, 659.3, 784.0, 1_174.7, 740.0, 987.8, 1_396.9
    )

    fun frequencies(style: Int): DoubleArray {
        val index = style.coerceIn(0, FUNDAMENTALS.lastIndex)
        val fundamental = FUNDAMENTALS[index]
        val overtones = when (index % 4) {
            0 -> doubleArrayOf(1.0, 2.0, 3.0)
            1 -> doubleArrayOf(1.0, 2.4, 4.0)
            2 -> doubleArrayOf(1.0, 1.5, 2.25)
            else -> doubleArrayOf(1.0, 3.0, 5.0)
        }
        return overtones.map { fundamental * it }.toDoubleArray()
    }
}
