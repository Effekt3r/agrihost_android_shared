package za.co.mjrsolutions.shared.audit.internal

import com.google.firebase.firestore.FieldValue
import za.co.mjrsolutions.shared.audit.AuditConfig
import za.co.mjrsolutions.shared.audit.AuditKind
import za.co.mjrsolutions.shared.audit.AuditReportInfo

/**
 * Pure construction of audit entries and messages. Paths and payload schema are the
 * frozen Firestore contract (spec §Firestore Contracts); wording follows vrugte.
 * Callers must pass a userName already passed through [normaliseUserName].
 */
internal class AuditEntryFactory(private val config: AuditConfig) {

    fun entry(kind: AuditKind, payload: String?, success: Boolean, report: AuditReportInfo?,
              userName: String, millis: Long): AuditEntry {
        val status = if (success) "success" else "failure"
        val segment = when (kind) {
            AuditKind.GET_FROM_SERVER -> status
            AuditKind.SYNC_TO_SERVER -> reportSegment(report)
        }
        val map = mutableMapOf<String, Any?>(
            "request" to (payload ?: ""),
            "timestamp" to FieldValue.serverTimestamp(),
            "client" to config.clientId,
            "userName" to userName,
            "status" to status
        )
        if (kind == AuditKind.SYNC_TO_SERVER) {
            map["reportNumber"] = report?.reportNumber ?: ""
            map["reportServerId"] = report?.reportServerId ?: ""
            map["reportLocalId"] = report?.reportLocalId ?: ""
            map["landIds"] = report?.landIds?.toList() ?: emptyList<String>()
            map["landNames"] = report?.landNames?.toList() ?: emptyList<String>()
            map["landClaimIds"] = report?.landClaimIds?.toList() ?: emptyList<String>()
        }
        return AuditEntry(kind.collectionName, config.clientId, userName, segment,
            millis.toString(), map)
    }

    fun message(kind: AuditKind, success: Boolean, report: AuditReportInfo?,
                userName: String, entryPath: String, millis: Long): AuditMessage {
        val prefix = "${config.clientId} - $userName"
        val (title, body) = when (kind) {
            AuditKind.GET_FROM_SERVER -> "Received from Server" to
                if (success) "$prefix received data" else "$prefix failed to receive data"
            AuditKind.SYNC_TO_SERVER -> {
                val seg = reportSegment(report)
                val detail = if (seg == "Unknown") "" else " $seg"
                "Synced to Server" to
                    if (success) "$prefix synced report$detail" else "$prefix failed to sync report$detail"
            }
        }
        return AuditMessage(title, body, entryPath, millis, config.sender)
    }

    private fun reportSegment(report: AuditReportInfo?): String =
        report?.reportNumber?.takeIf { it.isNotBlank() } ?: "Unknown"

    companion object {
        fun normaliseUserName(name: String?): String =
            name?.takeIf { it.isNotBlank() } ?: "Unknown"
    }
}
