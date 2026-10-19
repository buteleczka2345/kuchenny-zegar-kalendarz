package pl.kuchenny.budzik.core

/** Siła potrząśnięcia potrzebna do wyłączenia budzika. */
object Shake {

    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 10

    /** Poziom 4 to dotychczasowy próg 11 m/s². */
    const val DEFAULT_LEVEL = 4

    /** Próg przyspieszenia w m/s²: od lekkiego ruchu (6,5) do mocnego machnięcia (20). */
    fun threshold(level: Int): Float = 5f + level.coerceIn(MIN_LEVEL, MAX_LEVEL) * 1.5f

    /**
     * Najwyższy poziom, przy którym ruch o sile [acceleration] wyłączyłby alarm;
     * 0, gdy ruch jest słabszy niż najlżejszy poziom.
     */
    fun levelFor(acceleration: Float): Int {
        var level = 0
        for (candidate in MIN_LEVEL..MAX_LEVEL) {
            if (acceleration >= threshold(candidate)) level = candidate
        }
        return level
    }

    /**
     * Wynik sprawdzania: najmocniejsze potrząśnięcie [peakLevel] wobec
     * ustawionej siły [setting].
     */
    fun testResult(peakLevel: Int, setting: Int): String {
        if (peakLevel <= 0) return "Potrząśnij telefonem tak, jak rano w łóżku…"
        val shown = "Twoje potrząśnięcie: $peakLevel / $MAX_LEVEL"
        return if (peakLevel >= setting.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
            "$shown — alarm by się wyłączył ✓"
        } else {
            "$shown — za słabo, alarm dalej by dzwonił. Przesuń suwak niżej albo potrząśnij mocniej."
        }
    }

    /** Opis poziomu na ekranie — „4 / 10 · średnio”. */
    fun describe(level: Int): String {
        val safe = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        val word = when {
            safe <= 2 -> "bardzo lekko"
            safe <= 4 -> "lekko"
            safe <= 6 -> "średnio"
            safe <= 8 -> "mocno"
            else -> "bardzo mocno"
        }
        return "$safe / $MAX_LEVEL · $word"
    }
}
