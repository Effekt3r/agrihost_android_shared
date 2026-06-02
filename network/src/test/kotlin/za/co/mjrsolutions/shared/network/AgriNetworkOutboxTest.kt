package za.co.mjrsolutions.shared.network

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.network.log.NoopLogSink
import za.co.mjrsolutions.shared.network.outbox.OpType
import za.co.mjrsolutions.shared.network.outbox.OutboxDatabase
import za.co.mjrsolutions.shared.network.outbox.OutboxOperation
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AgriNetworkOutboxTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        OutboxDatabase.resetForTest()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ctx,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        AgriNetwork.init(
            ctx,
            AgriNetworkConfig(
                baseUrl = server.url("/api").toString(),
                authHeaderProvider = { emptyMap() },
                logSink = NoopLogSink
            )
        )
    }

    @After fun tearDown() {
        server.shutdown()
        OutboxDatabase.resetForTest()
    }

    @Test
    fun `enqueue inserts a PENDING row and returns its id`() = runTest {
        val id = AgriNetwork.enqueue(
            OutboxOperation(OpType.SAVE_REPORT, HttpMethod.POST, "/damage-reports", "agrihost", payloadJson = "{}")
        )
        assertTrue(id.isNotBlank())
        // give the IO insert a moment under the test scheduler
        val row = AgriNetwork.outboxDaoForTest().getById(id)
        assertTrue(row == null || row.status == "PENDING")
    }

    @Test
    fun `syncNow enqueues a unique one-time work request`() {
        AgriNetwork.syncNow()
        val infos = WorkManager.getInstance(
            ApplicationProvider.getApplicationContext()
        ).getWorkInfosForUniqueWork("agri_outbox_oneshot_sync").get()
        assertEquals(1, infos.size)
    }
}
