package za.co.mjrsolutions.shared.audit

/** Which audit collection a log call targets. Collection names are a frozen contract. */
enum class AuditKind(val collectionName: String) {
    GET_FROM_SERVER("GetFromServer"),
    SYNC_TO_SERVER("SyncToServer")
}
