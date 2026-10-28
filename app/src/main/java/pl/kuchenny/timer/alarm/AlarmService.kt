package pl.kuchenny.timer.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import pl.kuchenny.timer.Settings
import pl.kuchenny.timer.TimerGraph
import pl.kuchenny.timer.core.TimerController
import pl.kuchenny.timer.core.TimerLimits
import pl.kuchenny.timer.core.TimerState

/**
 * Serwis dzwonienia.
 *
 * Prowadzi jedno dzwonienie naraz i wie, którego timera dotyczy. Gdy po
 * zatrzymaniu czeka kolejny timer, który właśnie się skończył, od razu
 * zaczyna dzwonić jego alarmem — inaczej drugi alarm po prostu ginął.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var settings: Settings
    private lateinit var audio: AlarmAudio
    private lateinit var controller: TimerController
    private lateinit var reminders: Reminders

    private var session: RingSession? = null
    private var finished = false
    private var wakeLock: PowerManager.WakeLock? = null

    private class RingSession(
        val mode: AlarmMode,
        val timerId: Long,
        val label: String,
        val style: Int,
        val frequencies: DoubleArray,
        var customUri: Uri?,
        val bellCount: Int,
        val intervalMs: Long,
        val durationLimitMs: Long,
        val startedAt: Long
    ) {
        var bellsPlayed: Int = 0
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        controller = TimerGraph.controller(this)
        reminders = Reminders(this)
        audio = AlarmAudio(this).apply {
            onPlaybackError = {
                // Wybrany plik jest nieczytelny — dzwonimy dzwonkiem wbudowanym.
                session?.customUri = null
            }
        }
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        val action = command.action ?: return START_NOT_STICKY
        return when (action) {
            ACTION_STOP -> {
                advance(notifyFinished = false)
                START_NOT_STICKY
            }
            ACTION_STOP_TIMER -> {
                handleStopTimer(command)
                START_NOT_STICKY
            }
            ACTION_PREVIEW -> handlePreview()
            ACTION_START -> handleStart(command)
            ACTION_START_REMINDER -> handleStartReminder(command)
            else -> START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (session != null) {
            // Proces ginie w trakcie dzwonienia — nie zostawiamy zawieszonego stanu.
            endSession(notifyFinished = false)
        }
        audio.stop()
        audio.cancelVibration()
        releaseWakeLock()
        super.onDestroy()
    }

    // --- obsługa poleceń ----------------------------------------------------

    private fun handleStart(command: Intent): Int {
        val timerId = command.getLongExtra(EXTRA_TIMER_ID, -1L)
        if (session?.mode == AlarmMode.KITCHEN && session?.timerId == timerId) {
            // To samo dzwonienie już trwa — nie przerywamy go.
            return START_NOT_STICKY
        }
        endSession(notifyFinished = false)
        finished = false
        startRinging(AlarmMode.KITCHEN, timerId)
        return START_NOT_STICKY
    }

    private fun handleStartReminder(command: Intent): Int {
        val reminderId = command.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminders.find(reminderId) == null) {
            // Przypomnienie usunięte przed czasem. Gdy nic nie gra, domykamy startForeground i kończymy.
            if (session == null) {
                startForeground(
                    Notifications.ID_ALARM,
                    Notifications.buildAlarm(this, "Przypomnienie", "Przypomnienie zostało usunięte.")
                )
                stopForeground(true)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        endSession(notifyFinished = false)
        finished = false
        startRinging(AlarmMode.REMINDER, reminderId)
        return START_NOT_STICKY
    }

    private fun handleStopTimer(command: Intent) {
        val timerId = command.getLongExtra(EXTRA_TIMER_ID, -1L)
        if (timerId > 0L) controller.stopTimer(timerId)
        when {
            session == null -> stopSelf()
            session?.mode == AlarmMode.KITCHEN && session?.timerId == timerId -> advance(notifyFinished = false)
            // Dzwoni inny timer — nie wolno go uciszać.
        }
    }

    private fun handlePreview(): Int {
        if (session != null) {
            // Dzwonek już gra — podglądu nie uruchamiamy i niczego nie zatrzymujemy.
            return START_NOT_STICKY
        }
        val anyRinging = controller.snapshot().timers.any { it.state is TimerState.Ringing }
        if (anyRinging) {
            // Stan mówi o dzwonieniu, ale serwis nie gra: domykamy obowiązek
            // startForeground i od razu zdejmujemy powiadomienie.
            startForeground(
                Notifications.ID_ALARM,
                Notifications.buildAlarm(this, "Podgląd dzwonka", "Alarm już dzwoni.")
            )
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        startRinging(AlarmMode.PREVIEW, timerId = -1L)
        return START_NOT_STICKY
    }

    // --- jedno dzwonienie ---------------------------------------------------

    private fun startRinging(mode: AlarmMode, timerId: Long) {
        val style = settings.soundStyle
        val note = reminders.note(timerId)
        val customUri = if (mode == AlarmMode.REMINDER) {
            // Przypomnienie gra nagrany głos; bez nagrania — wbudowany dzwonek.
            note.takeIf { it.exists() }?.let(Uri::fromFile)
        } else {
            settings.customSoundUri?.let(Uri::parse)
        }
        val label = when (mode) {
            AlarmMode.PREVIEW -> ""
            AlarmMode.REMINDER -> "Przypomnienie"
            AlarmMode.KITCHEN -> controller.snapshot().find(timerId)?.label ?: "Timer"
        }
        val bellCount = if (mode.isPreview) 1 else settings.bellCount
        // Dzwonek gra maksymalnie minutę; wcześniej kończy go wybrana liczba powtórzeń.
        val durationLimitMs = if (mode.isPreview) PREVIEW_LIMIT_MS else RING_LIMIT_MS

        val current = RingSession(
            mode = mode,
            timerId = timerId,
            label = label,
            style = style,
            frequencies = AlarmSounds.frequencies(style),
            customUri = customUri,
            bellCount = bellCount,
            intervalMs = BELL_INTERVAL_MS,
            durationLimitMs = durationLimitMs,
            startedAt = System.currentTimeMillis()
        )
        session = current
        audio.volume = settings.alarmVolume / 100f
        audio.vibrationAmplitude = settings.vibrationStrength * 255 / TimerLimits.MAX_VIBRATION_STRENGTH

        startForeground(
            Notifications.ID_ALARM,
            Notifications.buildAlarm(
                this,
                title = when (mode) {
                    AlarmMode.PREVIEW -> "Podgląd dzwonka"
                    AlarmMode.REMINDER -> "Przypomnienie"
                    AlarmMode.KITCHEN -> "Czas minął!"
                },
                message = when (mode) {
                    AlarmMode.PREVIEW -> "Podgląd: ${soundName(style)}"
                    AlarmMode.REMINDER -> "Twoje nagranie"
                    AlarmMode.KITCHEN -> "$label · ${soundName(style)}"
                }
            )
        )
        acquireWakeLock(durationLimitMs + WAKE_LOCK_MARGIN_MS)
        handler.post(ringTask)
    }

    private val ringTask = object : Runnable {
        override fun run() {
            val current = session ?: return
            if (finished) return
            val now = System.currentTimeMillis()

            if (now - current.startedAt >= current.durationLimitMs) {
                advance(notifyFinished = true)
                return
            }
            if (audio.isPlaying) {
                handler.postDelayed(this, RING_POLL_MS)
                return
            }
            if (current.bellsPlayed >= current.bellCount) {
                advance(notifyFinished = true)
                return
            }

            current.bellsPlayed++
            val uri = current.customUri
            if (uri != null) {
                audio.playFile(uri = uri, looping = false)
                handler.postDelayed(this, RING_POLL_MS)
            } else {
                audio.playBell(
                    frequencies = current.frequencies,
                    decayPerSecond = BELL_DECAY
                )
                handler.postDelayed(this, current.intervalMs)
            }
        }
    }

    /**
     * Kończy bieżące dzwonienie. Jeśli inny timer też już się skończył,
     * od razu zaczyna dzwonić jego alarmem.
     */
    private fun advance(notifyFinished: Boolean) {
        val current = session
        if (current == null) {
            stopSelf()
            return
        }
        val next = if (current.mode.isRealAlarm) {
            controller.nextRinging(excludeId = current.timerId)
        } else {
            null
        }
        endSession(notifyFinished)
        if (next != null) {
            finished = false
            startRinging(AlarmMode.KITCHEN, next.id)
        } else {
            finished = true
            stopForeground(true)
            stopSelf()
        }
    }

    private fun endSession(notifyFinished: Boolean) {
        val current = session ?: return
        session = null
        handler.removeCallbacks(ringTask)
        audio.stop()
        audio.cancelVibration()
        releaseWakeLock()
        when (current.mode) {
            AlarmMode.KITCHEN -> {
                controller.onRingFinished(current.timerId)
                if (notifyFinished) Notifications.showFinished(this, current.label)
            }
            // Przypomnienie zagrało: znika z listy razem z nagraniem.
            AlarmMode.REMINDER -> reminders.remove(current.timerId)
            AlarmMode.PREVIEW -> Unit
        }
    }

    // --- pomocnicze ---------------------------------------------------------

    private fun soundName(style: Int): String =
        Settings.SOUND_NAMES[style.coerceIn(0, Settings.SOUND_NAMES.lastIndex)]

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KuchennyZegar:Alarm").apply {
            setReferenceCounted(false)
            acquire(timeoutMs.coerceAtLeast(1_000L))
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "pl.kuchenny.timer.START_ALARM"
        const val ACTION_STOP = "pl.kuchenny.timer.STOP_ALARM"
        const val ACTION_STOP_TIMER = "pl.kuchenny.timer.STOP_TIMER"
        const val ACTION_PREVIEW = "pl.kuchenny.timer.PREVIEW_ALARM"
        const val ACTION_START_REMINDER = "pl.kuchenny.timer.START_REMINDER"

        const val EXTRA_TIMER_ID = "pl.kuchenny.timer.extra.TIMER_ID"
        const val EXTRA_REMINDER_ID = "pl.kuchenny.timer.extra.REMINDER_ID"

        private const val BELL_INTERVAL_MS = 850L
        private const val BELL_DECAY = 5.3
        private const val RING_POLL_MS = 250L
        private const val PREVIEW_LIMIT_MS = 15_000L
        private const val RING_LIMIT_MS = 60_000L
        private const val WAKE_LOCK_MARGIN_MS = 15_000L

        fun startTimer(context: Context, timerId: Long) {
            deliver(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_TIMER_ID, timerId)
            )
        }

        fun startReminder(context: Context, reminderId: Long) {
            deliver(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_START_REMINDER)
                    .putExtra(EXTRA_REMINDER_ID, reminderId)
            )
        }

        /** Ucisza dzwoniący alarm (nie dotyka odliczających timerów). */
        fun stopRinging(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java).setAction(ACTION_STOP)
                )
            }
        }

        /** Wywoływane z powiadomienia timera — zatrzymuje wskazany timer. */
        fun stopTimer(context: Context, timerId: Long) {
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java)
                        .setAction(ACTION_STOP_TIMER)
                        .putExtra(EXTRA_TIMER_ID, timerId)
                )
            }
        }

        fun preview(context: Context) {
            deliver(
                context,
                Intent(context, AlarmService::class.java).setAction(ACTION_PREVIEW)
            )
        }

        private fun deliver(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (exception: RuntimeException) {
                // System mógł odmówić startu w tle — nie wywalamy odbiornika.
                // Stan timera wyczyści najbliższe uzgodnienie po powrocie do aplikacji.
            }
        }
    }
}
