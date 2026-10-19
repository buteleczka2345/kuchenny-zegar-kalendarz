package pl.kuchenny.budzik.core

/** Narastanie głośności: od ciszy do pełnej głośności w zadanym czasie. */
object Ramp {

    /** Najdłuższe narastanie: dwie minuty. */
    const val MAX_SECONDS = 120

    /** Krok suwaka narastania w sekundach. */
    const val STEP_SECONDS = 5

    /**
     * Ucho słyszy głośność logarytmicznie: przy liniowym wzroście już 10%
     * mocy brzmi prawie jak pełna. Krzywa sześcienna zaczyna naprawdę cicho
     * i narasta słyszalnie przez cały czas.
     */
    fun volume(elapsedMs: Long, rampMillis: Long): Float {
        if (rampMillis <= 0L) return 1f
        val progress = (elapsedMs.toFloat() / rampMillis).coerceIn(0f, 1f)
        return progress * progress * progress
    }
}
