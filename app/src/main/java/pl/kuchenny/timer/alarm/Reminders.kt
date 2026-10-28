package pl.kuchenny.timer.alarm

import android.content.Context
import pl.kuchenny.timer.Settings
import pl.kuchenny.timer.core.Reminder
import pl.kuchenny.timer.core.ReminderCodec
import java.io.File

/**
 * Lista przypomnień z kalendarza, ich alarmy systemowe i nagrania głosowe.
 *
 * Nagranie przypomnienia to plik `notatki/<id>.m4a`; znika razem z przypomnieniem.
 */
class Reminders(context: Context) {

    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(Settings.FILE_NAME, Context.MODE_PRIVATE)
    private val scheduler = PlatformAlarmScheduler(appContext)

    fun all(): List<Reminder> = ReminderCodec.decode(preferences.getString(KEY_REMINDERS, null))

    fun find(id: Long): Reminder? = all().firstOrNull { it.id == id }

    /** Dodaje przypomnienie na [at] i planuje je. Zwraca jego id. */
    fun add(at: Long): Long {
        val reminders = all()
        val id = (reminders.maxOfOrNull { it.id } ?: 0L) + 1L
        save(reminders + Reminder(id, at))
        scheduler.scheduleReminder(id, at)
        return id
    }

    fun remove(id: Long) {
        scheduler.cancelReminder(id)
        note(id).delete()
        save(all().filterNot { it.id == id })
    }

    /** Po restarcie telefonu: alarmy systemowe trzeba ustawić od nowa. */
    fun rescheduleAll() {
        all().forEach { scheduler.scheduleReminder(it.id, it.at) }
    }

    fun note(id: Long): File = File(dir(), "$id.m4a")

    /** Nagranie w trakcie dodawania — staje się nagraniem przypomnienia po „Zapisz”. */
    fun draft(): File = File(dir(), "szkic.m4a")

    private fun dir(): File = File(appContext.filesDir, "notatki").apply { mkdirs() }

    private fun save(reminders: List<Reminder>) {
        preferences.edit().putString(KEY_REMINDERS, ReminderCodec.encode(reminders)).apply()
    }

    private companion object {
        const val KEY_REMINDERS = "reminders_json"
    }
}
