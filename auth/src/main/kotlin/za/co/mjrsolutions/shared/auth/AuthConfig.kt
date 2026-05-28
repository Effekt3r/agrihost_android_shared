package za.co.mjrsolutions.shared.auth

data class AuthConfig(
    val baseUrl: String,
    val applicationId: String,
    val appVersion: String,
    val flavor: String,
    val fcmTokenProvider: () -> String?,
    /**
     * Product / app identifier — one of "Hael", "MR", "Brand", "Vrugte".
     * Used for Crashlytics tagging so reports can be filtered by app.
     */
    val product: String = ""
)
