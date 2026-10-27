package pl.kuchenny.budzik.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import pl.kuchenny.budzik.AlarmSettings
import pl.kuchenny.budzik.core.HourlyChime

/** Pyknięcie o pełnej godzinie i zaplanowanie następnego. */
class ChimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHIME) return
        val settings = AlarmSettings(context)
        schedule(context)
        if (!settings.chimeEnabled || AlarmService.isRinging) return

        // System mógł spóźnić alarm — pykamy godzinę, która właśnie wybiła.
        val now = System.currentTimeMillis()
        val hour = HourlyChime.hourOf(now + LATE_TOLERANCE_MS)
        if (!HourlyChime.isActive(hour, settings.chimeFrom, settings.chimeTo)) return

        val pending = goAsync()
        val wakeLock = context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Budzik:Chime")
            ?.apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        val done = {
            wakeLock?.let { if (it.isHeld) it.release() }
            pending.finish()
        }
        if (settings.chimeSpeak) {
            HourVoicePlayer.play(context, hour, settings.chimeVolume / 100f, done)
        } else {
            ChimePlayer.play(settings.chimeVolume / 100f, done)
        }
    }

    companion object {
        const val ACTION_CHIME = "pl.kuchenny.budzik.CHIME"

        private const val REQUEST_CHIME = 3
        private const val LATE_TOLERANCE_MS = 5 * 60 * 1_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 9_000L

        /** Planuje następne pyknięcie albo je odwołuje, gdy pykanie jest wyłączone. */
        fun schedule(context: Context) {
            val settings = AlarmSettings(context)
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pendingIntent = pendingIntent(context)
            if (!settings.chimeEnabled) {
                alarmManager.cancel(pendingIntent)
                return
            }
            val triggerAt = HourlyChime.next(
                System.currentTimeMillis(),
                settings.chimeFrom,
                settings.chimeTo
            )
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            try {
                if (exact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    return
                }
            } catch (exception: SecurityException) {
                // Zabrakło zgody — zostaje alarm niedokładny.
            }
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CHIME,
            Intent(context, ChimeReceiver::class.java).setAction(ACTION_CHIME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
