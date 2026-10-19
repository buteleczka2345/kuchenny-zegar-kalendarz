package pl.kuchenny.budzik

import android.content.Context
import pl.kuchenny.budzik.alarm.PlatformAlarmScheduler
import pl.kuchenny.budzik.core.AlarmController
import pl.kuchenny.budzik.core.SharedPreferencesAlarmStore

/** Składa obiekty aplikacji w jednym miejscu. */
object BudzikGraph {

    fun controller(context: Context): AlarmController {
        val appContext = context.applicationContext
        return AlarmController(
            store = SharedPreferencesAlarmStore(appContext),
            scheduler = PlatformAlarmScheduler(appContext)
        )
    }
}
