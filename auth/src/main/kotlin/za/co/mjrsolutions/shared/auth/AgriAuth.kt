package za.co.mjrsolutions.shared.auth

import android.content.Context
import androidx.fragment.app.FragmentActivity
import za.co.mjrsolutions.shared.permissions.BiometricHelper
import za.co.mjrsolutions.shared.permissions.BiometricStatus

object AgriAuth {

    @Volatile private var configRef: AuthConfig? = null

    private val config: AuthConfig
        get() = configRef ?: error(
            "AgriAuth.init(...) was not called. Call from your Application.onCreate."
        )

    fun init(config: AuthConfig) {
        configRef = config
    }

    fun initStores(context: Context) {
        TokenStore.init(context)
        OfflineAuthStore.init(context)
        BiometricCredentialStore.init(context)
        LoginClient.init(context)
    }

    fun login(username: String, password: String, callback: AuthCallback) {
        val cfg = config

        val deviceToken: String? = try { cfg.fcmTokenProvider() } catch (_: Exception) { null }

        LoginClient.login(
            baseUrl = cfg.baseUrl,
            username = username,
            password = password,
            applicationId = cfg.applicationId,
            appVersion = cfg.appVersion,
            deviceToken = deviceToken,
            onSuccess = { response ->
                val user = response.toAuthUser(username)
                TokenStore.saveToken(user.apiToken)
                TokenStore.saveUser(user)
                Thread { OfflineAuthStore.upsertUser(user, password) }.start()
                CrashlyticsKeys.setForUser(user, cfg, offlineLogin = false)
                callback.onSuccess(user)
            },
            onFailure = { statusCode, message ->
                if (statusCode != null && statusCode < 500) {
                    // The server reached a verdict on these credentials. Falling back
                    // to the offline store here would let revoked or changed passwords
                    // keep working offline indefinitely — and it masked the real error
                    // ("Offline login failed" instead of invalid credentials).
                    callback.onFailure(AuthError(AuthErrorType.INVALID_CREDENTIALS, message))
                } else {
                    // No HTTP verdict (no signal, DNS, timeout) or server outage (5xx):
                    // the field-assessor case the offline store exists for.
                    attemptOfflineLogin(username, password, callback)
                }
            }
        )
    }

    @JvmOverloads
    fun loginWithBiometrics(
        activity: FragmentActivity,
        callback: AuthCallback,
        title: String = "Login",
        subtitle: String = "Verify your identity",
        negativeButtonText: String = "Cancel"
    ) {
        if (!hasSavedCredentials()) {
            callback.onFailure(AuthError(AuthErrorType.BIOMETRIC_NOT_AVAILABLE, "No saved credentials"))
            return
        }
        if (!isBiometricAvailable(activity)) {
            callback.onFailure(AuthError(AuthErrorType.BIOMETRIC_NOT_AVAILABLE, "Biometric not available"))
            return
        }

        BiometricHelper.prompt(
            activity = activity,
            title = title,
            subtitle = subtitle,
            negativeButtonText = negativeButtonText,
            onSuccess = {
                val username = BiometricCredentialStore.getUsername()!!
                val password = BiometricCredentialStore.getPassword()!!
                login(username, password, callback)
            },
            onFailure = { msg ->
                callback.onFailure(AuthError(AuthErrorType.BIOMETRIC_ERROR, msg))
            }
        )
    }

    @JvmOverloads
    fun enableBiometricRememberMe(
        activity: FragmentActivity,
        username: String,
        password: String,
        callback: BiometricSetupCallback,
        title: String = "Enable quick login",
        subtitle: String = "Verify your identity to save credentials",
        negativeButtonText: String = "Cancel"
    ) {
        if (!isBiometricAvailable(activity)) {
            callback.onFailure(AuthError(AuthErrorType.BIOMETRIC_NOT_AVAILABLE, "Biometric not available"))
            return
        }

        BiometricHelper.prompt(
            activity = activity,
            title = title,
            subtitle = subtitle,
            negativeButtonText = negativeButtonText,
            onSuccess = {
                BiometricCredentialStore.saveCredentials(username, password)
                callback.onSuccess()
            },
            onFailure = { msg ->
                callback.onFailure(AuthError(AuthErrorType.BIOMETRIC_ERROR, msg))
            }
        )
    }

    fun hasSavedCredentials(): Boolean = BiometricCredentialStore.hasSavedCredentials()

    /** Returns the most recently-used username, preferring biometric-saved over offline DB. */
    fun getLastUsername(): String? =
        BiometricCredentialStore.getUsername() ?: OfflineAuthStore.getLastUsername()

    /**
     * One-time migration: seed an existing local credential into the offline store so an
     * upgrading user can log in offline before their first online login on the new build.
     * Idempotent — only seeds when absent. Use [seedOfflineFromHash] when the legacy store
     * holds a BCrypt hash (MR); [seedOfflineFromPlaintext] when it holds the plaintext
     * password (hael/vrugte/Brand). Returns true if a row was seeded.
     */
    fun seedOfflineFromPlaintext(user: AuthUser, plainPassword: String): Boolean =
        OfflineAuthStore.seedWithPlaintext(user, plainPassword)

    fun seedOfflineFromHash(user: AuthUser, bcryptHash: String): Boolean =
        OfflineAuthStore.seedWithHash(user, bcryptHash)

    fun isBiometricAvailable(context: Context): Boolean = BiometricHelper.isBiometricAvailable(context)

    fun checkBiometricStatus(context: Context): BiometricStatus =
        BiometricHelper.checkBiometricStatus(context)

    fun shouldShowBiometricOnboarding(context: Context): Boolean {
        return isBiometricAvailable(context) &&
            !hasSavedCredentials() &&
            !BiometricCredentialStore.isOnboardingSeen()
    }

    fun markBiometricOnboardingSeen() {
        BiometricCredentialStore.markOnboardingSeen()
    }

    fun getToken(): String? = TokenStore.getToken()

    fun getAuthHeaders(): Map<String, String> = TokenStore.getAuthHeaders()

    fun isLoggedIn(): Boolean = TokenStore.getToken() != null

    fun getCurrentUser(): AuthUser? = TokenStore.getUser()

    @JvmOverloads
    fun logout(clearBiometrics: Boolean = false) {
        TokenStore.clear()
        if (clearBiometrics) {
            BiometricCredentialStore.clear()
        }
        CrashlyticsKeys.clear()
    }

    private fun attemptOfflineLogin(username: String, password: String, callback: AuthCallback) {
        val cfg = config
        Thread {
            val user = OfflineAuthStore.verifyOffline(username, password)
            if (user != null) {
                TokenStore.saveUser(user)
                CrashlyticsKeys.setForUser(user, cfg, offlineLogin = true)
                callback.onOfflineSuccess(user)
            } else {
                callback.onFailure(AuthError(AuthErrorType.OFFLINE_NOT_AVAILABLE, "Offline login failed"))
            }
        }.start()
    }
}
