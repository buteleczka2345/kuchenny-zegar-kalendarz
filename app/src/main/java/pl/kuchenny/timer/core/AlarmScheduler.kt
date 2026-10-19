package pl.kuchenny.timer.core

/**
 * Planowanie alarmów systemowych.
 *
 * Każdy timer ma własny alarm, więc identyfikator jest częścią adresu
 * `PendingIntent` — inaczej jeden alarm zastępowałby drugi.
 */
interface AlarmScheduler {
    fun scheduleTimer(timerId: Long, endTime: Long)
    fun cancelTimer(timerId: Long)
}
