package pl.kuchenny.budzik.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Zapis listy alarmów do jednego ciągu JSON. */
object AlarmCodec {

    private const val KEY_ALARMS = "alarms"
    private const val KEY_ID = "id"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DAYS = "days"

    fun encode(snapshot: AlarmsSnapshot): String {
        val root = JSONObject()
        val alarms = JSONArray()
        snapshot.alarms.forEach { alarm ->
            alarms.put(
                JSONObject()
                    .put(KEY_ID, alarm.id)
                    .put(KEY_HOUR, alarm.hour)
                    .put(KEY_MINUTE, alarm.minute)
                    .put(KEY_ENABLED, alarm.enabled)
                    .put(KEY_DAYS, alarm.days)
            )
        }
        root.put(KEY_ALARMS, alarms)
        return root.toString()
    }

    fun decode(raw: String?): AlarmsSnapshot? {
        if (raw.isNullOrBlank()) return null
        return try {
            val array = JSONObject(raw).optJSONArray(KEY_ALARMS) ?: return null
            val alarms = ArrayList<AlarmItem>(array.length())
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optLong(KEY_ID, 0L)
                if (id <= 0L) continue
                alarms.add(
                    AlarmItem(
                        id = id,
                        hour = json.optInt(KEY_HOUR, 7),
                        minute = json.optInt(KEY_MINUTE, 0),
                        enabled = json.optBoolean(KEY_ENABLED, true),
                        // Stare zapisy nie mają dni — dzwoniły codziennie.
                        days = json.optInt(KEY_DAYS, Days.ALL)
                    )
                )
            }
            AlarmsSnapshot(alarms).normalized()
        } catch (exception: JSONException) {
            null
        }
    }
}
