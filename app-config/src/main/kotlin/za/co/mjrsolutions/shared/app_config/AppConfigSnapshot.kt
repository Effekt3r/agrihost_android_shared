package za.co.mjrsolutions.shared.app_config

data class AppConfigSnapshot(
    val currentVersion: String,
    val minVersion: String?,
    val maintenanceMessage: String?,
    val updateRequired: Boolean,
    val forceUpdate: Boolean,
    val extras: Map<String, Any?>
)
