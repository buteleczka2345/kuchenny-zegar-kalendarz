package pl.kuchenny.budzik.core

/**
 * Jedyny właściciel listy alarmów: dodawanie, włączanie i planowanie dzwonienia.
 *
 * Klasa nie zna Androida — dostaje [AlarmStore], [AlarmScheduler] i zegar,
 * więc każdą regułę można sprawdzić testem jednostkowym.
 */
class AlarmController(
    private val store: AlarmStore,
    private val scheduler: AlarmScheduler,
    private val clock: () -> Long = System::currentTimeMillis
) {

    fun snapshot(): AlarmsSnapshot = store.load().normalized()

    fun add(hour: Int, minute: Int, enabled: Boolean = true, days: Int = Days.ALL): Long? {
        val snapshot = snapshot()
        if (snapshot.alarms.size >= AlarmsSnapshot.MAX_ALARMS) return null
        val id = (snapshot.alarms.maxOfOrNull { it.id } ?: 0L) + 1L
        val alarm = AlarmItem(id, hour, minute, enabled, days)
        commit(snapshot.copy(alarms = snapshot.alarms + alarm))
        return id
    }

    fun remove(id: Long) {
        val snapshot = snapshot()
        if (snapshot.find(id) == null) return
        scheduler.cancel(id)
        commit(snapshot.without(id))
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        val snapshot = snapshot()
        val alarm = snapshot.find(id) ?: return
        if (alarm.enabled == enabled) return
        commit(snapshot.replace(alarm.copy(enabled = enabled)))
    }

    fun setTime(id: Long, hour: Int, minute: Int) {
        val alarm = snapshot().find(id) ?: return
        update(id, hour, minute, alarm.days)
    }

    /**
     * Nowa godzina i dni alarmu. Zapisany alarm zostaje włączony —
     * po edycji użytkownik chce, żeby zadzwonił.
     */
    fun update(id: Long, hour: Int, minute: Int, days: Int) {
        val snapshot = snapshot()
        val alarm = snapshot.find(id) ?: return
        val changed = alarm.copy(hour = hour, minute = minute, days = days, enabled = true)
        if (changed == alarm) return
        commit(snapshot.replace(changed))
    }

    /** Ustawia wszystkie alarmy ponownie, np. po restarcie telefonu. */
    fun rescheduleAll() {
        snapshot().alarms.forEach { schedule(it) }
    }

    /**
     * Alarm właśnie zadzwonił: planujemy od razu następne dzwonienie,
     * żeby restart telefonu w trakcie dzwonienia nie zgubił alarmu.
     * Alarm jednorazowy sam się wyłącza. Zwraca alarm sprzed wyłączenia,
     * żeby zadzwonił ten jeden raz.
     */
    fun onFired(id: Long): AlarmItem? {
        val snapshot = snapshot()
        val alarm = snapshot.find(id) ?: return null
        if (alarm.oneShot && alarm.enabled) {
            commit(snapshot.replace(alarm.copy(enabled = false)))
        } else {
            schedule(alarm)
        }
        return alarm
    }

    /** Najbliższy włączony alarm i jego czas — do podpisu na ekranie. */
    fun nextAlarm(): Pair<AlarmItem, Long>? {
        val now = clock()
        return snapshot().enabled()
            .map { it to NextTrigger.next(now, it.hour, it.minute, it.days) }
            .minByOrNull { it.second }
    }

    fun timeUntilNextAlarm(): Long? = nextAlarm()?.second?.minus(clock())

    private fun commit(snapshot: AlarmsSnapshot) {
        val safe = snapshot.normalized()
        store.save(safe)
        safe.alarms.forEach { schedule(it) }
    }

    private fun schedule(alarm: AlarmItem) {
        if (!alarm.enabled) {
            scheduler.cancel(alarm.id)
            return
        }
        scheduler.schedule(alarm.id, NextTrigger.next(clock(), alarm.hour, alarm.minute, alarm.days))
    }
}
