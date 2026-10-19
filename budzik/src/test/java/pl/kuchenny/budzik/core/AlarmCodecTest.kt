package pl.kuchenny.budzik.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zapis listy alarmów — to on decyduje, co wróci po restarcie telefonu. */
class AlarmCodecTest {

    @Test
    fun `lista przechodzi przez zapis i odczyt`() {
        val snapshot = AlarmsSnapshot(
            listOf(
                AlarmItem(1L, 5, 0, true),
                AlarmItem(2L, 6, 30, false),
                AlarmItem(3L, 23, 59, true, Days.WORKDAYS),
                AlarmItem(4L, 8, 0, true, Days.NONE)
            )
        )

        val decoded = AlarmCodec.decode(AlarmCodec.encode(snapshot))

        assertEquals(snapshot.normalized(), decoded)
    }

    @Test
    fun `pusty albo uszkodzony zapis daje null`() {
        assertNull(AlarmCodec.decode(null))
        assertNull(AlarmCodec.decode(""))
        assertNull(AlarmCodec.decode("to nie jest json"))
        assertNull(AlarmCodec.decode("{}"))
    }

    @Test
    fun `lista jest sortowana po godzinie`() {
        val decoded = AlarmCodec.decode(
            """{"alarms":[
                {"id":1,"hour":18,"minute":0,"enabled":true},
                {"id":2,"hour":5,"minute":30,"enabled":true}
            ]}"""
        )

        assertEquals(listOf(2L, 1L), decoded?.alarms?.map { it.id })
    }

    @Test
    fun `godzina poza zakresem jest przycinana`() {
        val decoded = AlarmCodec.decode(
            """{"alarms":[{"id":1,"hour":99,"minute":-5,"enabled":true}]}"""
        )

        val alarm = decoded?.alarms?.first()
        assertEquals(23, alarm?.hour)
        assertEquals(0, alarm?.minute)
    }

    @Test
    fun `stary zapis bez dni dzwoni codziennie`() {
        val decoded = AlarmCodec.decode(
            """{"alarms":[{"id":1,"hour":5,"minute":0,"enabled":true}]}"""
        )

        assertEquals(Days.ALL, decoded?.alarms?.first()?.days)
    }

    @Test
    fun `opis dni alarmu`() {
        assertEquals("codziennie", TimeText.days(Days.ALL))
        assertEquals("pn–pt", TimeText.days(Days.WORKDAYS))
        assertEquals("weekend", TimeText.days(Days.WEEKEND))
        assertEquals("jednorazowo", TimeText.days(Days.NONE))
        assertEquals("pn, śr, pt", TimeText.days(0b0010101))
    }

    @Test
    fun `lista wlasnych dzwiekow przechodzi przez zapis i odczyt`() {
        val sounds = listOf(
            CustomSound("content://a/1", "Pobudka"),
            CustomSound("content://a/2", "Ptaki")
        )

        assertEquals(sounds, CustomSoundCodec.decode(CustomSoundCodec.encode(sounds)))
        assertTrue(CustomSoundCodec.decode("zepsute").isEmpty())
        assertTrue(CustomSoundCodec.decode(null).isEmpty())
    }

    @Test
    fun `alarm bez identyfikatora jest pomijany`() {
        val decoded = AlarmCodec.decode(
            """{"alarms":[{"hour":5,"minute":0,"enabled":true}]}"""
        )

        assertTrue(decoded?.alarms?.isEmpty() == true)
    }
}
