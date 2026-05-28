package za.co.mjrsolutions.shared.app_config

data class AppConfigInit(
    val product: String,         // "Hael", "MR", "Vrugte", "Bransito" etc.
    val bundleId: String,        // BuildConfig.APPLICATION_ID
    val currentVersion: String   // BuildConfig.VERSION_NAME
)
