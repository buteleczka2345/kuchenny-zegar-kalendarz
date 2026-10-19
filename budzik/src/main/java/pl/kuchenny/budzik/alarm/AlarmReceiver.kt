package pl.kuchenny.budzik.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.kuchenny.budzik.BudzikGraph

/** Alarm systemowy budzika. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ALARM) return
        val alarmId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val alarm = BudzikGraph.controller(context).onFired(alarmId) ?: return
        if (!alarm.enabled) return
        AlarmService.start(context, alarmId)
    }

    companion object {
        const val ACTION_ALARM = "pl.kuchenny.budzik.ALARM"
    }
}
