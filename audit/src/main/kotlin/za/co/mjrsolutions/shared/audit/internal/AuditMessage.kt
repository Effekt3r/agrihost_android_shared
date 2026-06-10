package za.co.mjrsolutions.shared.audit.internal

internal data class AuditMessage(
    val title: String,
    val body: String,
    val data: String,            // audit entry path
    val timestamp: Long,
    val sender: String
) {
    /** Exact legacy document shape — legacy POJO serialization included "id": null. */
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to null,
        "title" to title,
        "body" to body,
        "data" to data,
        "timestamp" to timestamp,
        "sender" to sender,
        "read" to false
    )
}
