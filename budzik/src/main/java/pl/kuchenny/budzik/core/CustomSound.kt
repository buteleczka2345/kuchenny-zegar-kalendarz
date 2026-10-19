package pl.kuchenny.budzik.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Własny dźwięk wybrany z telefonu: adres pliku i nazwa do wyświetlenia. */
data class CustomSound(val uri: String, val name: String) {

    companion object {
        /** Najwięcej własnych dźwięków na liście. */
        const val MAX_SOUNDS = 10
    }
}

/** Zapis listy własnych dźwięków do jednego ciągu JSON. */
object CustomSoundCodec {

    private const val KEY_URI = "uri"
    private const val KEY_NAME = "name"

    fun encode(sounds: List<CustomSound>): String {
        val array = JSONArray()
        sounds.forEach { sound ->
            array.put(JSONObject().put(KEY_URI, sound.uri).put(KEY_NAME, sound.name))
        }
        return array.toString()
    }

    fun decode(raw: String?): List<CustomSound> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val sounds = ArrayList<CustomSound>(array.length())
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val uri = json.optString(KEY_URI, "")
                if (uri.isBlank()) continue
                val name = json.optString(KEY_NAME, "").ifBlank { "Własny dźwięk" }
                sounds.add(CustomSound(uri, name))
            }
            sounds.distinctBy { it.uri }.take(CustomSound.MAX_SOUNDS)
        } catch (exception: JSONException) {
            emptyList()
        }
    }
}
