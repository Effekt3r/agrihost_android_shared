package za.co.mjrsolutions.shared.files

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.files.backup.AgriBackup
import za.co.mjrsolutions.shared.files.internal.BackupJobService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class AgriFilesTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scheduler get() = app.getSystemService(JobScheduler::class.java)

    @After
    fun reset() = AgriFiles.resetForTest()

    private fun config(legacy: List<Int> = emptyList()) =
        FilesConfig("hael", "AGRIHOST_TEST", { "janb" }, { "Jan Botha" }, "10.0.24", 1, "x", legacyJobIds = legacy)

    @Test
    fun `initCancelsLegacyJob - hael's old photo job can't block the new one`() {
        scheduler.schedule(JobInfo.Builder(711302017, ComponentName(app, BackupJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).build())
        assertNotNull(scheduler.getPendingJob(711302017))
        AgriFiles.init(app, config(legacy = listOf(711302017)))
        assertNull(scheduler.getPendingJob(711302017))
    }

    @Test
    fun `requestWifiPass queues one unmetered job`() {
        AgriFiles.init(app, config())
        AgriBackup.requestWifiPass(app)
        AgriBackup.requestWifiPass(app)
        val job = scheduler.getPendingJob(711302018)
        assertNotNull(job)
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, job.networkType)
        assertEquals(1, scheduler.allPendingJobs.size)
    }
}
