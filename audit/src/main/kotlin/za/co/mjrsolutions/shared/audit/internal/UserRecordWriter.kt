package za.co.mjrsolutions.shared.audit.internal

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import za.co.mjrsolutions.shared.audit.AuditFormat

/** The two user documents behind a seam, so the field rules are testable without Firestore. */
internal interface UserDocsGateway {
    fun mergeUser(username: String, fields: Map<String, Any?>)
    fun mergeBundle(username: String, applicationId: String, fields: Map<String, Any?>)
}

/**
 * What a login writes for the console: users/{login id} (the profile the admin push and the App
 * admins tab read) and users/{login id}/bundle/{applicationId} (the Users tab, one per app).
 * Field names are hael's (FirebaseUserModel getters: username, displayName, appVersion,
 * lastLoginTime, pushToken, admin) and mr's profile doc. Merge writes only; a value the app does
 * not know (admin flag, push token) is left out, so it never overwrites what is there.
 */
internal class UserRecordWriter(
    private val docs: UserDocsGateway,
    private val appVersion: String,
    private val applicationId: String,
    private val clock: () -> Long
) {
    fun write(username: String?, displayName: String?, isAdmin: Boolean?, pushToken: String?) {
        if (AuditFormat.isBlank(username)) return
        val id = username!!.trim()
        val name = if (AuditFormat.isBlank(displayName)) id else displayName!!.trim()
        val now = clock()

        val root = linkedMapOf<String, Any?>(
            "username" to id, "displayName" to name, "appVersion" to appVersion, "lastLoginTime" to now
        )
        if (!AuditFormat.isBlank(pushToken)) root["pushToken"] = pushToken
        if (isAdmin != null) root["isAdmin"] = isAdmin
        docs.mergeUser(id, root)

        if (AuditFormat.isBlank(applicationId)) return
        val bundle = linkedMapOf<String, Any?>(
            "username" to id, "displayName" to name, "appVersion" to appVersion, "lastLoginTime" to now
        )
        if (!AuditFormat.isBlank(pushToken)) bundle["pushToken"] = pushToken
        if (isAdmin != null) bundle["admin"] = isAdmin
        docs.mergeBundle(id, applicationId, bundle)
    }
}

internal class FirestoreUserDocs : UserDocsGateway {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    override fun mergeUser(username: String, fields: Map<String, Any?>) {
        db.collection("users").document(username)
            .set(fields, SetOptions.merge())
            .addOnFailureListener { Crash.record(it) }
    }

    override fun mergeBundle(username: String, applicationId: String, fields: Map<String, Any?>) {
        db.collection("users").document(username)
            .collection("bundle").document(applicationId)
            .set(fields, SetOptions.merge())
            .addOnFailureListener { Crash.record(it) }
    }
}
