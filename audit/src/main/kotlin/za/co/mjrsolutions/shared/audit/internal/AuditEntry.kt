package za.co.mjrsolutions.shared.audit.internal

/** A fully-resolved audit write: doc path segments + payload. [path] is the slash-joined
 *  Firestore path used as the Message.data deep link. */
internal data class AuditEntry(
    val collection: String,      // "GetFromServer" | "SyncToServer"
    val clientId: String,
    val userName: String,
    val segment: String,         // "success"/"failure" (get) | reportNumber/"Unknown" (sync)
    val docId: String,           // epoch millis as string
    val payload: Map<String, Any?>
) {
    val path: String get() = "/$collection/$clientId/$userName/$segment/entries/$docId"
}
