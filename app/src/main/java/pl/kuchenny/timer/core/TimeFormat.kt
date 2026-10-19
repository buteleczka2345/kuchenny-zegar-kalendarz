package pl.kuchenny.timer.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Formatowanie czasu w jednym miejscu — ekran i powiadomienia liczą tak samo. */
object TimeFormat {

    fun clock(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val calendar = calendarAt(nowMillis, timeZone)
        return clock(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    fun clock(hour: Int, minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

    fun finishTime(
        nowMillis: Long,
        remainingMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = clock(nowMillis + remainingMillis.coerceAtLeast(0L), timeZone)

    /** Czas timera: `15:00`, `00:30` albo `01:05:00` przy ponad godzinie. */
    fun remaining(millis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0L))
        return if (seconds < TimeUnit.HOURS.toSeconds(1)) {
            String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
        } else {
            String.format(
                Locale.getDefault(),
                "%02d:%02d:%02d",
                seconds / TimeUnit.HOURS.toSeconds(1),
                (seconds / 60) % 60,
                seconds % 60
            )
        }
    }

    private fun calendarAt(millis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }
}
