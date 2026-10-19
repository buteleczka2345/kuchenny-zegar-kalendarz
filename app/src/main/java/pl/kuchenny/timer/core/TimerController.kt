package pl.kuchenny.timer.core

/** Wynik uzgadniania stanu po powrocie do aplikacji. */
enum class ReconcileResult {
    UNCHANGED,
    /** Timer miał się skończyć, ale alarm nigdy nie zadzwonił. */
    TIMER_MISSED,
    /** Dzwonek skończył się, gdy proces aplikacji był martwy. */
    RINGING_STALE
}

/**
 * Jedyny właściciel stanu timerów: przejścia, alarmy systemowe i powiadomienia.
 *
 * Klasa nie zna Androida — dostaje [TimersStore], [AlarmScheduler] i [TimerNotifier],
 * więc każdą regułę można sprawdzić testem jednostkowym.
 */
class TimerController(
    private val store: TimersStore,
    private val alarms: AlarmScheduler,
    private val notifier: TimerNotifier,
    private val clock: () -> Long = System::currentTimeMillis
) {

    fun snapshot(): TimersSnapshot = store.load().normalized()

    fun active(): TimerItem = snapshot().active

    // --- lista timerów ------------------------------------------------------

    fun selectTimer(id: Long) {
        val snapshot = snapshot()
        if (snapshot.activeId == id || snapshot.find(id) == null) return
        store.save(snapshot.copy(activeId = id))
    }

    /** Dodaje timer i od razu go pokazuje. `null`, gdy osiągnięto limit. */
    fun addTimer(presetMillis: Long = TimersSnapshot.DEFAULT_PRESET_MILLIS): Long? {
        val snapshot = snapshot()
        if (snapshot.timers.size >= TimerLimits.MAX_TIMERS) return null
        val id = (snapshot.timers.maxOfOrNull { it.id } ?: 0L) + 1L
        val item = TimerItem(
            id = id,
            label = nextLabel(snapshot),
            presetMillis = presetMillis.coerceIn(0L, TimerLimits.MAX_PRESET_MILLIS),
            state = TimerState.Idle
        )
        store.save(snapshot.copy(activeId = id, timers = snapshot.timers + item))
        return id
    }

    fun removeTimer(id: Long): Boolean {
        val snapshot = snapshot()
        if (snapshot.timers.size <= 1) return false
        val remaining = snapshot.timers.filterNot { it.id == id }
        if (remaining.size == snapshot.timers.size) return false
        alarms.cancelTimer(id)
        notifier.cancel(id)
        val activeId = if (snapshot.activeId == id) remaining.first().id else snapshot.activeId
        commit(snapshot.copy(activeId = activeId, timers = remaining))
        return true
    }

    fun rename(id: Long, label: String) {
        val snapshot = snapshot()
        val item = snapshot.find(id) ?: return
        val safe = label.trim().take(MAX_LABEL_LENGTH).ifBlank { item.label }
        if (safe == item.label) return
        commit(snapshot.replace(item.copy(label = safe)))
    }

    // --- czas aktywnego timera ---------------------------------------------

    fun select(minutes: Int): Boolean = setPreset(minutesToMillis(minutes))

    /**
     * Ustawia czas z tarczy. W trakcie odliczania zaczyna nowe odliczanie od tej
     * wartości; zejście do zera zatrzymuje timer.
     */
    fun setPreset(millis: Long): Boolean {
        val snapshot = snapshot()
        val item = snapshot.active
        if (item.state is TimerState.Ringing) return false
        val preset = millis.coerceIn(0L, TimerLimits.MAX_PRESET_MILLIS)
        val next = when (val state = item.state) {
            is TimerState.Running -> if (preset < TimerLimits.MIN_RUNNABLE_MILLIS) {
                item.copy(presetMillis = 0L, state = TimerState.Idle)
            } else {
                item.copy(
                    presetMillis = preset,
                    state = state.copy(endTime = clock() + preset, totalMillis = preset)
                )
            }
            is TimerState.Paused -> if (preset < TimerLimits.MIN_RUNNABLE_MILLIS) {
                item.copy(presetMillis = 0L, state = TimerState.Idle)
            } else {
                item.copy(
                    presetMillis = preset,
                    state = state.copy(remainingMillis = preset, totalMillis = preset)
                )
            }
            TimerState.Idle -> item.copy(presetMillis = preset)
            is TimerState.Ringing -> return false
        }
        commit(snapshot.replace(next))
        return true
    }

    /**
     * Dodaje (albo odejmuje) czas aktywnemu timerowi — przyciski +30 s / +1 min / +5 min.
     * Działa też w trakcie odliczania, bo wydłuża wtedy sam koniec.
     */
    fun addTime(deltaMillis: Long): Boolean {
        if (deltaMillis == 0L) return false
        val snapshot = snapshot()
        val item = snapshot.active
        val next = when (val state = item.state) {
            TimerState.Idle -> item.copy(
                presetMillis = clampPreset(item.presetMillis + deltaMillis)
            )
            is TimerState.Running -> item.copy(
                presetMillis = clampPreset(item.presetMillis + deltaMillis),
                state = state.copy(
                    endTime = (state.endTime + deltaMillis)
                        .coerceAtLeast(clock() + TimerLimits.MIN_RUNNABLE_MILLIS),
                    totalMillis = (state.totalMillis + deltaMillis)
                        .coerceAtLeast(TimerLimits.MIN_RUNNABLE_MILLIS)
                )
            )
            is TimerState.Paused -> item.copy(
                presetMillis = clampPreset(item.presetMillis + deltaMillis),
                state = state.copy(
                    remainingMillis = (state.remainingMillis + deltaMillis).coerceAtLeast(0L),
                    totalMillis = (state.totalMillis + deltaMillis)
                        .coerceAtLeast(TimerLimits.MIN_RUNNABLE_MILLIS)
                )
            )
            is TimerState.Ringing -> return false
        }
        commit(snapshot.replace(next))
        return true
    }

    fun start(): Boolean {
        val snapshot = snapshot()
        val item = snapshot.active
        if (item.state !is TimerState.Idle) return false
        if (item.presetMillis < TimerLimits.MIN_RUNNABLE_MILLIS) return false
        commit(
            snapshot.replace(
                item.copy(state = TimerState.Running(clock() + item.presetMillis, item.presetMillis))
            )
        )
        return true
    }

    fun pause() {
        val snapshot = snapshot()
        val item = snapshot.active
        val state = item.state as? TimerState.Running ?: return
        val remaining = (state.endTime - clock()).coerceAtLeast(0L)
        commit(snapshot.replace(item.copy(state = TimerState.Paused(remaining, state.totalMillis))))
    }

    fun resume() {
        val snapshot = snapshot()
        val item = snapshot.active
        val state = item.state as? TimerState.Paused ?: return
        if (state.remainingMillis < TimerLimits.MIN_RUNNABLE_MILLIS) {
            commit(snapshot.replace(item.copy(state = TimerState.Idle)))
            start()
            return
        }
        commit(
            snapshot.replace(
                item.copy(state = TimerState.Running(clock() + state.remainingMillis, state.totalMillis))
            )
        )
    }

    fun stop() {
        stopTimer(active().id)
    }

    /** Zatrzymuje aktywny timer i zeruje jego ustawiony czas. */
    fun reset() {
        val snapshot = snapshot()
        val item = snapshot.active
        if (item.state is TimerState.Idle && item.presetMillis == 0L) return
        commit(snapshot.replace(item.copy(state = TimerState.Idle, presetMillis = 0L)))
    }

    fun stopTimer(id: Long) {
        val snapshot = snapshot()
        val item = snapshot.find(id) ?: return
        if (item.state is TimerState.Idle) {
            // Czyścimy też to, co mogło zostać po zabitym procesie.
            alarms.cancelTimer(id)
            notifier.cancel(id)
            return
        }
        // Zatrzymanie dzwoniącego alarmu zeruje timer — jak w zegarze kuchennym.
        val next = if (item.state is TimerState.Ringing) {
            item.copy(state = TimerState.Idle, presetMillis = 0L)
        } else {
            item.copy(state = TimerState.Idle)
        }
        commit(snapshot.replace(next))
    }

    // --- dzwonienie ---------------------------------------------------------

    /**
     * Alarm systemowy właśnie zadziałał. `false`, gdy timer nie odliczał —
     * wtedy nie wolno uruchamiać dzwonka.
     */
    fun onTimeFired(id: Long): Boolean {
        val snapshot = snapshot()
        val item = snapshot.find(id) ?: return false
        if (item.state !is TimerState.Running) return false
        commit(snapshot.replace(item.copy(state = TimerState.Ringing(clock()))))
        return true
    }

    fun onRingFinished(id: Long) {
        val snapshot = snapshot()
        val item = snapshot.find(id) ?: return
        if (item.state !is TimerState.Ringing) return
        // Po dzwonieniu tarcza wraca na zero.
        commit(snapshot.replace(item.copy(state = TimerState.Idle, presetMillis = 0L)))
    }

    /** Kolejny timer, który już się skończył i czeka na swoją kolej dzwonienia. */
    fun nextRinging(excludeId: Long = -1L): TimerItem? =
        snapshot().timers
            .filter { it.id != excludeId && it.state is TimerState.Ringing }
            .minByOrNull { (it.state as TimerState.Ringing).since }

    // --- uzgadnianie stanu --------------------------------------------------

    /**
     * Porządkuje stan po powrocie do aplikacji lub po restarcie telefonu:
     * wykrywa zgubione alarmy i zawieszone dzwonki, a działające timery uzbraja ponownie.
     */
    fun reconcile(missedGraceMs: Long = MISSED_GRACE_MS): ReconcileResult {
        val snapshot = snapshot()
        val now = clock()
        var changed = false
        var missed = false
        var stale = false
        val timers = snapshot.timers.map { item ->
            when (val state = item.state) {
                is TimerState.Running -> if (now > state.endTime + missedGraceMs) {
                    changed = true
                    missed = true
                    item.copy(state = TimerState.Idle)
                } else {
                    item
                }
                is TimerState.Ringing -> if (now - state.since > MAX_RINGING_AGE_MS) {
                    changed = true
                    stale = true
                    item.copy(state = TimerState.Idle, presetMillis = 0L)
                } else {
                    item
                }
                else -> item
            }
        }
        if (changed) {
            commit(snapshot.copy(timers = timers))
        } else {
            // Odświeżamy to, co widać w powiadomieniach, i uzbrajamy alarmy ponownie.
            snapshot.timers.forEach { item ->
                when (val state = item.state) {
                    is TimerState.Running -> {
                        alarms.scheduleTimer(item.id, state.endTime)
                        notifier.showRunning(item.id, item.label, state.endTime)
                    }
                    is TimerState.Paused ->
                        notifier.showPaused(item.id, item.label, state.remainingMillis)
                    else -> Unit
                }
            }
        }
        return when {
            missed -> ReconcileResult.TIMER_MISSED
            stale -> ReconcileResult.RINGING_STALE
            else -> ReconcileResult.UNCHANGED
        }
    }

    // --- wnętrze ------------------------------------------------------------

    private fun commit(snapshot: TimersSnapshot) {
        val safe = snapshot.normalized()
        store.save(safe)
        safe.timers.forEach { item ->
            when (val state = item.state) {
                is TimerState.Running -> {
                    alarms.scheduleTimer(item.id, state.endTime)
                    notifier.showRunning(item.id, item.label, state.endTime)
                }
                is TimerState.Paused -> {
                    alarms.cancelTimer(item.id)
                    notifier.showPaused(item.id, item.label, state.remainingMillis)
                }
                is TimerState.Ringing -> {
                    alarms.cancelTimer(item.id)
                    notifier.cancel(item.id)
                }
                TimerState.Idle -> {
                    alarms.cancelTimer(item.id)
                    notifier.cancel(item.id)
                }
            }
        }
    }

    private fun clampPreset(millis: Long): Long = millis.coerceIn(0L, TimerLimits.MAX_PRESET_MILLIS)

    private fun nextLabel(snapshot: TimersSnapshot): String {
        val taken = snapshot.timers.map { it.label }.toSet()
        var index = snapshot.timers.size + 1
        while (taken.contains("Timer $index")) index++
        return "Timer $index"
    }

    private companion object {
        /** Zapas na opóźnienie odbiornika, zanim uznamy alarm za zgubiony. */
        const val MISSED_GRACE_MS = 5_000L

        /** Najdłuższy możliwy dzwonek (60 sekund serii) plus zapas. */
        const val MAX_RINGING_AGE_MS = 2 * 60 * 1_000L

        const val MAX_LABEL_LENGTH = 24
    }
}
