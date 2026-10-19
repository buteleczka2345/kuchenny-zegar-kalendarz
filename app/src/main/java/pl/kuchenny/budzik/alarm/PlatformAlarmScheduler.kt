package pl.kuchenny.budzik.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import pl.kuchenny.budzik.MainActivity
import pl.kuchenny.budzik.core.AlarmScheduler

/** Planowanie alarmów w systemie — każdy alarm ma własny `PendingIntent`. */
class PlatformAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(alarmId: Long, triggerAt: Long) {
        val pendingIntent = alarmPendingIntent(alarmId)
        if (canScheduleExact()) {
            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent()),
                    pendingIntent
                )
                return
            } catch (exception: SecurityException) {
                // Zabrakło zgody — zostaje alarm niedokładny.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    override fun cancel(alarmId: Long) {
        val pendingIntent = alarmPendingIntent(alarmId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmPendingIntent(alarmId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_ALARM)
            .setData(Uri.parse("$SCHEME://alarm/$alarmId")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SHOW_APP,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val SCHEME = "budzik"
        const val REQUEST_ALARM = 1
        const val REQUEST_SHOW_APP = 2
    }
}
