package za.co.mjrsolutions.shared.network.outbox

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One queued write. [id] is a client-generated UUID and doubles as the Idempotency-Key sent
 * to the server, so a retried upload after a lost response can be de-duplicated server-side.
 * Large binaries are referenced by [payloadFileRef] (a file path), never inlined.
 */
@Entity(tableName = "outbox_entry")
data class OutboxEntry(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val opType: String,
    val method: String,
    val path: String,
    val tenant: String,
    val payloadJson: String?,
    val payloadFileRef: String?,
    val dependsOnId: String?,
    val status: String,
    val attemptCount: Int,
    val lastError: String?,
    val lastAttemptAt: Long?,
    val serverIdResult: String?
)
