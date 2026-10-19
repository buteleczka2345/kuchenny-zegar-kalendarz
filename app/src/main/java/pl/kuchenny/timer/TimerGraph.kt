package pl.kuchenny.timer

import android.content.Context
import pl.kuchenny.timer.alarm.AndroidTimerNotifier
import pl.kuchenny.timer.alarm.PlatformAlarmScheduler
import pl.kuchenny.timer.core.SharedPreferencesTimersStore
import pl.kuchenny.timer.core.TimerController

/**
 * Składa obiekty aplikacji w jednym miejscu.
 *
 * Dzięki temu aktywność, serwis i odbiorniki dostają ten sam zestaw zależności
 * i nie sięgają już do statycznych metod `MainActivity`.
 */
object TimerGraph {

    fun controller(context: Context): TimerController {
        val appContext = context.applicationContext
        return TimerController(
            store = SharedPreferencesTimersStore(appContext),
            alarms = PlatformAlarmScheduler(appContext),
            notifier = AndroidTimerNotifier(appContext)
        )
    }
}
