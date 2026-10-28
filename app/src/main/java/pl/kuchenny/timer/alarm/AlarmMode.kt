package pl.kuchenny.timer.alarm

/** Rodzaj dzwonienia: alarm timera, przypomnienie z kalendarza albo krótki podgląd dzwonka. */
enum class AlarmMode(val isPreview: Boolean) {
    KITCHEN(isPreview = false),
    REMINDER(isPreview = false),
    PREVIEW(isPreview = true);

    val isRealAlarm: Boolean
        get() = !isPreview
}
