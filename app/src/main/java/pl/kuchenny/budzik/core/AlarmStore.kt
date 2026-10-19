package pl.kuchenny.budzik.core

import android.content.Context
import android.content.SharedPreferences

/** Zapis i odczyt listy alarmów. */
interface AlarmStore {
    fun load(): AlarmsSnapshot
    fun save(snapshot: AlarmsSnapshot)
}

class SharedPreferencesAlarmStore(context: Context) : AlarmStore {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): AlarmsSnapshot =
        AlarmCodec.decode(preferences.getString(KEY_ALARMS, null)) ?: AlarmsSnapshot.EMPTY

    override fun save(snapshot: AlarmsSnapshot) {
        preferences.edit()
            .putString(KEY_ALARMS, AlarmCodec.encode(snapshot.normalized()))
            .apply()
    }

    companion object {
        const val FILE_NAME = "budzik"
        private const val KEY_ALARMS = "alarms_json"
    }
}
