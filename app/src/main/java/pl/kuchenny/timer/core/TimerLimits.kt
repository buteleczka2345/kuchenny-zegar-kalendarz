package pl.kuchenny.timer.core

/**
 * Jedno miejsce z liczbowymi ograniczeniami aplikacji.
 */
object TimerLimits {
    const val MAX_HOURS = 23
    const val MAX_MINUTES = MAX_HOURS * 60 + 59

    /** Najdłuższy ustawiany czas: 23:59. */
    const val MAX_PRESET_MILLIS = MAX_MINUTES * 60_000L

    /** Najkrótszy sensowny czas uruchomienia timera. */
    const val MIN_RUNNABLE_MILLIS = 1_000L

    /** Ile timerów jednocześnie mieści pasek u góry. */
    const val MAX_TIMERS = 10

    const val MIN_BELL_COUNT = 1
    const val MAX_BELL_COUNT = 20

    const val MAX_VIBRATION_STRENGTH = 10
}
