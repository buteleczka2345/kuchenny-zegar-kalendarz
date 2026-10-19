package pl.kuchenny.budzik.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Narastanie głośności od ciszy do wybranego poziomu. */
class RampTest {

    @Test
    fun `bez narastania glosnosc jest od razu pelna`() {
        assertEquals(1f, Ramp.volume(0L, 0L), 0.001f)
    }

    @Test
    fun `w polowie czasu jest jeszcze cicho`() {
        assertEquals(0.125f, Ramp.volume(30_000L, 60_000L), 0.001f)
    }

    @Test
    fun `glosnosc rosnie przez caly czas narastania`() {
        val samples = (0..120).map { Ramp.volume(it * 1_000L, 120_000L) }
        assertEquals(samples.sorted(), samples)
        // Po 12 sekundach z dwóch minut to wciąż szept, nie pełny dźwięk.
        assertEquals(0.001f, Ramp.volume(12_000L, 120_000L), 0.0005f)
    }

    @Test
    fun `po czasie narastania glosnosc nie rosnie dalej`() {
        assertEquals(1f, Ramp.volume(120_000L, 60_000L), 0.001f)
    }

    @Test
    fun `na starcie jest cicho`() {
        assertEquals(0f, Ramp.volume(0L, 60_000L), 0.001f)
    }

    @Test
    fun `suwak narastania konczy sie na dwoch minutach`() {
        assertEquals(120, Ramp.MAX_SECONDS)
        assertEquals(0, Ramp.MAX_SECONDS % Ramp.STEP_SECONDS)
    }

    @Test
    fun `opis czasu narastania`() {
        assertEquals("Bez narastania", TimeText.duration(0))
        assertEquals("45 s", TimeText.duration(45))
        assertEquals("1 min", TimeText.duration(60))
        assertEquals("1 min 15 s", TimeText.duration(75))
        assertEquals("2 min", TimeText.duration(120))
    }
}
