package za.co.mjrsolutions.shared.files.internal

import android.app.job.JobParameters
import android.app.job.JobService
import za.co.mjrsolutions.shared.files.backup.AgriBackup

/**
 * Runs the automatic copy once the tablet is on Wi-Fi: [BackupScheduler] queues this job with an
 * unmetered-network requirement. The copying runs on the backup thread; the job stays open until
 * that pass ends.
 */
class BackupJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        AgriBackup.runWifiPass(applicationContext) { jobFinished(params, false) }
        return true
    }

    // Android ended it early (Wi-Fi gone, battery saver...): the pass stops at its next Wi-Fi
    // check, and the job runs again once Wi-Fi is back.
    override fun onStopJob(params: JobParameters): Boolean = true
}
