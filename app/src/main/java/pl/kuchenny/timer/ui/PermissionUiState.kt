package pl.kuchenny.timer.ui

/** Stan uprawnień systemowych pokazywany na ekranie ustawień. */
data class PermissionUiState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
    val dndAccess: Boolean,
    val alarmsDuringDnd: Boolean
)
