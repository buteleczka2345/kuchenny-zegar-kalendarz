package pl.kuchenny.timer.core

import android.content.Context
import android.content.SharedPreferences
import pl.kuchenny.timer.Settings

/**
 * Lista timerów w SharedPreferences jako jeden JSON.
 *
 * Przy pierwszym uruchomieniu po aktualizacji tłumaczy stary, pojedynczy timer
 * na „Timer 1” i dokłada pusty „Timer 2” — nic nie przepada.
 */
class SharedPreferencesTimersStore(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis
) : TimersStore {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(Settings.FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): TimersSnapshot {
        val decoded = TimerListCodec.decode(preferences.getString(KEY_TIMERS, null))
        if (decoded != null) return decoded
        val migrated = migrateLegacy()
        save(migrated)
        return migrated
    }

    override fun save(snapshot: TimersSnapshot) {
        preferences.edit()
            .putString(KEY_TIMERS, TimerListCodec.encode(snapshot.normalized()))
            .apply()
        removeLegacyKeys()
    }

    private fun migrateLegacy(): TimersSnapshot {
        val presetMinutes = preferences.getInt(LEGACY_SELECTED_MINUTES, Settings.DEFAULT_MINUTES)
            .coerceIn(0, TimerLimits.MAX_MINUTES)
        val state = when (preferences.getString(KEY_TIMER_STATE, null)) {
            STATE_RUNNING -> TimerState.Running(
                endTime = preferences.getLong(LEGACY_END_TIME, 0L),
                totalMillis = preferences.getLong(LEGACY_TOTAL_MILLIS, 0L)
            )
            STATE_PAUSED -> TimerState.Paused(
                remainingMillis = preferences.getLong(LEGACY_PAUSED_MILLIS, 0L),
                totalMillis = preferences.getLong(LEGACY_TOTAL_MILLIS, 0L)
            )
            STATE_RINGING -> TimerState.Ringing(since = now())
            else -> when {
                preferences.getBoolean(LEGACY_RUNNING, false) -> TimerState.Running(
                    endTime = preferences.getLong(LEGACY_END_TIME, 0L),
                    totalMillis = preferences.getLong(LEGACY_TOTAL_MILLIS, 0L)
                )
                preferences.getBoolean(LEGACY_PAUSED, false) -> TimerState.Paused(
                    remainingMillis = preferences.getLong(LEGACY_PAUSED_MILLIS, 0L),
                    totalMillis = preferences.getLong(LEGACY_TOTAL_MILLIS, 0L)
                )
                preferences.getBoolean(LEGACY_RINGING, false) -> TimerState.Ringing(since = now())
                else -> TimerState.Idle
            }
        }
        val first = TimerItem(
            id = 1L,
            label = "Timer 1",
            presetMillis = presetMinutes * 60_000L,
            state = state
        ).normalized()
        val second = TimerItem(id = 2L, label = "Timer 2", presetMillis = 0L, state = TimerState.Idle)
        return TimersSnapshot(activeId = 1L, timers = listOf(first, second)).normalized()
    }

    private fun removeLegacyKeys() {
        preferences.edit()
            .remove(KEY_TIMER_STATE)
            .remove(LEGACY_SELECTED_MINUTES)
            .remove(LEGACY_END_TIME)
            .remove(LEGACY_TOTAL_MILLIS)
            .remove(LEGACY_PAUSED_MILLIS)
            .remove(KEY_RINGING_SINCE)
            .remove(LEGACY_RUNNING)
            .remove(LEGACY_PAUSED)
            .remove(LEGACY_RINGING)
            .apply()
    }

    private companion object {
        const val KEY_TIMERS = "timers_json"
        const val KEY_TIMER_STATE = "timer_state"
        const val KEY_RINGING_SINCE = "ringing_since"

        const val LEGACY_SELECTED_MINUTES = "selected_minutes"
        const val LEGACY_END_TIME = "end_time"
        const val LEGACY_TOTAL_MILLIS = "total_millis"
        const val LEGACY_PAUSED_MILLIS = "paused_millis"
        const val LEGACY_RUNNING = "running"
        const val LEGACY_PAUSED = "paused"
        const val LEGACY_RINGING = "ringing"

        const val STATE_RUNNING = "running"
        const val STATE_PAUSED = "paused"
        const val STATE_RINGING = "ringing"
    }
}
