package pl.kuchenny.timer.alarm

/** Rodzaj dzwonienia: prawdziwy alarm timera albo krótki podgląd dzwonka. */
enum class AlarmMode(val isPreview: Boolean) {
    KITCHEN(isPreview = false),
    PREVIEW(isPreview = true);

    val isRealAlarm: Boolean
        get() = !isPreview
}
