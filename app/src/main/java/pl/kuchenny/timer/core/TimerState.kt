package pl.kuchenny.timer.core

import java.util.concurrent.TimeUnit

/**
 * Stan jednego timera kuchennego.
 *
 * Typ zamknięty gwarantuje, że timer jest dokładnie w jednym z czterech stanów.
 */
sealed interface TimerState {
    /** Timer nie odlicza; ekran pokazuje ustawiony czas. */
    data object Idle : TimerState

    /** Odliczanie trwa do [endTime] (czas systemowy, przeżywa restart procesu). */
    data class Running(val endTime: Long, val totalMillis: Long) : TimerState

    /** Odliczanie zatrzymane; [remainingMillis] to reszta do wznowienia. */
    data class Paused(val remainingMillis: Long, val totalMillis: Long) : TimerState

    /** Czas minął i dzwonek właśnie gra; [since] pozwala wykryć stan zawieszony. */
    data class Ringing(val since: Long) : TimerState
}

/**
 * Timer z listy: nazwa, ustawiony czas i stan.
 *
 * Czas trzymamy w milisekundach, bo przyciski +30 s nie mieszczą się w minutach.
 */
data class TimerItem(
    val id: Long,
    val label: String,
    val presetMillis: Long,
    val state: TimerState
) {
    fun remainingMillis(now: Long): Long = when (state) {
        is TimerState.Running -> (state.endTime - now).coerceAtLeast(0L)
        is TimerState.Paused -> state.remainingMillis.coerceAtLeast(0L)
        is TimerState.Ringing -> 0L
        TimerState.Idle -> presetMillis.coerceAtLeast(0L)
    }

    fun totalMillis(): Long = when (state) {
        is TimerState.Running -> state.totalMillis
        is TimerState.Paused -> state.totalMillis
        TimerState.Idle, is TimerState.Ringing -> presetMillis.coerceAtLeast(0L)
    }

    fun progress(now: Long): Float {
        val total = totalMillis().coerceAtLeast(1L)
        return (remainingMillis(now).toFloat() / total).coerceIn(0f, 1f)
    }

    fun isRunning(): Boolean = state is TimerState.Running
    fun isPaused(): Boolean = state is TimerState.Paused
    fun isRinging(): Boolean = state is TimerState.Ringing

    /** Odrzuca wartości niemożliwe, np. po odczycie uszkodzonych preferencji. */
    fun normalized(): TimerItem {
        val safePreset = presetMillis.coerceIn(0L, TimerLimits.MAX_PRESET_MILLIS)
        val safeLabel = label.trim().ifBlank { "Timer" }
        val safeState = when (val current = state) {
            is TimerState.Running -> if (current.endTime <= 0L || current.totalMillis <= 0L) {
                TimerState.Idle
            } else {
                current
            }
            is TimerState.Paused -> if (current.totalMillis <= 0L) {
                TimerState.Idle
            } else {
                current.copy(remainingMillis = current.remainingMillis.coerceAtLeast(0L))
            }
            else -> current
        }
        return copy(presetMillis = safePreset, label = safeLabel, state = safeState)
    }
}

/**
 * Cała lista timerów i wskazanie, który jest aktywny na ekranie.
 *
 * Wcześniej aplikacja znała dokładnie jeden timer; teraz każdy ma własny stan,
 * alarm i powiadomienie.
 */
data class TimersSnapshot(
    val activeId: Long,
    val timers: List<TimerItem>
) {
    val active: TimerItem
        get() = timers.firstOrNull { it.id == activeId } ?: timers.first()

    fun find(id: Long): TimerItem? = timers.firstOrNull { it.id == id }

    fun replace(item: TimerItem): TimersSnapshot =
        copy(timers = timers.map { if (it.id == item.id) item else it })

    fun ringing(): List<TimerItem> = timers.filter { it.state is TimerState.Ringing }

    fun normalized(): TimersSnapshot {
        if (timers.isEmpty()) return default()
        val safeTimers = timers
            .distinctBy { it.id }
            .map { it.normalized() }
            .ifEmpty { default().timers }
        val safeActive = if (safeTimers.any { it.id == activeId }) activeId else safeTimers.first().id
        return TimersSnapshot(activeId = safeActive, timers = safeTimers)
    }

    companion object {
        const val DEFAULT_PRESET_MILLIS = 15 * 60_000L

        /** Dwa timery na start — tak jak w aplikacji, do której się wzorujemy. */
        fun default(): TimersSnapshot = TimersSnapshot(
            activeId = 1L,
            timers = listOf(
                TimerItem(1L, "Timer 1", DEFAULT_PRESET_MILLIS, TimerState.Idle),
                TimerItem(2L, "Timer 2", 0L, TimerState.Idle)
            )
        )
    }
}

/** Wygodny skrót dla minut z tarczy. */
fun minutesToMillis(minutes: Int): Long = TimeUnit.MINUTES.toMillis(minutes.toLong())
