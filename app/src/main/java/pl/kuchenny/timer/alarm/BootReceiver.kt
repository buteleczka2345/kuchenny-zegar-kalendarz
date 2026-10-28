package pl.kuchenny.timer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.kuchenny.timer.TimerGraph

/**
 * Porządkowanie po restarcie telefonu, zmianie godziny i zmianie strefy czasowej.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        // Timer, który miał zadzwonić w trakcie restartu, jeszcze się odezwie.
        TimerGraph.controller(context).reconcile(missedGraceMs = BOOT_GRACE_MS)
        // Przypomnienie, które minęło przy wyłączonym telefonie, zagra od razu.
        Reminders(context).rescheduleAll()
    }

    private companion object {
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        /** Po restarcie urządzenia dajemy timerowi więcej czasu, zanim uznamy go za zgubiony. */
        const val BOOT_GRACE_MS = 5 * 60 * 1_000L

        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED
        )
    }
}
