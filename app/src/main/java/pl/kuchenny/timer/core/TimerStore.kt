package pl.kuchenny.timer.core

/** Zapis i odczyt listy timerów — jedyne miejsce, które dotyka trwałości. */
interface TimersStore {
    fun load(): TimersSnapshot
    fun save(snapshot: TimersSnapshot)
}
