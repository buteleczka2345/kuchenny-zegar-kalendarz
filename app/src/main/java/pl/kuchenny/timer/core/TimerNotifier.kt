package pl.kuchenny.timer.core

/**
 * Powiadomienia o timerach działających w tle.
 *
 * Każdy uruchomiony timer ma własne powiadomienie z odliczaniem, tak jak
 * w aplikacji wielotimerowej.
 */
interface TimerNotifier {
    fun showRunning(timerId: Long, label: String, endTime: Long)
    fun showPaused(timerId: Long, label: String, remainingMillis: Long)
    fun cancel(timerId: Long)
}
