package pl.kuchenny.timer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import pl.kuchenny.timer.core.AlarmScheduler

/**
 * Planowanie alarmów systemowych.
 *
 * Każdy timer dostaje własny `PendingIntent` (rozróżniany adresem danych),
 * więc kilka timerów może być uzbrojonych naraz.
 */
class PlatformAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun scheduleTimer(timerId: Long, endTime: Long) {
        schedule(timerPendingIntent(timerId), endTime)
    }

    override fun cancelTimer(timerId: Long) {
        cancel(timerPendingIntent(timerId))
    }

    fun scheduleReminder(reminderId: Long, at: Long) {
        schedule(reminderPendingIntent(reminderId), at)
    }

    fun cancelReminder(reminderId: Long) {
        cancel(reminderPendingIntent(reminderId))
    }

    private fun schedule(pendingIntent: PendingIntent, endTime: Long) {
        // Alarm w przeszłości ma zadziałać od razu, ale nie z datą ujemną.
        val triggerAt = maxOf(endTime, System.currentTimeMillis() + MIN_TRIGGER_DELAY_MS)
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                return
            } catch (exception: SecurityException) {
                // Zabrakło zgody w trakcie planowania — schodzimy do alarmu niedokładnego.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun cancel(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun timerPendingIntent(timerId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_TIMER,
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_TIMER_FINISHED)
            .setData(Uri.parse("$SCHEME://timer/$timerId")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun reminderPendingIntent(reminderId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_TIMER,
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_REMINDER)
            .setData(Uri.parse("$SCHEME://reminder/$reminderId")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val SCHEME = "kuchenny-zegar"
        const val REQUEST_TIMER = 1
        const val MIN_TRIGGER_DELAY_MS = 1_000L
    }
}
