package pl.kuchenny.budzik.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Reguły listy alarmów — bez Androida, na podstawionym zegarze. */
class AlarmControllerTest {

    private val utc = TimeZone.getTimeZone("UTC")

    @Before
    fun useUtc() {
        // NextTrigger bez strefy bierze domyślną strefę JVM.
        TimeZone.setDefault(utc)
    }
    private var now = Calendar.getInstance(utc).apply {
        clear()
        set(2024, 4, 10, 4, 0, 0)
    }.timeInMillis

    private val store = FakeStore(AlarmsSnapshot.EMPTY)
    private val scheduler = FakeScheduler()
    private val controller = AlarmController(store, scheduler, clock = { now })

    private fun at(hour: Int, minute: Int, day: Int = 10): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2024, 4, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `dodany alarm jest wlaczony i zaplanowany`() {
        val id = controller.add(5, 0)

        val alarm = controller.snapshot().find(id!!)
        assertTrue(alarm!!.enabled)
        assertEquals(at(5, 0), scheduler.scheduled[id])
    }

    @Test
    fun `wylaczenie alarmu odwoluje plan`() {
        val id = controller.add(5, 0)!!

        controller.setEnabled(id, false)

        assertNull(scheduler.scheduled[id])
        assertTrue(!controller.snapshot().find(id)!!.enabled)
    }

    @Test
    fun `wlaczenie alarmu planuje go ponownie`() {
        val id = controller.add(5, 0)!!
        controller.setEnabled(id, false)

        controller.setEnabled(id, true)

        assertEquals(at(5, 0), scheduler.scheduled[id])
    }

    @Test
    fun `zmiana godziny przestawia alarm`() {
        val id = controller.add(5, 0)!!

        controller.setTime(id, 6, 30)

        assertEquals(at(6, 30), scheduler.scheduled[id])
    }

    @Test
    fun `usuniecie alarmu odwoluje plan`() {
        val id = controller.add(5, 0)!!

        controller.remove(id)

        assertNull(controller.snapshot().find(id))
        assertNull(scheduler.scheduled[id])
    }

    @Test
    fun `lista jest uporzadkowana po godzinie`() {
        controller.add(18, 0)
        controller.add(5, 30)

        assertEquals(listOf(5 * 60 + 30, 18 * 60), controller.snapshot().alarms.map { it.minutesOfDay })
    }

    @Test
    fun `nastepny alarm to najblizszy wlaczony`() {
        controller.add(5, 0)
        controller.add(6, 0)

        val next = controller.nextAlarm()

        assertEquals(5 * 60, next?.first?.minutesOfDay)
        assertEquals(at(5, 0), next?.second)
    }

    @Test
    fun `wylaczone alarmy nie licza sie do nastepnego`() {
        val id = controller.add(5, 0)!!
        controller.setEnabled(id, false)
        controller.add(6, 0)

        assertEquals(6 * 60, controller.nextAlarm()?.first?.minutesOfDay)
    }

    @Test
    fun `po zadzwonieniu planowany jest kolejny dzien`() {
        val id = controller.add(5, 0)!!
        now = at(5, 0)

        val fired = controller.onFired(id)

        assertEquals(at(5, 0, day = 11), scheduler.scheduled[id])
        assertEquals(5, fired?.hour)
    }

    @Test
    fun `alarm na dni robocze w piatek po godzinie planowany na poniedzialek`() {
        // 10 maja 2024 to piątek.
        now = at(7, 0)

        val id = controller.add(6, 0, days = Days.WORKDAYS)!!

        assertEquals(at(6, 0, day = 13), scheduler.scheduled[id])
    }

    @Test
    fun `alarm jednorazowy po zadzwonieniu sam sie wylacza`() {
        val id = controller.add(5, 0, days = Days.NONE)!!
        now = at(5, 0)

        val fired = controller.onFired(id)

        assertTrue(fired!!.enabled)
        assertTrue(!controller.snapshot().find(id)!!.enabled)
        assertNull(scheduler.scheduled[id])
    }

    @Test
    fun `edycja zmienia dni i wlacza alarm`() {
        val id = controller.add(5, 0)!!
        controller.setEnabled(id, false)

        controller.update(id, 6, 0, Days.WEEKEND)

        val alarm = controller.snapshot().find(id)!!
        assertEquals(Days.WEEKEND, alarm.days)
        assertTrue(alarm.enabled)
        assertEquals(at(6, 0, day = 11), scheduler.scheduled[id])
    }

    @Test
    fun `liczba alarmow jest ograniczona`() {
        repeat(AlarmsSnapshot.MAX_ALARMS) { controller.add(5, 0) }

        assertNull(controller.add(5, 0))
    }

    @Test
    fun `ponowne ustawienie planuje wszystkie wlaczone`() {
        controller.add(5, 0)
        controller.add(6, 0)

        scheduler.scheduled.clear()
        controller.rescheduleAll()

        assertEquals(2, scheduler.scheduled.size)
    }

    private class FakeStore(var snapshot: AlarmsSnapshot) : AlarmStore {
        override fun load(): AlarmsSnapshot = snapshot
        override fun save(snapshot: AlarmsSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeScheduler : AlarmScheduler {
        val scheduled = mutableMapOf<Long, Long>()

        override fun schedule(alarmId: Long, triggerAt: Long) {
            scheduled[alarmId] = triggerAt
        }

        override fun cancel(alarmId: Long) {
            scheduled.remove(alarmId)
        }
    }
}
