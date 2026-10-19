package pl.kuchenny.budzik.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Formatowanie godziny w jednym miejscu. */
object TimeText {

    fun clock(hour: Int, minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

    fun clock(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        return clock(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    /** Ile zostało do alarmu — „za 7 godz. 12 min”. */
    fun until(nowMillis: Long, triggerAt: Long): String {
        val minutes = ((triggerAt - nowMillis) / 60_000L).coerceAtLeast(0L)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours <= 0L -> "za $rest min"
            rest == 0L -> "za $hours godz."
            else -> "za $hours godz. $rest min"
        }
    }

    /** Opis dni alarmu — „codziennie”, „pn–pt”, „pn, śr, pt”. */
    fun days(days: Int): String {
        val mask = days and Days.ALL
        return when (mask) {
            Days.NONE -> "jednorazowo"
            Days.ALL -> "codziennie"
            Days.WORKDAYS -> "pn–pt"
            Days.WEEKEND -> "weekend"
            else -> Days.SHORT_NAMES.indices
                .filter { Days.isOn(mask, it) }
                .joinToString(", ") { Days.SHORT_NAMES[it].lowercase(Locale("pl")) }
        }
    }

    /** Czas narastania — „Bez narastania”, „45 s”, „1 min 15 s”. */
    fun duration(seconds: Int): String {
        if (seconds <= 0) return "Bez narastania"
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            minutes == 0 -> "$rest s"
            rest == 0 -> "$minutes min"
            else -> "$minutes min $rest s"
        }
    }
}
