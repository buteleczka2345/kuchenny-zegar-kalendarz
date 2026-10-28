package pl.kuchenny.timer.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Przypomnienie z kalendarza: o [at] gra nagranie głosowe jak dzwonek timera. */
data class Reminder(val id: Long, val at: Long)

/** Zapis listy przypomnień do jednego ciągu JSON. */
object ReminderCodec {

    private const val KEY_ID = "id"
    private const val KEY_AT = "at"

    fun encode(reminders: List<Reminder>): String {
        val array = JSONArray()
        reminders.forEach { array.put(JSONObject().put(KEY_ID, it.id).put(KEY_AT, it.at)) }
        return array.toString()
    }

    /** Uszkodzony albo pusty zapis daje pustą listę. */
    fun decode(raw: String?): List<Reminder> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { array.optJSONObject(it) }
                .map { Reminder(it.optLong(KEY_ID, 0L), it.optLong(KEY_AT, 0L)) }
                .filter { it.id > 0L && it.at > 0L }
                .distinctBy { it.id }
                .sortedBy { it.at }
        } catch (exception: JSONException) {
            emptyList()
        }
    }
}
