package za.co.mjrsolutions.shared.audit

import com.google.firebase.messaging.FirebaseMessaging
import za.co.mjrsolutions.shared.audit.internal.Crash
import za.co.mjrsolutions.shared.audit.internal.FirestoreUserDocs
import za.co.mjrsolutions.shared.audit.internal.UserRecordWriter

/**
 * The console's record of a login, the same in every app: users/{login id} and
 * users/{login id}/bundle/{applicationId} (see UserRecordWriter for the fields). Call after an
 * online login. Uses the [AuditConfig] given to [AgriAudit.init] (appVersion, applicationId);
 * before init it does nothing. Never throws.
 */
object AgriUserRecord {

    /**
     * @param username the login id (document id)
     * @param displayName the assessor's full name
     * @param isAdmin only when the app knows it (hael); null leaves the console's value alone
     */
    @JvmStatic
    @JvmOverloads
    fun recordLogin(username: String?, displayName: String?, isAdmin: Boolean? = null) {
        val config = AgriAudit.config ?: return
        if (AuditFormat.isBlank(username)) return
        val writer = UserRecordWriter(FirestoreUserDocs(), config.appVersion, config.applicationId,
            System::currentTimeMillis)
        try {
            // A listener, never Tasks.await: login callbacks run on the main thread.
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result else null
                runCatching { writer.write(username, displayName, isAdmin, token) }.onFailure { Crash.record(it) }
            }
        } catch (e: Exception) {
            Crash.record(e)
            runCatching { writer.write(username, displayName, isAdmin, null) }.onFailure { Crash.record(it) }
        }
    }
}
