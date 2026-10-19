package pl.kuchenny.timer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.kuchenny.timer.TimerGraph

/** Alarm systemowy jednego z timerów kuchennych. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TIMER_FINISHED) return
        val timerId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        // Spóźniony albo wcześniej anulowany alarm nie może uruchomić dzwonka.
        if (!TimerGraph.controller(context).onTimeFired(timerId)) return
        AlarmService.startTimer(context, timerId)
    }

    companion object {
        const val ACTION_TIMER_FINISHED = "pl.kuchenny.timer.TIMER_FINISHED"
    }
}
