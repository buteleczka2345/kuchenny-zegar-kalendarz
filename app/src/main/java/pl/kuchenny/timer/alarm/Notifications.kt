package pl.kuchenny.timer.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import pl.kuchenny.timer.MainActivity
import pl.kuchenny.timer.core.TimeFormat
import pl.kuchenny.timer.core.TimerNotifier

/**
 * Wszystkie powiadomienia aplikacji w jednym miejscu: kanał, alarm,
 * informacja o końcu dzwonienia oraz powiadomienie każdego działającego timera.
 */
object Notifications {

    const val ID_ALARM = 42
    const val ID_FINISHED = 43

    /** Powiadomienia timerów dostają numer 100 + id timera. */
    const val RUNNING_ID_BASE = 100

    private const val CHANNEL_ALARM = "kitchen_timer_alarm"
    private const val CHANNEL_RUNNING = "kitchen_timer_running"

    private const val REQUEST_OPEN_APP = 0
    private const val REQUEST_STOP_ALARM = 1
    private const val REQUEST_STOP_TIMER = 3

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            "Dzwonek kuchennego zegara",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Dzwonienie timera kuchennego"
            setSound(null, null)
        }
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            "Działające timery",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Odliczanie widoczne po schowaniu aplikacji"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(alarm, running))
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun buildAlarm(context: Context, title: String, message: String): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_STOP_ALARM,
            Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return builder(context, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                android.R.drawable.ic_media_pause,
                "Zatrzymaj dzwonienie",
                stopIntent
            )
            .build()
    }

    fun showFinished(context: Context, label: String) {
        if (!canPost(context)) return
        val notification = builder(context, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Czas minął. Alarm zakończył granie.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(ID_FINISHED, notification)
    }

    fun buildRunningTimer(
        context: Context,
        timerId: Long,
        label: String,
        endTime: Long
    ): Notification {
        val now = System.currentTimeMillis()
        val finishAt = TimeFormat.finishTime(now, endTime - now)
        val builder = builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Gotowe o $finishAt")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(endTime)
            .setContentIntent(openAppIntent(context))
            .addAction(
                android.R.drawable.ic_media_pause,
                "Zatrzymaj timer",
                stopTimerIntent(context, timerId)
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        }
        return builder.build()
    }

    fun buildPausedTimer(
        context: Context,
        timerId: Long,
        label: String,
        remainingMillis: Long
    ): Notification =
        builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$label — pauza")
            .setContentText("Pozostało ${TimeFormat.remaining(remainingMillis)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
            .addAction(
                android.R.drawable.ic_media_pause,
                "Zatrzymaj timer",
                stopTimerIntent(context, timerId)
            )
            .build()

    fun cancelTimerNotification(context: Context, timerId: Long) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(RUNNING_ID_BASE + timerId.toInt())
    }

    private fun builder(context: Context, channelId: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun stopTimerIntent(context: Context, timerId: Long): PendingIntent = PendingIntent.getService(
        context,
        REQUEST_STOP_TIMER,
        Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_STOP_TIMER)
            .putExtra(AlarmService.EXTRA_TIMER_ID, timerId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Implementacja [TimerNotifier] dla urządzenia. */
class AndroidTimerNotifier(context: Context) : TimerNotifier {

    private val appContext = context.applicationContext

    override fun showRunning(timerId: Long, label: String, endTime: Long) {
        if (!Notifications.canPost(appContext)) return
        Notifications.ensureChannels(appContext)
        manager()?.notify(
            Notifications.RUNNING_ID_BASE + timerId.toInt(),
            Notifications.buildRunningTimer(appContext, timerId, label, endTime)
        )
    }

    override fun showPaused(timerId: Long, label: String, remainingMillis: Long) {
        if (!Notifications.canPost(appContext)) return
        Notifications.ensureChannels(appContext)
        manager()?.notify(
            Notifications.RUNNING_ID_BASE + timerId.toInt(),
            Notifications.buildPausedTimer(appContext, timerId, label, remainingMillis)
        )
    }

    override fun cancel(timerId: Long) {
        Notifications.cancelTimerNotification(appContext, timerId)
    }

    private fun manager(): NotificationManager? =
        appContext.getSystemService(NotificationManager::class.java)
}
