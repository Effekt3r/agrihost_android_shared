package za.co.mjrsolutions.shared.network.outbox

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class OutboxDaoTest {

    private lateinit var db: OutboxDatabase
    private lateinit var dao: OutboxDao

    private fun entry(id: String, createdAt: Long, status: String = "PENDING", dependsOn: String? = null) =
        OutboxEntry(
            id = id, createdAt = createdAt, opType = OpType.SAVE_REPORT, method = "POST",
            path = "/damage-reports", tenant = "agrihost", payloadJson = "{}", payloadFileRef = null,
            dependsOnId = dependsOn, status = status, attemptCount = 0, lastError = null,
            lastAttemptAt = null, serverIdResult = null
        )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), OutboxDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.outboxDao()
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `pendingOrdered returns only PENDING sorted by createdAt`() = runTest {
        dao.insert(entry("b", createdAt = 200))
        dao.insert(entry("a", createdAt = 100))
        dao.insert(entry("done", createdAt = 50, status = "DONE"))

        val pending = dao.pendingOrdered()
        assertEquals(listOf("a", "b"), pending.map { it.id })
    }

    @Test
    fun `markDone sets status and server id`() = runTest {
        dao.insert(entry("a", createdAt = 1))
        dao.markDone("a", serverId = "srv-42", now = 999)
        val row = dao.getById("a")!!
        assertEquals("DONE", row.status)
        assertEquals("srv-42", row.serverIdResult)
    }

    @Test
    fun `bumpAttempt increments and keeps PENDING`() = runTest {
        dao.insert(entry("a", createdAt = 1))
        dao.bumpAttempt("a", error = "503", now = 5)
        val row = dao.getById("a")!!
        assertEquals("PENDING", row.status)
        assertEquals(1, row.attemptCount)
        assertEquals("503", row.lastError)
    }

    @Test
    fun `markFailed is terminal`() = runTest {
        dao.insert(entry("a", createdAt = 1))
        dao.markFailed("a", error = "400", now = 5)
        assertEquals("FAILED", dao.getById("a")!!.status)
    }

    @Test
    fun `countByStatus and missing row`() = runTest {
        dao.insert(entry("a", createdAt = 1))
        assertEquals(1, dao.countByStatus("PENDING"))
        assertNull(dao.getById("nope"))
    }
}
