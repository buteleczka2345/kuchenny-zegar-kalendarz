package pl.kuchenny.budzik.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.kuchenny.budzik.AlarmSettings
import kotlin.math.abs

/** Melodie budzika: każda gra, nie przesterowuje i ma swoją nazwę. */
class MelodySynthTest {

    @Test
    fun `kazda melodia ma nazwe w ustawieniach`() {
        assertEquals(MelodySynth.COUNT, AlarmSettings.MELODY_NAMES.size)
    }

    @Test
    fun `kazda melodia gra i nie przesterowuje`() {
        for (style in 0 until MelodySynth.COUNT) {
            val samples = MelodySynth.pcm16(style)
            val peak = samples.maxOf { abs(it.toInt()) }
            assertTrue("melodia $style jest za krótka", samples.size > MelodySynth.SAMPLE_RATE / 2)
            assertTrue("melodia $style jest cicha", peak > Short.MAX_VALUE * 0.3)
            assertTrue("melodia $style przesterowuje", peak < Short.MAX_VALUE * 0.5)
        }
    }

    @Test
    fun `nowe melodie roznia sie od siebie`() {
        val synthesized = (MelodySynth.MELODIES.size until MelodySynth.COUNT)
            .map { MelodySynth.pcm16(it).toList() }
        assertEquals(synthesized.size, synthesized.distinct().size)
    }
}
