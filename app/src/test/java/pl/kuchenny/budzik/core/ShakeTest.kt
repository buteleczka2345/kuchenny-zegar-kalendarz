package pl.kuchenny.budzik.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Siła potrząśnięcia potrzebna do wyłączenia budzika. */
class ShakeTest {

    @Test
    fun `domyslny poziom to dotychczasowy prog`() {
        assertEquals(11f, Shake.threshold(Shake.DEFAULT_LEVEL), 0.001f)
    }

    @Test
    fun `wyzszy poziom wymaga mocniejszego potrzasniecia`() {
        val thresholds = (Shake.MIN_LEVEL..Shake.MAX_LEVEL).map { Shake.threshold(it) }
        assertEquals(thresholds.sorted(), thresholds)
        assertTrue(thresholds.first() < thresholds.last())
    }

    @Test
    fun `poziom poza zakresem jest przycinany`() {
        assertEquals(Shake.threshold(Shake.MIN_LEVEL), Shake.threshold(-5), 0.001f)
        assertEquals(Shake.threshold(Shake.MAX_LEVEL), Shake.threshold(99), 0.001f)
    }

    @Test
    fun `sprawdzenie pokazuje ten sam prog co alarm`() {
        for (level in Shake.MIN_LEVEL..Shake.MAX_LEVEL) {
            assertEquals(level, Shake.levelFor(Shake.threshold(level)))
            assertEquals(level - 1, Shake.levelFor(Shake.threshold(level) - 0.01f))
        }
        assertEquals(Shake.MAX_LEVEL, Shake.levelFor(80f))
    }

    @Test
    fun `wynik sprawdzania`() {
        assertTrue(Shake.testResult(0, 4).startsWith("Potrząśnij"))
        assertTrue(Shake.testResult(6, 4).endsWith("wyłączył ✓"))
        assertTrue(Shake.testResult(4, 4).endsWith("wyłączył ✓"))
        assertTrue(Shake.testResult(3, 4).contains("za słabo"))
    }

    @Test
    fun `filtr nie widzi ruchu w spoczynku i widzi szarpniecie`() {
        val filter = GravityFilter()
        repeat(50) { assertEquals(0f, filter.linear(0f, 0f, 9.81f), 0.001f) }
        assertTrue(filter.linear(15f, 0f, 9.81f) >= Shake.threshold(Shake.DEFAULT_LEVEL))
    }

    @Test
    fun `opis poziomu`() {
        assertEquals("1 / 10 · bardzo lekko", Shake.describe(1))
        assertEquals("5 / 10 · średnio", Shake.describe(5))
        assertEquals("10 / 10 · bardzo mocno", Shake.describe(10))
    }
}
