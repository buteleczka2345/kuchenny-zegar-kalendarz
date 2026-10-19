package pl.kuchenny.budzik.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Wyliczanie następnego dzwonienia alarmu. */
class NextTriggerTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `godzina jeszcze dzisiaj`() {
        val now = at(2024, 5, 10, 4, 30)
        assertEquals(at(2024, 5, 10, 5, 0), NextTrigger.next(now, 5, 0, utc))
    }

    @Test
    fun `godzina juz minela - wypada nastepnego dnia`() {
        val now = at(2024, 5, 10, 5, 1)
        assertEquals(at(2024, 5, 11, 5, 0), NextTrigger.next(now, 5, 0, utc))
    }

    @Test
    fun `dokladnie ta sama godzina przeskakuje o dobe`() {
        val now = at(2024, 5, 10, 5, 0)
        assertEquals(at(2024, 5, 11, 5, 0), NextTrigger.next(now, 5, 0, utc))
    }

    @Test
    fun `przejscie przez koniec miesiaca`() {
        val now = at(2024, 1, 31, 23, 30)
        assertEquals(at(2024, 2, 1, 6, 15), NextTrigger.next(now, 6, 15, utc))
    }

    // 10 maja 2024 to piątek.

    @Test
    fun `dni robocze - w piatek wieczorem wypada poniedzialek`() {
        val now = at(2024, 5, 10, 20, 0)
        assertEquals(at(2024, 5, 13, 6, 0), NextTrigger.next(now, 6, 0, Days.WORKDAYS, utc))
    }

    @Test
    fun `dni robocze - w piatek rano jeszcze dzisiaj`() {
        val now = at(2024, 5, 10, 5, 0)
        assertEquals(at(2024, 5, 10, 6, 0), NextTrigger.next(now, 6, 0, Days.WORKDAYS, utc))
    }

    @Test
    fun `tylko niedziela`() {
        val now = at(2024, 5, 10, 5, 0)
        val sunday = 1 shl 6
        assertEquals(at(2024, 5, 12, 9, 30), NextTrigger.next(now, 9, 30, sunday, utc))
    }

    @Test
    fun `ten sam dzien tygodnia po godzinie - za tydzien`() {
        val now = at(2024, 5, 10, 7, 0)
        val friday = 1 shl 4
        assertEquals(at(2024, 5, 17, 6, 0), NextTrigger.next(now, 6, 0, friday, utc))
    }

    @Test
    fun `alarm jednorazowy - najblizsza taka godzina`() {
        val now = at(2024, 5, 10, 7, 0)
        assertEquals(at(2024, 5, 11, 6, 0), NextTrigger.next(now, 6, 0, Days.NONE, utc))
    }

    @Test
    fun `niepoprawna godzina jest przycinana`() {
        val now = at(2024, 5, 10, 4, 30)
        assertEquals(at(2024, 5, 10, 23, 59), NextTrigger.next(now, 99, 99, utc))
    }
}
