package za.co.mjrsolutions.shared.network.outbox

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.network.AgriNetwork
import za.co.mjrsolutions.shared.network.AgriNetworkConfig
import za.co.mjrsolutions.shared.network.log.NoopLogSink
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private lateinit var server: MockWebServer

    // Insert rows directly (deterministic) rather than via the fire-and-forget enqueue(),
    // so the worker has its PENDING row before doWork() runs.
    private fun pending(id: String) = OutboxEntry(
        id = id, createdAt = 1, opType = OpType.SAVE_REPORT, method = "POST",
        path = "/damage-reports", tenant = "agrihost", payloadJson = "{}", payloadFileRef = null,
        dependsOnId = null, status = "PENDING", attemptCount = 0, lastError = null,
        lastAttemptAt = null, serverIdResult = null
    )

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        OutboxDatabase.resetForTest()
        AgriNetwork.init(
            ApplicationProvider.getApplicationContext(),
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
    fun `worker drains a pending entry to DONE and succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val dao = AgriNetwork.outboxDaoForTest()
        dao.insert(pending("id1"))

        val worker = TestListenableWorkerBuilder<SyncWorker>(
            ApplicationProvider.getApplicationContext()
        ).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals("DONE", dao.getById("id1")!!.status)
    }

    @Test
    fun `worker retries when the server is failing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        AgriNetwork.outboxDaoForTest().insert(pending("id2"))

        val worker = TestListenableWorkerBuilder<SyncWorker>(
            ApplicationProvider.getApplicationContext()
        ).build()

        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }
}
