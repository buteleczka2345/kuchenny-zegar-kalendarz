package pl.kuchenny.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zapis listy timerów do JSON — to on decyduje, co wróci po restarcie telefonu. */
class TimerListCodecTest {

    @Test
    fun `wszystkie stany przechodza przez zapis i odczyt`() {
        val snapshot = TimersSnapshot(
            activeId = 2L,
            timers = listOf(
                TimerItem(1L, "Pierogi", 15 * 60_000L, TimerState.Idle),
                TimerItem(2L, "Zupa", 5 * 60_000L, TimerState.Running(1_700_000L, 5 * 60_000L)),
                TimerItem(3L, "Ciasto", 60 * 60_000L, TimerState.Paused(120_000L, 60 * 60_000L)),
                TimerItem(4L, "Herbata", 0L, TimerState.Ringing(1_700_500L))
            )
        )

        val decoded = TimerListCodec.decode(TimerListCodec.encode(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `nazwy ze znakami specjalnymi przechodza bez zmian`() {
        val snapshot = TimersSnapshot(
            activeId = 1L,
            timers = listOf(
                TimerItem(1L, "Ziemniaki \"z koperkiem\" | 2", 0L, TimerState.Idle)
            )
        )

        val decoded = TimerListCodec.decode(TimerListCodec.encode(snapshot))

        assertEquals(snapshot.timers.first().label, decoded?.timers?.first()?.label)
    }

    @Test
    fun `pusty albo uszkodzony zapis daje null`() {
        assertNull(TimerListCodec.decode(null))
        assertNull(TimerListCodec.decode(""))
        assertNull(TimerListCodec.decode("to nie jest json"))
        assertNull(TimerListCodec.decode("{\"timers\":[]}"))
    }

    @Test
    fun `nieznany stan jest czytany jako bezczynnosc`() {
        val raw = """{"activeId":1,"timers":[{"id":1,"label":"Timer 1","preset":60000,"state":"cos"}]}"""

        val decoded = TimerListCodec.decode(raw)

        assertTrue(decoded?.timers?.first()?.state is TimerState.Idle)
    }

    @Test
    fun `wskazanie nieistniejacego timera wraca do pierwszego`() {
        val raw = """{"activeId":99,"timers":[
            {"id":1,"label":"Timer 1","preset":60000,"state":"idle"},
            {"id":2,"label":"Timer 2","preset":0,"state":"idle"}
        ]}"""

        val decoded = TimerListCodec.decode(raw)

        assertEquals(1L, decoded?.activeId)
    }

    @Test
    fun `czas poza zakresem jest przycinany przy odczycie`() {
        val raw = """{"activeId":1,"timers":[{"id":1,"label":"Timer 1","preset":999999999,"state":"idle"}]}"""

        val decoded = TimerListCodec.decode(raw)

        assertEquals(TimerLimits.MAX_PRESET_MILLIS, decoded?.timers?.first()?.presetMillis)
    }
}
