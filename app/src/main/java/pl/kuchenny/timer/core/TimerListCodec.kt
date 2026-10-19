package pl.kuchenny.timer.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Zapis listy timerów do jednego ciągu JSON.
 *
 * Wydzielone z magazynu preferencji, żeby dało się sprawdzić testem bez Androida:
 * to tutaj decyduje się, czy po restarcie telefonu timer wróci z tym samym stanem.
 */
object TimerListCodec {

    private const val KEY_ACTIVE = "activeId"
    private const val KEY_TIMERS = "timers"
    private const val KEY_ID = "id"
    private const val KEY_LABEL = "label"
    private const val KEY_PRESET = "preset"
    private const val KEY_STATE = "state"
    private const val KEY_END = "end"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_TOTAL = "total"
    private const val KEY_SINCE = "since"

    private const val STATE_IDLE = "idle"
    private const val STATE_RUNNING = "running"
    private const val STATE_PAUSED = "paused"
    private const val STATE_RINGING = "ringing"

    fun encode(snapshot: TimersSnapshot): String {
        val root = JSONObject()
        root.put(KEY_ACTIVE, snapshot.activeId)
        val timers = JSONArray()
        snapshot.timers.forEach { item ->
            val json = JSONObject()
            json.put(KEY_ID, item.id)
            json.put(KEY_LABEL, item.label)
            json.put(KEY_PRESET, item.presetMillis)
            when (val state = item.state) {
                TimerState.Idle -> json.put(KEY_STATE, STATE_IDLE)
                is TimerState.Running -> {
                    json.put(KEY_STATE, STATE_RUNNING)
                    json.put(KEY_END, state.endTime)
                    json.put(KEY_TOTAL, state.totalMillis)
                }
                is TimerState.Paused -> {
                    json.put(KEY_STATE, STATE_PAUSED)
                    json.put(KEY_REMAINING, state.remainingMillis)
                    json.put(KEY_TOTAL, state.totalMillis)
                }
                is TimerState.Ringing -> {
                    json.put(KEY_STATE, STATE_RINGING)
                    json.put(KEY_SINCE, state.since)
                }
            }
            timers.put(json)
        }
        root.put(KEY_TIMERS, timers)
        return root.toString()
    }

    fun decode(raw: String?): TimersSnapshot? {
        if (raw.isNullOrBlank()) return null
        return try {
            val root = JSONObject(raw)
            val array = root.optJSONArray(KEY_TIMERS) ?: return null
            val timers = ArrayList<TimerItem>(array.length())
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val item = decodeTimer(json) ?: continue
                timers.add(item)
            }
            if (timers.isEmpty()) return null
            TimersSnapshot(activeId = root.optLong(KEY_ACTIVE, timers.first().id), timers = timers)
                .normalized()
        } catch (exception: JSONException) {
            null
        }
    }

    private fun decodeTimer(json: JSONObject): TimerItem? {
        val id = json.optLong(KEY_ID, 0L)
        if (id <= 0L) return null
        val preset = json.optLong(KEY_PRESET, 0L)
        val label = json.optString(KEY_LABEL, "Timer")
        val state = when (json.optString(KEY_STATE, STATE_IDLE)) {
            STATE_RUNNING -> TimerState.Running(
                endTime = json.optLong(KEY_END, 0L),
                totalMillis = json.optLong(KEY_TOTAL, 0L)
            )
            STATE_PAUSED -> TimerState.Paused(
                remainingMillis = json.optLong(KEY_REMAINING, 0L),
                totalMillis = json.optLong(KEY_TOTAL, 0L)
            )
            STATE_RINGING -> TimerState.Ringing(since = json.optLong(KEY_SINCE, 0L))
            else -> TimerState.Idle
        }
        return TimerItem(id = id, label = label, presetMillis = preset, state = state).normalized()
    }
}
