package za.co.mjrsolutions.shared.auth

data class AuthUser(
    val serverUserId: String,
    val username: String,
    val name: String,
    val surname: String,
    val email: String?,
    val apiToken: String,
    val specialInstructions: String?,
    val languageCode: String?,
    val languageVersion: String?,
    val serverTime: String?
)

data class AuthError(
    val type: AuthErrorType,
    val message: String
)

enum class AuthErrorType {
    INVALID_CREDENTIALS,
    NETWORK_ERROR,
    SERVER_ERROR,
    OFFLINE_NOT_AVAILABLE,
    BIOMETRIC_ERROR,
    BIOMETRIC_NOT_AVAILABLE,
    NOT_INITIALIZED
}

interface AuthCallback {
    fun onSuccess(user: AuthUser)
    fun onOfflineSuccess(user: AuthUser)
    fun onFailure(error: AuthError)
}

interface BiometricSetupCallback {
    fun onSuccess()
    fun onFailure(error: AuthError)
}
