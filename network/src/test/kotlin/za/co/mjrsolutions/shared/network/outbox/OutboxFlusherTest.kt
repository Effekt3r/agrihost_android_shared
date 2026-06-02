package za.co.mjrsolutions.shared.network.outbox

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.network.AgriRequest
import za.co.mjrsolutions.shared.network.HttpMethod
import za.co.mjrsolutions.shared.network.NetworkResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OutboxFlusherTest {

    private lateinit var db: OutboxDatabase
    private lateinit var dao: OutboxDao

    private fun entry(id: String, createdAt: Long, dependsOn: String? = null) = OutboxEntry(
        id = id, createdAt = createdAt, opType = OpType.SAVE_REPORT, method = "POST",
        path = "/damage-reports", tenant = "agrihost", payloadJson = "{}", payloadFileRef = null,
        dependsOnId = dependsOn, status = "PENDING", attemptCount = 0, lastError = null,
        lastAttemptAt = null, serverIdResult = null
    )

    private class FakeExecutor(private val result: (AgriRequest) -> NetworkResult<String>) : RequestExecutor {
        val seen = mutableListOf<AgriRequest>()
        override fun execute(request: AgriRequest): NetworkResult<String> {
            seen.add(request); return result(request)
        }
    }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), OutboxDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.outboxDao()
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `success marks DONE, sends idempotency key, and reconciles server id`() = runTest {
        dao.insert(entry("a", 1))
        val exec = FakeExecutor { NetworkResult.Success("""{"id":"srv-9"}""") }
        val reconciled = mutableListOf<Pair<String, String?>>()
        val flusher = OutboxFlusher(
            dao, exec,
            reconciler = { localId, _, serverId -> reconciled.add(localId to serverId) },
            serverIdExtractor = { _, body -> if (body!!.contains("srv-9")) "srv-9" else null },
            now = { 100L }
        )

        flusher.flush()

        assertEquals("DONE", dao.getById("a")!!.status)
        assertEquals("srv-9", dao.getById("a")!!.serverIdResult)
        assertEquals("a", exec.seen.first().extraHeaders["Idempotency-Key"])
        assertEquals(listOf<Pair<String, String?>>("a" to "srv-9"), reconciled)
    }

    @Test
    fun `5xx keeps PENDING and bumps attempt`() = runTest {
        dao.insert(entry("a", 1))
        val flusher = OutboxFlusher(
            dao, { NetworkResult.ServerError(503, "down") },
            OutboxReconciler.NONE, ServerIdExtractor.NONE, now = { 1L }
        )
        flusher.flush()
        val row = dao.getById("a")!!
        assertEquals("PENDING", row.status)
        assertEquals(1, row.attemptCount)
    }

    @Test
    fun `4xx is terminal FAILED`() = runTest {
        dao.insert(entry("a", 1))
        val flusher = OutboxFlusher(
            dao, { NetworkResult.ServerError(400, "bad") },
            OutboxReconciler.NONE, ServerIdExtractor.NONE, now = { 1L }
        )
        flusher.flush()
        assertEquals("FAILED", dao.getById("a")!!.status)
    }

    @Test
    fun `offline keeps PENDING for a later run`() = runTest {
        dao.insert(entry("a", 1))
        val flusher = OutboxFlusher(
            dao, { NetworkResult.Offline },
            OutboxReconciler.NONE, ServerIdExtractor.NONE, now = { 1L }
        )
        flusher.flush()
        assertEquals("PENDING", dao.getById("a")!!.status)
    }

    @Test
    fun `dependent is not sent until its parent is DONE`() = runTest {
        dao.insert(entry("parent", 1))
        dao.insert(entry("child", 2, dependsOn = "parent"))
        // First pass: parent fails with 503, so child must be held back.
        val firstPass = FakeExecutor { req ->
            if (req.extraHeaders["Idempotency-Key"] == "parent") NetworkResult.ServerError(503, "x")
            else NetworkResult.Success("should-not-happen")
        }
        OutboxFlusher(dao, firstPass, OutboxReconciler.NONE, ServerIdExtractor.NONE) { 1L }.flush()
        assertEquals(listOf("parent"), firstPass.seen.map { it.extraHeaders["Idempotency-Key"] })
        assertEquals("PENDING", dao.getById("child")!!.status)

        // Second pass: parent succeeds, then child is allowed through.
        val secondPass = FakeExecutor { NetworkResult.Success("ok") }
        OutboxFlusher(dao, secondPass, OutboxReconciler.NONE, ServerIdExtractor.NONE) { 2L }.flush()
        assertEquals(listOf("parent", "child"), secondPass.seen.map { it.extraHeaders["Idempotency-Key"] })
        assertEquals("DONE", dao.getById("child")!!.status)
    }

    @Test
    fun `flush integrates with MockWebServer end to end`() = runTest {
        val server = MockWebServer(); server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        dao.insert(entry("a", 1))
        val exec = RequestExecutor { req ->
            // mimic AgriNetwork.execute against the mock server
            val client = okhttp3.OkHttpClient()
            val body = (req.bodyJson ?: "").toByteArray()
            val r = okhttp3.Request.Builder()
                .url(server.url(req.path))
                .post(okhttp3.RequestBody.create(null, body))
                .apply { req.extraHeaders.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(r).execute().use {
                if (it.isSuccessful) NetworkResult.Success(it.body!!.string())
                else NetworkResult.ServerError(it.code, null)
            }
        }
        OutboxFlusher(dao, exec, OutboxReconciler.NONE, ServerIdExtractor.NONE) { 1L }.flush()
        assertEquals("DONE", dao.getById("a")!!.status)
        assertEquals("a", server.takeRequest().getHeader("Idempotency-Key"))
        server.shutdown()
    }
}
