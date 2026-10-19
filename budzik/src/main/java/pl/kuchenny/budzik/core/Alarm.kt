package pl.kuchenny.budzik.core

/**
 * Jeden alarm: godzina, minuta, dni tygodnia i to, czy jest włączony.
 *
 * [days] to maska dni ([Days]); 0 oznacza alarm jednorazowy.
 */
data class AlarmItem(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val days: Int = Days.ALL
) {
    val minutesOfDay: Int
        get() = hour * 60 + minute

    val oneShot: Boolean
        get() = days == Days.NONE

    fun normalized(): AlarmItem = copy(
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        days = days and Days.ALL
    )
}

/**
 * Dni tygodnia jako maska bitowa: bit 0 = poniedziałek … bit 6 = niedziela.
 */
object Days {
    const val NONE = 0
    const val ALL = 0b1111111
    const val WORKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    val SHORT_NAMES = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "Sb", "Nd")

    fun isOn(days: Int, dayIndex: Int): Boolean = (days and (1 shl dayIndex)) != 0

    fun toggle(days: Int, dayIndex: Int): Int = days xor (1 shl dayIndex)
}

/** Cała lista alarmów. */
data class AlarmsSnapshot(val alarms: List<AlarmItem>) {

    fun find(id: Long): AlarmItem? = alarms.firstOrNull { it.id == id }

    fun replace(item: AlarmItem): AlarmsSnapshot =
        copy(alarms = alarms.map { if (it.id == item.id) item else it })

    fun without(id: Long): AlarmsSnapshot =
        copy(alarms = alarms.filterNot { it.id == id })

    fun enabled(): List<AlarmItem> = alarms.filter { it.enabled }

    fun normalized(): AlarmsSnapshot = copy(
        alarms = alarms.map { it.normalized() }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.minutesOfDay }, { it.id }))
    )

    companion object {
        val EMPTY = AlarmsSnapshot(emptyList())
        const val MAX_ALARMS = 8
    }
}
