package pl.kuchenny.budzik.ui

/** Stan uprawnień systemowych potrzebnych budzikowi. */
data class PermissionUiState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean
)
