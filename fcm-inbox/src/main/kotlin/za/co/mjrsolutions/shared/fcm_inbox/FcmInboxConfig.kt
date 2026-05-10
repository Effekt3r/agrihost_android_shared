package za.co.mjrsolutions.shared.fcm_inbox

data class FcmInboxConfig(
    val app: String,                 // "agrihost_hael" | "agrihost_mr" | "agrihost_vrugte"
    val flavor: String,              // BuildConfig.FLAVOR
    val applicationId: String,       // BuildConfig.APPLICATION_ID
    val appVersion: String,          // BuildConfig.VERSION_NAME
    val usernameProvider: () -> String?
)
