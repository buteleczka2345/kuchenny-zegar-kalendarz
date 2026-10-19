package pl.kuchenny.budzik.core

/** Planowanie alarmów systemowych — każdy alarm ma własny `PendingIntent`. */
interface AlarmScheduler {
    fun schedule(alarmId: Long, triggerAt: Long)
    fun cancel(alarmId: Long)
}
