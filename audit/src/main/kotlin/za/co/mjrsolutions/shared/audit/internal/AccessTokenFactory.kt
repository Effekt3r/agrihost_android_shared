package za.co.mjrsolutions.shared.audit.internal

import com.google.auth.oauth2.GoogleCredentials
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/** service-account JSON → short-lived OAuth token for FCM v1. Open seam for tests. */
internal fun interface AccessTokenFactory {
    fun token(serviceAccountJson: String): String?

    companion object {
        val GOOGLE = AccessTokenFactory { json ->
            val credentials = GoogleCredentials
                .fromStream(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)))
                .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
            credentials.refreshIfExpired()
            credentials.accessToken?.tokenValue
        }
    }
}
