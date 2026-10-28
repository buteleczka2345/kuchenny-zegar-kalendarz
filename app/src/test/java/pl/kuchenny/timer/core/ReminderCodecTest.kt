package pl.kuchenny.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zapis przypomnień — to on decyduje, co wróci po restarcie telefonu. */
class ReminderCodecTest {

    @Test
    fun `lista przechodzi przez zapis i odczyt posortowana po dacie`() {
        val reminders = listOf(Reminder(2L, 2_000L), Reminder(1L, 1_000L))

        assertEquals(reminders.sortedBy { it.at }, ReminderCodec.decode(ReminderCodec.encode(reminders)))
    }

    @Test
    fun `pusty albo uszkodzony zapis daje pusta liste`() {
        assertTrue(ReminderCodec.decode(null).isEmpty())
        assertTrue(ReminderCodec.decode("").isEmpty())
        assertTrue(ReminderCodec.decode("{zepsute").isEmpty())
    }

    @Test
    fun `wpisy bez id albo daty sa pomijane`() {
        val raw = """[{"id":0,"at":5},{"id":3},{"id":4,"at":7}]"""

        assertEquals(listOf(Reminder(4L, 7L)), ReminderCodec.decode(raw))
    }
}
