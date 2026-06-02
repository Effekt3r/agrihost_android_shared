package za.co.mjrsolutions.shared.network.outbox

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(entry: OutboxEntry)

    @Query("SELECT * FROM outbox_entry WHERE id = :id")
    suspend fun getById(id: String): OutboxEntry?

    @Query("SELECT * FROM outbox_entry WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun pendingOrdered(): List<OutboxEntry>

    @Query("SELECT COUNT(*) FROM outbox_entry WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM outbox_entry WHERE status = 'PENDING'")
    fun observePendingCount(): LiveData<Int>

    @Query("SELECT * FROM outbox_entry ORDER BY createdAt DESC")
    fun observeAll(): LiveData<List<OutboxEntry>>

    @Query("UPDATE outbox_entry SET status = 'IN_FLIGHT', lastAttemptAt = :now WHERE id = :id")
    suspend fun markInFlight(id: String, now: Long)

    @Query("UPDATE outbox_entry SET status = 'DONE', serverIdResult = :serverId, lastAttemptAt = :now WHERE id = :id")
    suspend fun markDone(id: String, serverId: String?, now: Long)

    @Query("UPDATE outbox_entry SET status = 'FAILED', lastError = :error, lastAttemptAt = :now WHERE id = :id")
    suspend fun markFailed(id: String, error: String?, now: Long)

    @Query("UPDATE outbox_entry SET status = 'PENDING', attemptCount = attemptCount + 1, lastError = :error, lastAttemptAt = :now WHERE id = :id")
    suspend fun bumpAttempt(id: String, error: String?, now: Long)
}
