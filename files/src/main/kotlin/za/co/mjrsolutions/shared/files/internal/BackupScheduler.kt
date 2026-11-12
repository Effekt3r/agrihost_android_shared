package za.co.mjrsolutions.shared.files.internal

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/**
 * The Wi-Fi job (hael's PhotoBackupJobService rules): queued with an unmetered-network
 * requirement, so Android starts it once the tablet is on Wi-Fi, app open or not. A job already
 * queued or running is left alone - rescheduling one stops it mid-pass.
 */
internal object BackupScheduler {

    /** Ours alone among the app's jobs. hael's old photo job used 711302017. */
    const val JOB_ID = 711302018

    /** Queues the Wi-Fi job unless it is already queued; false when it could not be queued. */
    fun scheduleWifiPass(context: Context): Boolean {
        val app = context.applicationContext
        return try {
            val scheduler = app.getSystemService(JobScheduler::class.java) ?: return false
            if (scheduler.getPendingJob(JOB_ID) != null) return true
            val job = JobInfo.Builder(JOB_ID, ComponentName(app, BackupJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .build()
            val scheduled = scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
            if (!scheduled) Report.exception(IllegalStateException("Backup job not scheduled"))
            scheduled
        } catch (e: RuntimeException) {
            Report.exception(e)
            false
        }
    }

    /** Cancels jobs an older app version queued for this work, so they can't block [JOB_ID]. */
    fun cancelLegacyJobs(context: Context, ids: List<Int>) {
        if (ids.isEmpty()) return
        try {
            val scheduler = context.applicationContext.getSystemService(JobScheduler::class.java) ?: return
            ids.filter { it != JOB_ID }.forEach { scheduler.cancel(it) }
        } catch (e: RuntimeException) {
            Report.exception(e)
        }
    }
}
