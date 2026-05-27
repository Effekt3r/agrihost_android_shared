package za.co.mjrsolutions.shared.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

internal object BiometricCredentialStore {
    private const val PREFS_NAME = "agriauth_biometric"
    private const val KEY_USERNAME = "bio_username"
    private const val KEY_PASSWORD = "bio_password"
    private const val KEY_ONBOARDING_SEEN = "bio_onboarding_seen"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredentials(username: String, password: String) {
        prefs?.edit()
            ?.putString(KEY_USERNAME, username)
            ?.putString(KEY_PASSWORD, password)
            ?.apply()
    }

    fun getUsername(): String? = prefs?.getString(KEY_USERNAME, null)
    fun getPassword(): String? = prefs?.getString(KEY_PASSWORD, null)

    fun hasSavedCredentials(): Boolean {
        return getUsername() != null && getPassword() != null
    }

    fun isOnboardingSeen(): Boolean {
        return prefs?.getBoolean(KEY_ONBOARDING_SEEN, false) ?: false
    }

    fun markOnboardingSeen() {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_SEEN, true)?.apply()
    }

    /**
     * Clears saved credentials only. The onboarding-seen flag is intentionally preserved —
     * once a user has seen the biometric onboarding prompt, they should not see it again.
     */
    fun clear() {
        prefs?.edit()
            ?.remove(KEY_USERNAME)
            ?.remove(KEY_PASSWORD)
            ?.apply()
    }
}
