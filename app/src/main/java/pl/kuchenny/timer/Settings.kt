package pl.kuchenny.timer

import android.content.Context
import android.content.SharedPreferences
import pl.kuchenny.timer.core.TimerLimits

/**
 * Jedno miejsce, w którym zdefiniowane są wszystkie klucze SharedPreferences
 * i typowane ustawienia dzwonka.
 *
 * Same timery (czasy, nazwy, stan) trzyma `SharedPreferencesTimersStore`.
 */
class Settings(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var soundStyle: Int
        get() = preferences.getInt(KEY_SOUND_STYLE, 0).coerceIn(0, SOUND_NAMES.lastIndex)
        set(value) {
            preferences.edit()
                .putInt(KEY_SOUND_STYLE, value.coerceIn(0, SOUND_NAMES.lastIndex))
                .apply()
        }

    var bellCount: Int
        get() = preferences.getInt(KEY_BELL_COUNT, 1)
            .coerceIn(TimerLimits.MIN_BELL_COUNT, TimerLimits.MAX_BELL_COUNT)
        set(value) {
            preferences.edit()
                .putInt(
                    KEY_BELL_COUNT,
                    value.coerceIn(TimerLimits.MIN_BELL_COUNT, TimerLimits.MAX_BELL_COUNT)
                )
                .apply()
        }

    var customSoundUri: String?
        get() = preferences.getString(KEY_CUSTOM_SOUND_URI, null)
        set(value) {
            preferences.edit().putString(KEY_CUSTOM_SOUND_URI, value).apply()
        }

    /** Siła wibracji 0–10; zero oznacza wibrację wyłączoną. */
    var vibrationStrength: Int
        get() {
            val stored = preferences.getInt(KEY_VIBRATION_STRENGTH, UNSET)
            if (stored != UNSET) return stored.coerceIn(0, TimerLimits.MAX_VIBRATION_STRENGTH)
            // Migracja ze starego przełącznika włącz/wyłącz.
            val legacyOn = preferences.getBoolean(KEY_LEGACY_VIBRATION, true)
            return if (legacyOn) DEFAULT_VIBRATION_STRENGTH else 0
        }
        set(value) {
            preferences.edit()
                .putInt(
                    KEY_VIBRATION_STRENGTH,
                    value.coerceIn(0, TimerLimits.MAX_VIBRATION_STRENGTH)
                )
                .remove(KEY_LEGACY_VIBRATION)
                .apply()
        }

    /** Głośność dzwonka w procentach (0–100) — niezależna od głośności systemu. */
    var alarmVolume: Int
        get() = preferences.getInt(KEY_ALARM_VOLUME, DEFAULT_VOLUME).coerceIn(0, 100)
        set(value) {
            preferences.edit().putInt(KEY_ALARM_VOLUME, value.coerceIn(0, 100)).apply()
        }

    companion object {
        const val FILE_NAME = "kuchenny_zegar"

        /** Domyślny czas pierwszego timera — używany też przy migracji starych ustawień. */
        const val DEFAULT_MINUTES = 15
        const val DEFAULT_VOLUME = 100
        const val DEFAULT_VIBRATION_STRENGTH = 7

        val SOUND_NAMES = listOf(
            "Dzwon 1 · kryształ",
            "Dzwon 2 · ciepły",
            "Dzwon 3 · niski",
            "Dzwon 4 · kurant",
            "Dzwon 5 · srebrny",
            "Dzwon 6 · miękki",
            "Dzwon 7 · jasny",
            "Dzwon 8 · wysoki"
        )

        private const val UNSET = -1
        private const val KEY_SOUND_STYLE = "sound_style"
        private const val KEY_BELL_COUNT = "bell_count"
        private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
        private const val KEY_VIBRATION_STRENGTH = "vibration_strength"
        private const val KEY_LEGACY_VIBRATION = "vibration"
        private const val KEY_ALARM_VOLUME = "alarm_volume"
    }
}
