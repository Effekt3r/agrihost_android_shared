package za.co.mjrsolutions.shared.audit.internal

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * One FCM v1 send per admin token. Synchronous — always called on the pipeline's
 * background dispatcher. 404 (UNREGISTERED) / 410 mean the token is permanently dead:
 * trigger [onStaleToken] instead of crash-reporting (hael's hardening, ported).
 */
internal class FcmSender(
    private val fcmUrl: String = FCM_URL,
    private val client: OkHttpClient = OkHttpClient(),
    private val onStaleToken: (userId: String) -> Unit
) {
    fun send(accessToken: String, admin: AdminToken, message: AuditMessage) {
        try {
            val body = Gson().toJson(mapOf("message" to mapOf(
                "token" to admin.pushToken,
                "notification" to mapOf("title" to message.title, "body" to message.body),
                "data" to mapOf("path" to message.data)
            )))
            val request = Request.Builder().url(fcmUrl)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> Log.d("AgriAudit", "Notification sent")
                    resp.code == 404 || resp.code == 410 -> {
                        Log.w("AgriAudit", "Stale FCM token (${resp.code}) user=${admin.userId}")
                        onStaleToken(admin.userId)
                    }
                    else -> Crash.record(Exception("FCM send non-200: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Crash.record(e)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val FCM_URL = "https://fcm.googleapis.com/v1/projects/agrihost-7970a/messages:send"
    }
}
