package za.co.mjrsolutions.shared.audit.internal

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** All Firestore I/O behind one seam so AuditPipeline is testable with a fake. */
internal interface FirestoreGateway {
    fun writeAuditEntry(entry: AuditEntry)                  // fire-and-forget
    fun writeMessage(message: AuditMessage)                 // fire-and-forget
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

    override fun writeMessage(message: AuditMessage) {
        db.collection("messages").add(message.toFirestoreMap())
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

    override suspend fun fetchServiceAccountJson(): String? =
        db.collection("services").document("AccountKey").get().await().getString("json")

    override fun clearStaleToken(userId: String, bundleId: String?, bundleField: String?) {
        if (userId.isEmpty()) return
        db.collection("users").document(userId)
            .update("pushToken", FieldValue.delete())
            .addOnFailureListener { Crash.record(it) }
        if (!bundleId.isNullOrEmpty() && !bundleField.isNullOrEmpty()) {
            db.collection("users").document(userId)
                .collection("bundle").document(bundleId)
                .update(bundleField, FieldValue.delete())
                .addOnFailureListener { Crash.record(it) }
        }
    }
}
