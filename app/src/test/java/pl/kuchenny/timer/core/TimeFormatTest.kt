package pl.kuchenny.timer.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Formatowanie czasu widoczne na ekranie i w powiadomieniach. */
class TimeFormatTest {

    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `czas ustawiony pokazuje minuty albo godziny`() {
        assertEquals("00:00", TimeFormat.remaining(0L))
        assertEquals("00:30", TimeFormat.remaining(30_000L))
        assertEquals("15:00", TimeFormat.remaining(TimeUnit.MINUTES.toMillis(15)))
        assertEquals("59:00", TimeFormat.remaining(TimeUnit.MINUTES.toMillis(59)))
        assertEquals("01:00:00", TimeFormat.remaining(TimeUnit.HOURS.toMillis(1)))
        assertEquals("01:05:00", TimeFormat.remaining(TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(5)))
        assertEquals("23:59:00", TimeFormat.remaining(TimeUnit.MINUTES.toMillis(1439)))
    }

    @Test
    fun `pozostaly czas skraca sie do godzin dopiero powyzej godziny`() {
        assertEquals("00:00", TimeFormat.remaining(0L))
        assertEquals("00:01", TimeFormat.remaining(1_000L))
        assertEquals("00:59", TimeFormat.remaining(59_999L))
        assertEquals("01:00", TimeFormat.remaining(TimeUnit.MINUTES.toMillis(1)))
        assertEquals("01:04:37", TimeFormat.remaining(TimeUnit.HOURS.toMillis(1) + 277_000L))
    }

    @Test
    fun `ujemny czas nie daje ujemnych cyfr`() {
        assertEquals("00:00", TimeFormat.remaining(-5_000L))
    }

    @Test
    fun `godzina zakonczenia liczy sie od teraz`() {
        // 1 stycznia 1970, 00:00 UTC
        assertEquals("00:15", TimeFormat.finishTime(0L, TimeUnit.MINUTES.toMillis(15), utc))
        assertEquals(
            "01:30",
            TimeFormat.finishTime(TimeUnit.HOURS.toMillis(1), TimeUnit.MINUTES.toMillis(30), utc)
        )
    }

    @Test
    fun `zegar pokazuje godzine w formacie dwucyfrowym`() {
        assertEquals("00:00", TimeFormat.clock(0L, utc))
        assertEquals(
            "13:07",
            TimeFormat.clock(TimeUnit.HOURS.toMillis(13) + TimeUnit.MINUTES.toMillis(7), utc)
        )
    }
}
