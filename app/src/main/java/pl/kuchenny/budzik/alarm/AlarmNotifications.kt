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

/** Powiadomienie dzwoniącego budzika. */
object AlarmNotifications {

    const val ID_ALARM = 1

    private const val CHANNEL = "budzik_alarm"
    private const val REQUEST_STOP_SCREEN = 10
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
        val stopScreen = stopScreenIntent(context, title)
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(stopScreen)
            .setFullScreenIntent(stopScreen, true)
            .addAction(android.R.drawable.ic_media_pause, "Zatrzymaj budzik", stopIntent)
            .build()
    }

    /** Ekran z dużym czerwonym STOP — zapala ekran i pokazuje się nad blokadą. */
    private fun stopScreenIntent(context: Context, title: String): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_STOP_SCREEN,
        AlarmStopActivity.intent(context, title),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
