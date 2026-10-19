package pl.kuchenny.budzik.core

import java.util.Calendar
import java.util.TimeZone

/** Wyliczanie najbliższego dzwonienia alarmu. */
object NextTrigger {

    /**
     * Najbliższy moment po [nowMillis] o godzinie [hour]:[minute].
     * Gdy ta godzina już dziś minęła, wypada następnego dnia.
     */
    fun next(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = next(nowMillis, hour, minute, Days.ALL, timeZone)

    /**
     * Najbliższy moment po [nowMillis] o godzinie [hour]:[minute] w jeden
     * z dni [days]. Bez dni (alarm jednorazowy) — najbliższa taka godzina.
     */
    fun next(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        days: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        val mask = days and Days.ALL
        if (mask == Days.NONE || mask == Days.ALL) return calendar.timeInMillis
        repeat(7) {
            if (Days.isOn(mask, dayIndex(calendar))) return calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    /** Indeks dnia w [Days]: poniedziałek = 0 … niedziela = 6. */
    private fun dayIndex(calendar: Calendar): Int =
        (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
}
