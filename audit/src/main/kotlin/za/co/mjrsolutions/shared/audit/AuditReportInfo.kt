package za.co.mjrsolutions.shared.audit

/** Report metadata for SyncToServer entries. Plain values — no app model dependency. */
data class AuditReportInfo @JvmOverloads constructor(
    val reportNumber: String?,
    val reportServerId: String?,
    val reportLocalId: String?,
    val landIds: List<String> = emptyList(),
    val landNames: List<String> = emptyList(),
    val landClaimIds: List<String> = emptyList()
)
