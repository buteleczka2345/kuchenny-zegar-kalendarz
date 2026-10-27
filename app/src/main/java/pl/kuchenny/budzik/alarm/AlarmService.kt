package pl.kuchenny.budzik.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import pl.kuchenny.budzik.AlarmSettings
import pl.kuchenny.budzik.BudzikGraph
import pl.kuchenny.budzik.core.AlarmController
import pl.kuchenny.budzik.core.Ramp
import pl.kuchenny.budzik.core.Shake
import pl.kuchenny.budzik.core.TimeText

/**
 * Serwis dzwoniącego budzika.
 *
 * Gra zapętloną melodię, stopniowo podgłaśnia do wybranego poziomu, wibruje
 * i nasłuchuje potrząśnięcia oraz odwrócenia telefonu. Dzwoni do zatrzymania.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var settings: AlarmSettings
    private lateinit var player: AlarmPlayer
    private lateinit var controller: AlarmController
    private lateinit var motion: MotionDismiss
    private lateinit var flash: FlashBlinker

    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null
    private var startedAt = 0L
    private var ringing = false

    override fun onCreate() {
        super.onCreate()
        settings = AlarmSettings(this)
        player = AlarmPlayer(this)
        controller = BudzikGraph.controller(this)
        motion = MotionDismiss(
            context = this,
            shakeEnabled = { settings.shakeEnabled },
            shakeThreshold = { Shake.threshold(settings.shakeLevel) },
            flipEnabled = { settings.flipEnabled },
            onDismiss = { stopRinging() }
        )
        flash = FlashBlinker(this)
        AlarmNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        return when (command.action) {
            ACTION_STOP -> {
                stopRinging()
                START_NOT_STICKY
            }
            ACTION_START -> {
                startRinging(command.getLongExtra(EXTRA_ALARM_ID, -1L))
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        motion.stop()
        flash.stop()
        releaseWakeLock()
        ringing = false
        isRinging = false
        super.onDestroy()
    }

    private fun startRinging(alarmId: Long) {
        if (ringing) return
        val alarm = controller.snapshot().find(alarmId)
        // Alarm jednorazowy jest już wyłączony przez AlarmReceiver — i tak dzwoni.
        if (alarm == null) {
            stopSelf()
            return
        }
        ringing = true
        isRinging = true
        startedAt = System.currentTimeMillis()

        val title = "Budzik ${TimeText.clock(alarm.hour, alarm.minute)}"
        startForeground(
            AlarmNotifications.ID_ALARM,
            AlarmNotifications.build(
                this,
                title = title,
                text = "Dotknij, aby otworzyć ekran STOP, lub potrząśnij telefonem."
            )
        )
        // Pełnoekranowe powiadomienie zwykle samo otwiera ekran STOP; to jest zapas,
        // gdy system na to pozwala (np. telefon odblokowany).
        runCatching { startActivity(AlarmStopActivity.intent(this, title)) }

        val rampMillis = settings.rampSeconds * 1_000L
        player.volume = targetVolume(0L, rampMillis)
        val custom = settings.selectedCustomSound
        if (custom != null) {
            player.startSound(custom.uri, settings.melodyStyle)
        } else {
            player.startMelody(settings.melodyStyle)
        }
        player.startVibration(settings.vibrationStrength)
        acquireWakeLock()
        if (settings.shakeEnabled || settings.flipEnabled) {
            motion.start()
        }
        if (settings.flashEnabled) {
            flash.start()
        }
        handler.post(rampTask)
    }

    private val rampTask = object : Runnable {
        override fun run() {
            if (!ringing) return
            val rampMillis = settings.rampSeconds * 1_000L
            val elapsed = System.currentTimeMillis() - startedAt
            player.volume = targetVolume(elapsed, rampMillis)
            if (elapsed < rampMillis) {
                handler.postDelayed(this, RAMP_INTERVAL_MS)
            }
        }
    }

    private fun targetVolume(elapsedMs: Long, rampMillis: Long): Float =
        Ramp.volume(elapsedMs, rampMillis) * (settings.volume / 100f)

    private fun stopRinging() {
        if (!ringing) {
            stopSelf()
            return
        }
        ringing = false
        isRinging = false
        handler.removeCallbacks(rampTask)
        player.stop()
        motion.stop()
        flash.stop()
        releaseWakeLock()
        stopForeground(true)
        stopSelf()
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Budzik:Alarm"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        if (settings.screenOnEnabled) {
            // Przestarzała, ale wciąż działająca droga, by zapalić ekran z serwisu.
            @Suppress("DEPRECATION")
            screenLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Budzik:Screen"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
        screenLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        screenLock = null
    }

    companion object {
        const val ACTION_START = "pl.kuchenny.budzik.START_ALARM"
        const val ACTION_STOP = "pl.kuchenny.budzik.STOP_ALARM"
        const val EXTRA_ALARM_ID = "pl.kuchenny.budzik.extra.ALARM_ID"

        /** Czy budzik właśnie dzwoni — wtedy pykanie o pełnej godzinie milknie. */
        @Volatile
        var isRinging = false
            private set

        private const val RAMP_INTERVAL_MS = 250L
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1_000L

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (exception: RuntimeException) {
                // System mógł odmówić startu w tle — nie wywalamy odbiornika.
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
