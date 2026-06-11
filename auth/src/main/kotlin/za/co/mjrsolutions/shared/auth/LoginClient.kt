package za.co.mjrsolutions.shared.auth

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

internal object LoginClient {

    private var queue: RequestQueue? = null

    fun init(context: Context) {
        queue = Volley.newRequestQueue(context.applicationContext, TlsCompat.createHttpStack())
    }

    fun login(
        baseUrl: String,
        username: String,
        password: String,
        applicationId: String,
        appVersion: String,
        deviceToken: String?,
        onSuccess: (LoginResponse) -> Unit,
        /** statusCode is null when the request never produced an HTTP response
         *  (no connectivity, DNS, timeout) — the only case offline fallback is for. */
        onFailure: (statusCode: Int?, message: String) -> Unit
    ) {
        val q = queue ?: run {
            onFailure(null, "LoginClient not initialized")
            return
        }
        val url = "${baseUrl.trimEnd('/')}/login"
        val body = Gson().toJson(
            LoginRequest(username, password, applicationId, appVersion, deviceToken ?: "")
        )

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val parsed = Gson().fromJson(response, LoginResponse::class.java)
                    if (parsed?.apiToken != null) {
                        onSuccess(parsed)
                    } else {
                        onFailure(200, parsed?.message ?: "Login failed — no token in response")
                    }
                } catch (e: Exception) {
                    onFailure(200, "Failed to parse login response: ${e.message}")
                }
            },
            { error ->
                val status = error.networkResponse?.statusCode
                // Prefer the server's own message body (e.g. invalid credentials)
                // over a bare status code.
                val serverMsg = error.networkResponse?.data?.let {
                    try {
                        Gson().fromJson(String(it, Charsets.UTF_8), LoginResponse::class.java)?.message
                    } catch (_: Exception) {
                        null
                    }
                }
                val msg = when {
                    !serverMsg.isNullOrBlank() -> serverMsg
                    status == 401 || status == 403 -> "Invalid username or password"
                    status != null -> "Server error $status"
                    else -> error.message ?: "Network error"
                }
                onFailure(status, msg)
            }
        ) {
            override fun getBodyContentType(): String = "application/json; charset=utf-8"
            override fun getBody(): ByteArray = body.toByteArray(Charsets.UTF_8)
            override fun getHeaders(): Map<String, String> {
                return mapOf(
                    "Accept" to "application/json",
                    "Content-Type" to "application/json"
                )
            }
        }

        request.retryPolicy = DefaultRetryPolicy(
            15_000,
            2,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        q.add(request)
    }

    internal data class LoginRequest(
        val username: String,
        val password: String,
        val applicationId: String,
        val appVersionNumber: String,
        val deviceToken: String
    )

    internal data class LoginResponse(
        @SerializedName("api_token") val apiToken: String?,
        @SerializedName("server_user_id") val serverUserId: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("surname") val surname: String?,
        @SerializedName("email") val email: String?,
        @SerializedName("server_time") val serverTime: String?,
        @SerializedName("special_instructions") val specialInstructions: String?,
        @SerializedName("language_code") val languageCode: String?,
        @SerializedName("language_version") val languageVersion: String?,
        @SerializedName("message") val message: String?
    ) {
        fun toAuthUser(username: String): AuthUser = AuthUser(
            serverUserId = serverUserId ?: "",
            username = username,
            name = name ?: "",
            surname = surname ?: "",
            email = email,
            apiToken = apiToken ?: "",
            specialInstructions = specialInstructions,
            languageCode = languageCode,
            languageVersion = languageVersion,
            serverTime = serverTime
        )
    }
}
