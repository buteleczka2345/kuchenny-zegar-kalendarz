package pl.kuchenny.budzik

import android.content.Context
import android.content.SharedPreferences
import pl.kuchenny.budzik.core.CustomSound
import pl.kuchenny.budzik.core.CustomSoundCodec
import pl.kuchenny.budzik.core.HourlyChime
import pl.kuchenny.budzik.core.Ramp
import pl.kuchenny.budzik.core.Shake

/** Ustawienia dzwonka budzika — wspólne dla wszystkich alarmów. */
class AlarmSettings(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(SharedPreferencesAlarmStoreName, Context.MODE_PRIVATE)

    var melodyStyle: Int
        get() = preferences.getInt(KEY_MELODY, 0).coerceIn(0, MELODY_NAMES.lastIndex)
        set(value) {
            preferences.edit().putInt(KEY_MELODY, value.coerceIn(0, MELODY_NAMES.lastIndex)).apply()
        }

    /** Własne dźwięki dodane z telefonu. */
    var customSounds: List<CustomSound>
        get() = CustomSoundCodec.decode(preferences.getString(KEY_CUSTOM_SOUNDS, null))
        set(value) {
            preferences.edit()
                .putString(KEY_CUSTOM_SOUNDS, CustomSoundCodec.encode(value.take(CustomSound.MAX_SOUNDS)))
                .apply()
        }

    /**
     * Wybrany własny dźwięk albo `null`, gdy gra wbudowana [melodyStyle].
     * Dźwięk usunięty z listy przestaje być wybrany.
     */
    var selectedCustomSound: CustomSound?
        get() {
            val uri = preferences.getString(KEY_SELECTED_SOUND, null) ?: return null
            return customSounds.firstOrNull { it.uri == uri }
        }
        set(value) {
            preferences.edit().putString(KEY_SELECTED_SOUND, value?.uri).apply()
        }

    /** Głośność końcowa w procentach. */
    var volume: Int
        get() = preferences.getInt(KEY_VOLUME, 100).coerceIn(0, 100)
        set(value) {
            preferences.edit().putInt(KEY_VOLUME, value.coerceIn(0, 100)).apply()
        }

    /** Narastanie głośności w sekundach: od 0 do 120. */
    var rampSeconds: Int
        get() = preferences.getInt(KEY_RAMP, 0).coerceIn(0, Ramp.MAX_SECONDS)
        set(value) {
            preferences.edit().putInt(KEY_RAMP, value.coerceIn(0, Ramp.MAX_SECONDS)).apply()
        }

    var vibrationStrength: Int
        get() = preferences.getInt(KEY_VIBRATION, 7).coerceIn(0, 10)
        set(value) {
            preferences.edit().putInt(KEY_VIBRATION, value.coerceIn(0, 10)).apply()
        }

    var shakeEnabled: Boolean
        get() = preferences.getBoolean(KEY_SHAKE, true)
        set(value) {
            preferences.edit().putBoolean(KEY_SHAKE, value).apply()
        }

    /** Siła potrząśnięcia potrzebna do wyłączenia: od 1 (lekko) do 10 (mocno). */
    var shakeLevel: Int
        get() = preferences.getInt(KEY_SHAKE_LEVEL, Shake.DEFAULT_LEVEL)
            .coerceIn(Shake.MIN_LEVEL, Shake.MAX_LEVEL)
        set(value) {
            preferences.edit()
                .putInt(KEY_SHAKE_LEVEL, value.coerceIn(Shake.MIN_LEVEL, Shake.MAX_LEVEL))
                .apply()
        }

    var flipEnabled: Boolean
        get() = preferences.getBoolean(KEY_FLIP, true)
        set(value) {
            preferences.edit().putBoolean(KEY_FLIP, value).apply()
        }

    /** Włączanie ekranu, gdy budzik dzwoni. */
    var screenOnEnabled: Boolean
        get() = preferences.getBoolean(KEY_SCREEN_ON, true)
        set(value) {
            preferences.edit().putBoolean(KEY_SCREEN_ON, value).apply()
        }

    /** Miganie latarką, gdy budzik dzwoni. */
    var flashEnabled: Boolean
        get() = preferences.getBoolean(KEY_FLASH, false)
        set(value) {
            preferences.edit().putBoolean(KEY_FLASH, value).apply()
        }

    /** Pykanie o pełnej godzinie. */
    var chimeEnabled: Boolean
        get() = preferences.getBoolean(KEY_CHIME, false)
        set(value) {
            preferences.edit().putBoolean(KEY_CHIME, value).apply()
        }

    /** Zamiast pyknięcia głos mówi, która jest godzina. */
    var chimeSpeak: Boolean
        get() = preferences.getBoolean(KEY_CHIME_SPEAK, false)
        set(value) {
            preferences.edit().putBoolean(KEY_CHIME_SPEAK, value).apply()
        }

    /** Pierwsza godzina, o której pyka (0–23). */
    var chimeFrom: Int
        get() = preferences.getInt(KEY_CHIME_FROM, HourlyChime.DEFAULT_FROM).coerceIn(0, 23)
        set(value) {
            preferences.edit().putInt(KEY_CHIME_FROM, value.coerceIn(0, 23)).apply()
        }

    /** Ostatnia godzina, o której pyka (0–23). */
    var chimeTo: Int
        get() = preferences.getInt(KEY_CHIME_TO, HourlyChime.DEFAULT_TO).coerceIn(0, 23)
        set(value) {
            preferences.edit().putInt(KEY_CHIME_TO, value.coerceIn(0, 23)).apply()
        }

    /** Głośność pykania w procentach. */
    var chimeVolume: Int
        get() = preferences.getInt(KEY_CHIME_VOLUME, 60).coerceIn(0, 100)
        set(value) {
            preferences.edit().putInt(KEY_CHIME_VOLUME, value.coerceIn(0, 100)).apply()
        }

    companion object {
        private const val SharedPreferencesAlarmStoreName = "budzik"

        const val MAX_VIBRATION_STRENGTH = 10

        val MELODY_NAMES = listOf(
            "Melodia 1 · delikatna",
            "Melodia 2 · klasyczny budzik",
            "Melodia 3 · dzwonki",
            "Melodia 4 · poranna",
            "Melodia 5 · głęboka",
            "Melodia 6 · energiczna",
            "Melodia 7 · ćwierkanie ptaków",
            "Melodia 8 · kogut",
            "Melodia 9 · kukułka",
            "Melodia 10 · stary telefon"
        )

        private const val KEY_MELODY = "melody_style"
        private const val KEY_CUSTOM_SOUNDS = "custom_sounds"
        private const val KEY_SELECTED_SOUND = "selected_custom_sound"
        private const val KEY_VOLUME = "alarm_volume"
        private const val KEY_RAMP = "ramp_seconds"
        private const val KEY_VIBRATION = "vibration_strength"
        private const val KEY_SHAKE = "shake_dismiss"
        private const val KEY_SHAKE_LEVEL = "shake_level"
        private const val KEY_FLIP = "flip_dismiss"
        private const val KEY_SCREEN_ON = "screen_on"
        private const val KEY_FLASH = "flash_blink"
        private const val KEY_CHIME = "chime_enabled"
        private const val KEY_CHIME_SPEAK = "chime_speak"
        private const val KEY_CHIME_FROM = "chime_from"
        private const val KEY_CHIME_TO = "chime_to"
        private const val KEY_CHIME_VOLUME = "chime_volume"
    }
}
