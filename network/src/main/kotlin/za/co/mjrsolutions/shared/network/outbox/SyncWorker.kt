package za.co.mjrsolutions.shared.network.outbox

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import za.co.mjrsolutions.shared.network.AgriNetwork

/**
 * Runs one outbox drain. Returns retry() when transient failures left rows PENDING so
 * WorkManager re-schedules with its own backoff; success() otherwise.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val flusher = AgriNetwork.internalFlusher() ?: return Result.success()
        return try {
            val summary = flusher.flush()
            if (summary.hadTransientFailure) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "agri_outbox_periodic_sync"
        const val UNIQUE_ONESHOT = "agri_outbox_oneshot_sync"
    }
}
