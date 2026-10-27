package pl.kuchenny.budzik.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Pykanie o pełnej godzinie. */
class HourlyChimeTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `zakres w ciagu dnia obejmuje obie granice`() {
        assertTrue(HourlyChime.isActive(8, 8, 22))
        assertTrue(HourlyChime.isActive(22, 8, 22))
        assertFalse(HourlyChime.isActive(7, 8, 22))
        assertFalse(HourlyChime.isActive(23, 8, 22))
    }

    @Test
    fun `zakres przez polnoc`() {
        assertTrue(HourlyChime.isActive(23, 22, 6))
        assertTrue(HourlyChime.isActive(0, 22, 6))
        assertTrue(HourlyChime.isActive(6, 22, 6))
        assertFalse(HourlyChime.isActive(12, 22, 6))
    }

    @Test
    fun `cala doba`() {
        (0..23).forEach { assertTrue(HourlyChime.isActive(it, 0, 23)) }
    }

    @Test
    fun `nastepna pelna godzina`() {
        val now = at(2024, 5, 10, 12, 34)
        assertEquals(at(2024, 5, 10, 13, 0), HourlyChime.next(now, 0, 23, utc))
    }

    @Test
    fun `dokladnie o pelnej godzinie liczy sie nastepna`() {
        val now = at(2024, 5, 10, 12, 0)
        assertEquals(at(2024, 5, 10, 13, 0), HourlyChime.next(now, 0, 23, utc))
    }

    @Test
    fun `po koncu zakresu czeka do rana`() {
        val now = at(2024, 5, 10, 22, 5)
        assertEquals(at(2024, 5, 11, 8, 0), HourlyChime.next(now, 8, 22, utc))
    }
}
