package za.co.mjrsolutions.shared.audit.internal

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** All Firestore I/O behind one seam so AuditPipeline is testable with a fake. */
internal interface FirestoreGateway {
    fun writeAuditEntry(entry: AuditEntry)                  // fire-and-forget

    /**
     * Fire-and-forget add to `messages`. [onCommitted] runs once the write is acknowledged
     * by the server — legacy parity: the admin push is gated on the success listener, so an
     * offline sync defers its notification until connectivity returns instead of attempting
     * (and losing) it immediately. On failure the write is crash-reported and [onCommitted]
     * never runs.
     * [onCommitted] is invoked on the main thread; it must be cheap and non-throwing.
     */
    fun writeMessage(message: AuditMessage, onCommitted: () -> Unit)

    suspend fun queryAdminTokens(): List<AdminToken>
    suspend fun fetchServiceAccountJson(): String?
    fun clearStaleToken(userId: String, bundleId: String?, bundleField: String?)
}

internal class FirebaseFirestoreGateway : FirestoreGateway {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    override fun writeAuditEntry(entry: AuditEntry) {
        db.collection(entry.collection).document(entry.clientId)
            .collection(entry.userName).document(entry.segment)
            .collection("entries").document(entry.docId)
            .set(entry.payload)
            .addOnFailureListener { Crash.record(it) }
    }

    override fun writeMessage(message: AuditMessage, onCommitted: () -> Unit) {
        db.collection("messages").add(message.toFirestoreMap())
            .addOnSuccessListener {
                try { onCommitted() } catch (t: Throwable) { Crash.record(t) }
            }
            .addOnFailureListener { Crash.record(it) }
    }

    override suspend fun queryAdminTokens(): List<AdminToken> =
        db.collection("users")
            .whereEqualTo("isAdmin", true)
            .whereNotEqualTo("pushToken", null)
            .get().await()
            .documents.mapNotNull { doc ->
                doc.getString("pushToken")?.takeIf { it.isNotEmpty() }
                    ?.let { AdminToken(doc.id, it) }
            }

    override suspend fun fetchServiceAccountJson(): String? {
        val json = db.collection("services").document("AccountKey").get().await()
            .getString("json")?.takeIf { it.isNotEmpty() }
        if (json == null) Crash.record(Exception("Service-account JSON missing or empty"))
        return json
    }

    override fun clearStaleToken(userId: String, bundleId: String?, bundleField: String?) {
        if (userId.isEmpty()) return
        try {
            // Listener failures stay at Log.w (legacy parity): NOT_FOUND here is routine,
            // e.g. an admin user doc without this app's bundle sub-doc.
            db.collection("users").document(userId)
                .update("pushToken", FieldValue.delete())
                .addOnFailureListener { Log.w("AgriAudit", "Failed clearing stale pushToken for user=$userId", it) }
            if (!bundleId.isNullOrEmpty() && !bundleField.isNullOrEmpty()) {
                db.collection("users").document(userId)
                    .collection("bundle").document(bundleId)
                    .update(bundleField, FieldValue.delete())
                    .addOnFailureListener { Log.w("AgriAudit", "Failed clearing stale $bundleField for user=$userId", it) }
            }
        } catch (e: Exception) {
            Crash.record(e)
        }
    }
}
