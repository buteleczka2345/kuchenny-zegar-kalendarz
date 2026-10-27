package pl.kuchenny.budzik.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.kuchenny.budzik.BudzikGraph

/** Po restarcie telefonu i zmianie godziny alarmy wracają do systemu. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        BudzikGraph.controller(context).rescheduleAll()
        ChimeReceiver.schedule(context)
    }

    private companion object {
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED
        )
    }
}
