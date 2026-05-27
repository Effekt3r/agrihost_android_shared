package za.co.mjrsolutions.shared.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

internal object TokenStore {
    private const val PREFS_NAME = "agriauth_token"
    private const val KEY_TOKEN = "api_token"
    private const val API_KEY = "t4LywO234234TDDSZctwMT1uuR234IL6fPjfOO234cVIf5aWNGwjgksdai"

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedUser: AuthUser? = null

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
        cachedToken = prefs?.getString(KEY_TOKEN, null)
    }

    fun saveToken(token: String) {
        cachedToken = token
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    fun getToken(): String? = cachedToken

    fun saveUser(user: AuthUser) {
        cachedUser = user
    }

    fun getUser(): AuthUser? = cachedUser

    fun clear() {
        cachedToken = null
        cachedUser = null
        prefs?.edit()?.clear()?.apply()
    }

    fun getAuthHeaders(): Map<String, String> = getAuthHeaders(cachedToken)

    internal fun getAuthHeaders(token: String?): Map<String, String> {
        val headers = mutableMapOf(
            "x-api-key" to API_KEY,
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )
        if (token != null) {
            headers["Authorization"] = "Bearer $token"
        }
        return headers
    }
}
