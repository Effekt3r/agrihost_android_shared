package za.co.mjrsolutions.shared.auth

data class AuthConfig(
    val baseUrl: String,
    val applicationId: String,
    val appVersion: String,
    val flavor: String,
    val fcmTokenProvider: () -> String?
)
