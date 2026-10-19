package pl.kuchenny.budzik.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import pl.kuchenny.budzik.MainActivity

/** Powiadomienie dzwoniącego budzika. */
object AlarmNotifications {

    const val ID_ALARM = 1

    private const val CHANNEL = "budzik_alarm"
    private const val REQUEST_OPEN_APP = 10
    private const val REQUEST_STOP = 11

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Dzwoniący budzik",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm budzika"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun build(context: Context, title: String, text: String): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_STOP,
            Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .addAction(android.R.drawable.ic_media_pause, "Zatrzymaj budzik", stopIntent)
            .build()
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
