package pl.kuchenny.budzik.core

import java.util.Calendar
import java.util.TimeZone

/** Pykanie o pełnej godzinie: w których godzinach pyka i kiedy następne. */
object HourlyChime {

    const val DEFAULT_FROM = 8
    const val DEFAULT_TO = 22

    /**
     * Czy o godzinie [hour] pyka. Zakres [from]–[to] obejmuje obie granice;
     * gdy [from] jest większe od [to], przechodzi przez północ (np. 22–6).
     */
    fun isActive(hour: Int, from: Int, to: Int): Boolean {
        val h = hour.coerceIn(0, 23)
        val start = from.coerceIn(0, 23)
        val end = to.coerceIn(0, 23)
        return if (start <= end) h in start..end else h >= start || h <= end
    }

    /**
     * Najbliższa pełna godzina po [nowMillis], o której pyka.
     * Dokładnie o pełnej godzinie liczy się już następna.
     */
    fun next(
        nowMillis: Long,
        from: Int,
        to: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        }
        // Zmiana czasu może dać dobę z 25 godzinami — sprawdzamy z zapasem.
        repeat(26) {
            if (isActive(calendar.get(Calendar.HOUR_OF_DAY), from, to)) return calendar.timeInMillis
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        }
        return calendar.timeInMillis
    }

    /** Godzina (0–23) chwili [millis]. */
    fun hourOf(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Int =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
}
