package pl.kuchenny.timer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Testy stanu timerów — bez Androida, na podstawionym zegarze i atrapie magazynu.
 */
class TimerControllerTest {

    private var now = 1_000_000L
    private val store = FakeStore(TimersSnapshot.default())
    private val alarms = FakeAlarms()
    private val notifier = FakeNotifier()
    private val controller = TimerController(store, alarms, notifier, clock = { now })

    private fun minutes(value: Int): Long = TimeUnit.MINUTES.toMillis(value.toLong())

    // --- podstawowe przejścia ----------------------------------------------

    @Test
    fun `start ustawia koniec, planuje alarm i pokazuje powiadomienie`() {
        controller.start()

        val state = controller.active().state
        assertTrue(state is TimerState.Running)
        assertEquals(now + minutes(15), (state as TimerState.Running).endTime)
        assertEquals(now + minutes(15), alarms.scheduled[1L])
        assertEquals(now + minutes(15), notifier.running[1L])
    }

    @Test
    fun `pauza zapamietuje pozostaly czas i odwoluje alarm`() {
        controller.start()
        now += minutes(3)

        controller.pause()

        val state = controller.active().state
        assertTrue(state is TimerState.Paused)
        assertEquals(minutes(12), (state as TimerState.Paused).remainingMillis)
        assertNull(alarms.scheduled[1L])
        assertEquals(minutes(12), notifier.paused[1L])
    }

    @Test
    fun `wznowienie liczy koniec od nowa od pozostalego czasu`() {
        controller.start()
        now += minutes(5)
        controller.pause()
        now += minutes(10)

        controller.resume()

        val state = controller.active().state as TimerState.Running
        assertEquals(now + minutes(10), state.endTime)
        assertEquals(minutes(15), state.totalMillis)
    }

    @Test
    fun `stop zeruje stan i sprzata alarm oraz powiadomienie`() {
        controller.start()

        controller.stop()

        assertTrue(controller.active().state is TimerState.Idle)
        assertNull(alarms.scheduled[1L])
        assertNull(notifier.running[1L])
    }

    @Test
    fun `zerowanie zatrzymuje timer i ustawia zero`() {
        controller.start()

        controller.reset()

        assertTrue(controller.active().state is TimerState.Idle)
        assertEquals(0L, controller.active().presetMillis)
        assertNull(alarms.scheduled[1L])
    }

    // --- wiele timerów naraz ------------------------------------------------

    @Test
    fun `dwa timery odliczaja niezaleznie`() {
        controller.start()

        controller.selectTimer(2L)
        controller.setPreset(minutes(5))
        controller.start()

        assertEquals(now + minutes(15), alarms.scheduled[1L])
        assertEquals(now + minutes(5), alarms.scheduled[2L])

        // Zatrzymanie drugiego nie rusza pierwszego.
        controller.stop()
        assertTrue(controller.active().state is TimerState.Idle)
        assertEquals(now + minutes(15), alarms.scheduled[1L])
        assertEquals(minutes(15), controller.snapshot().find(1L)?.remainingMillis(now))
    }

    @Test
    fun `dodany timer od razu staje sie aktywny`() {
        val id = controller.addTimer(minutes(7))

        assertNotNull(id)
        assertEquals(id, controller.snapshot().activeId)
        assertEquals(3, controller.snapshot().timers.size)
        assertEquals(minutes(7), controller.active().presetMillis)
    }

    @Test
    fun `usuniecie timera odwoluje jego alarm i powiadomienie`() {
        controller.start()
        controller.selectTimer(2L)

        assertTrue(controller.removeTimer(1L))

        assertNull(controller.snapshot().find(1L))
        assertNull(alarms.scheduled[1L])
        assertNull(notifier.running[1L])
        assertEquals(2L, controller.snapshot().activeId)
    }

    @Test
    fun `usuniecie aktywnego timera wskazuje nastepny`() {
        controller.selectTimer(2L)

        assertTrue(controller.removeTimer(2L))

        assertEquals(1L, controller.snapshot().activeId)
        assertEquals(1, controller.snapshot().timers.size)
    }

    @Test
    fun `ostatniego timera nie da sie usunac`() {
        controller.removeTimer(2L)

        assertFalse(controller.removeTimer(1L))
        assertEquals(1, controller.snapshot().timers.size)
    }

    @Test
    fun `liczba timerow jest ograniczona`() {
        while (controller.snapshot().timers.size < TimerLimits.MAX_TIMERS) {
            assertNotNull(controller.addTimer())
        }
        assertNull(controller.addTimer())
    }

    @Test
    fun `zmiana nazwy jest przycinana i nie przyjmuje pustej`() {
        controller.rename(1L, "  Pierogi  ")
        assertEquals("Pierogi", controller.snapshot().find(1L)?.label)

        controller.rename(1L, "   ")
        assertEquals("Pierogi", controller.snapshot().find(1L)?.label)

        controller.rename(1L, "x".repeat(100))
        assertEquals(24, controller.snapshot().find(1L)?.label?.length)
    }

    // --- ustawianie i dodawanie czasu --------------------------------------

    @Test
    fun `zmiana czasu w trakcie odliczania zaczyna nowe odliczanie`() {
        controller.start()
        now += minutes(2)

        assertTrue(controller.select(30))

        val state = controller.active().state as TimerState.Running
        assertEquals(now + minutes(30), state.endTime)
        assertEquals(30, (controller.active().presetMillis / 60_000L).toInt())
    }

    @Test
    fun `zejscie do zera zatrzymuje odliczanie`() {
        controller.start()

        assertTrue(controller.select(0))

        assertTrue(controller.active().state is TimerState.Idle)
        assertEquals(0L, controller.active().presetMillis)
        assertNull(alarms.scheduled[1L])
    }

    @Test
    fun `dodanie pol minuty do pustego timera daje trzydziesci sekund`() {
        controller.selectTimer(2L)
        assertEquals(0L, controller.active().presetMillis)

        assertTrue(controller.addTime(30_000L))

        assertEquals(30_000L, controller.active().presetMillis)
        assertEquals(30_000L, controller.active().remainingMillis(now))
    }

    @Test
    fun `dodanie czasu w trakcie odliczania wydluza koniec`() {
        controller.start()

        assertTrue(controller.addTime(minutes(1)))

        val state = controller.active().state as TimerState.Running
        assertEquals(now + minutes(16), state.endTime)
        assertEquals(minutes(16), state.totalMillis)
        assertEquals(now + minutes(16), alarms.scheduled[1L])
    }

    @Test
    fun `dodanie czasu w pauzie wydluza pozostalosc`() {
        controller.start()
        now += minutes(5)
        controller.pause()

        assertTrue(controller.addTime(minutes(2)))

        val state = controller.active().state as TimerState.Paused
        assertEquals(minutes(12), state.remainingMillis)
        assertEquals(minutes(17), state.totalMillis)
    }

    @Test
    fun `czas jest ograniczany do dwudziestu trzech godzin piecdziesieciu dziewieciu minut`() {
        controller.select(99_999)
        assertEquals(TimerLimits.MAX_PRESET_MILLIS, controller.active().presetMillis)

        controller.addTime(TimerLimits.MAX_PRESET_MILLIS)
        assertEquals(TimerLimits.MAX_PRESET_MILLIS, controller.active().presetMillis)
    }

    @Test
    fun `nie da sie uruchomic timera bez ustawionego czasu`() {
        controller.selectTimer(2L)

        assertFalse(controller.start())
        assertTrue(controller.active().state is TimerState.Idle)
    }

    // --- dzwonienie ---------------------------------------------------------

    @Test
    fun `alarm przechodzi w dzwonienie tylko gdy timer dziala`() {
        assertFalse(controller.onTimeFired(1L))

        controller.start()
        assertTrue(controller.onTimeFired(1L))
        assertTrue(controller.active().state is TimerState.Ringing)

        // Powtorne zadzialanie tego samego alarmu nie moze nic zepsuc.
        assertFalse(controller.onTimeFired(1L))
    }

    @Test
    fun `zakonczenie dzwonienia zeruje timer`() {
        controller.start()
        controller.onTimeFired(1L)

        controller.onRingFinished(1L)

        assertTrue(controller.active().state is TimerState.Idle)
        assertEquals(0L, controller.active().presetMillis)
    }

    @Test
    fun `zatrzymanie dzwonka zeruje timer`() {
        controller.start()
        controller.onTimeFired(1L)

        controller.stop()

        assertTrue(controller.active().state is TimerState.Idle)
        assertEquals(0L, controller.active().presetMillis)
    }

    @Test
    fun `kolejka dzwonienia zwraca ten drugi timer`() {
        controller.start()
        controller.selectTimer(2L)
        controller.setPreset(minutes(5))
        controller.start()

        now += minutes(5)
        assertTrue(controller.onTimeFired(2L))
        now += minutes(10)
        assertTrue(controller.onTimeFired(1L))

        assertEquals(1L, controller.nextRinging(excludeId = 2L)?.id)

        controller.onRingFinished(2L)
        controller.onRingFinished(1L)
        assertNull(controller.nextRinging())
    }

    // --- uzgadnianie stanu --------------------------------------------------

    @Test
    fun `dzialajace timery sa uzbrajane ponownie po powrocie do aplikacji`() {
        controller.start()

        val result = controller.reconcile()

        assertEquals(ReconcileResult.UNCHANGED, result)
        assertEquals(now + minutes(15), alarms.scheduled[1L])
    }

    @Test
    fun `zgubiony alarm konczy sie komunikatem zamiast martwego stanu`() {
        controller.start()
        now += minutes(30)

        val result = controller.reconcile()

        assertEquals(ReconcileResult.TIMER_MISSED, result)
        assertTrue(controller.active().state is TimerState.Idle)
        assertNull(alarms.scheduled[1L])
    }

    @Test
    fun `swiezo miniony czas jeszcze czeka na odbiornik alarmu`() {
        controller.start()
        now += minutes(15) + 1_000L

        assertEquals(ReconcileResult.UNCHANGED, controller.reconcile())
        assertTrue(controller.active().state is TimerState.Running)
    }

    @Test
    fun `zawieszona flaga dzwonienia jest czyszczona`() {
        controller.start()
        controller.onTimeFired(1L)
        now += TimeUnit.MINUTES.toMillis(30)

        assertEquals(ReconcileResult.RINGING_STALE, controller.reconcile())
        assertTrue(controller.active().state is TimerState.Idle)
    }

    @Test
    fun `trwajace dzwonienie nie jest czyszczone`() {
        controller.start()
        controller.onTimeFired(1L)
        now += 5_000L

        assertEquals(ReconcileResult.UNCHANGED, controller.reconcile())
        assertTrue(controller.active().state is TimerState.Ringing)
    }

    // --- atrapy -------------------------------------------------------------

    private class FakeStore(var snapshot: TimersSnapshot) : TimersStore {
        override fun load(): TimersSnapshot = snapshot
        override fun save(snapshot: TimersSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeAlarms : AlarmScheduler {
        val scheduled = mutableMapOf<Long, Long>()

        override fun scheduleTimer(timerId: Long, endTime: Long) {
            scheduled[timerId] = endTime
        }

        override fun cancelTimer(timerId: Long) {
            scheduled.remove(timerId)
        }
    }

    private class FakeNotifier : TimerNotifier {
        val running = mutableMapOf<Long, Long>()
        val paused = mutableMapOf<Long, Long>()

        override fun showRunning(timerId: Long, label: String, endTime: Long) {
            paused.remove(timerId)
            running[timerId] = endTime
        }

        override fun showPaused(timerId: Long, label: String, remainingMillis: Long) {
            running.remove(timerId)
            paused[timerId] = remainingMillis
        }

        override fun cancel(timerId: Long) {
            running.remove(timerId)
            paused.remove(timerId)
        }
    }
}
