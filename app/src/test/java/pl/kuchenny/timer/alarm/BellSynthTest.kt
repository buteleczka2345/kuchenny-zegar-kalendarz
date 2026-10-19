package pl.kuchenny.timer.alarm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Synteza dzwonka: długość bufora, głośność i brak przesterowania. */
class BellSynthTest {

    @Test
    fun `bufor ma dlugosc wynikajaca z czestotliwosci i czasu`() {
        val samples = BellSynth.pcm16(
            frequencies = doubleArrayOf(880.0, 1_760.0),
            durationMs = 700L,
            decayPerSecond = 5.3
        )
        assertEquals(BellSynth.SAMPLE_RATE * 700 / 1_000, samples.size)
    }

    @Test
    fun `dzwonek nie jest cisza`() {
        val samples = BellSynth.pcm16(
            frequencies = doubleArrayOf(880.0, 1_760.0, 2_640.0),
            durationMs = 700L,
            decayPerSecond = 5.3
        )
        assertTrue(samples.any { abs(it.toInt()) > 1_000 })
    }

    @Test
    fun `szczyt nie przekracza zapasu glosnosci`() {
        val samples = BellSynth.pcm16(
            frequencies = doubleArrayOf(1_046.5, 2_093.0, 3_139.5),
            durationMs = 700L,
            decayPerSecond = 5.3
        )
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue("szczyt $peak", peak <= (Short.MAX_VALUE * 0.83).toInt())
    }

    @Test
    fun `zestaw ma osiem stylow po trzy skladowe`() {
        for (style in 0 until 8) {
            assertEquals(3, AlarmSounds.frequencies(style).size)
        }
    }

    @Test
    fun `poza zakresem wybierany jest skrajny styl`() {
        assertArrayEquals(
            AlarmSounds.frequencies(0),
            AlarmSounds.frequencies(-5),
            0.001
        )
        assertArrayEquals(
            AlarmSounds.frequencies(7),
            AlarmSounds.frequencies(99),
            0.001
        )
    }
}
