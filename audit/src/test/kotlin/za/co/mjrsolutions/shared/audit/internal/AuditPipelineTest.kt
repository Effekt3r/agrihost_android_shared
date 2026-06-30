package za.co.mjrsolutions.shared.audit.internal

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.audit.AuditConfig
import za.co.mjrsolutions.shared.audit.AuditKind
import za.co.mjrsolutions.shared.audit.AuditReportInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Robolectric: failure paths hit Crash.record → android.util.Log fallback.
@RunWith(RobolectricTestRunner::class)
class AuditPipelineTest {

    private class FakeGateway : FirestoreGateway {
        val entries = mutableListOf<AuditEntry>()
        val messages = mutableListOf<AuditMessage>()
        private val pendingCommits = mutableListOf<() -> Unit>()
        var admins: List<AdminToken> = emptyList()
        var serviceJson: String? = "{}"
        var failEntryWrite = false
        var failMessageWrite = false
        var failAdminQuery = false
        override fun writeAuditEntry(entry: AuditEntry) {
            if (failEntryWrite) throw RuntimeException("boom"); entries.add(entry)
        }
        override fun writeMessage(message: AuditMessage, onCommitted: () -> Unit) {
            if (failMessageWrite) return            // legacy: failed add never fires the callback
            messages.add(message); pendingCommits.add(onCommitted)
        }
        /** Simulates the server acknowledging all pending `messages` writes. */
        fun commitAll() {
            val commits = pendingCommits.toList(); pendingCommits.clear()
            commits.forEach { it() }
        }
        override suspend fun queryAdminTokens(): List<AdminToken> {
            if (failAdminQuery) throw RuntimeException("query boom"); return admins
        }
        override suspend fun fetchServiceAccountJson(): String? = serviceJson
        override fun clearStaleToken(userId: String, bundleId: String?, bundleField: String?) {}
    }

    private val sent = mutableListOf<Pair<AdminToken, AuditMessage>>()
    private val config = AuditConfig("AGRIHOST_TEST_vrugte", { "Jan" })

    // Inline launcher: notify blocks run synchronously when the fake commits.
    private fun pipeline(gw: FakeGateway) = AuditPipeline(
        config = config, gateway = gw,
        tokenFactory = { "fake-oauth" },
        sendFn = { _, admin, msg -> sent.add(admin to msg) },
        clock = { 7000L },
        notifyLauncher = { block -> runBlocking { block() } }
    )

    @Test
    fun `happy path writes entry and message, then notifies every admin on commit`() = runTest {
        val gw = FakeGateway().apply {
            admins = listOf(AdminToken("a", "t1"), AdminToken("b", "t2"))
        }
        pipeline(gw).run(AuditKind.GET_FROM_SERVER, "resp", true, null)
        assertEquals(1, gw.entries.size)
        assertEquals("/GetFromServer/AGRIHOST_TEST_vrugte/Jan/success/entries/7000", gw.entries[0].path)
        assertEquals(1, gw.messages.size)
        assertEquals(gw.entries[0].path, gw.messages[0].data)
        assertTrue(sent.isEmpty())              // not yet committed → no push (offline-deferral parity)
        gw.commitAll()
        assertEquals(2, sent.size)
    }

    @Test
    fun `entry write failure still writes message and notifies on commit`() = runTest {
        val gw = FakeGateway().apply { failEntryWrite = true; admins = listOf(AdminToken("a", "t1")) }
        pipeline(gw).run(AuditKind.SYNC_TO_SERVER, "req", false,
            AuditReportInfo("R-1", null, null))
        assertEquals(0, gw.entries.size)
        assertEquals(1, gw.messages.size)       // step isolation (spec §Internal Architecture)
        gw.commitAll()
        assertEquals(1, sent.size)
    }

    @Test
    fun `message write failure means no notification`() = runTest {
        val gw = FakeGateway().apply { failMessageWrite = true; admins = listOf(AdminToken("a", "t1")) }
        pipeline(gw).run(AuditKind.GET_FROM_SERVER, "x", true, null)
        gw.commitAll()
        assertTrue(sent.isEmpty())              // legacy parity: push is gated on the messages commit
        assertEquals(1, gw.entries.size)        // entry write unaffected
    }

    @Test
    fun `admin query failure is swallowed`() = runTest {
        val gw = FakeGateway().apply { failAdminQuery = true }
        pipeline(gw).run(AuditKind.GET_FROM_SERVER, "x", true, null)
        gw.commitAll()                          // must not throw
        assertEquals(1, gw.entries.size); assertTrue(sent.isEmpty())
    }

    @Test
    fun `null service account json skips sends without throwing`() = runTest {
        val gw = FakeGateway().apply { serviceJson = null; admins = listOf(AdminToken("a", "t")) }
        pipeline(gw).run(AuditKind.GET_FROM_SERVER, "x", true, null)
        gw.commitAll()
        assertTrue(sent.isEmpty()); assertEquals(1, gw.entries.size)
    }

    @Test
    fun `userName provider failure falls back to Unknown`() = runTest {
        val throwing = AuditConfig("c", { throw IllegalStateException("no db yet") })
        val gw = FakeGateway()
        AuditPipeline(throwing, gw, { "t" }, { _, _, _ -> }, { 1L }, { block -> runBlocking { block() } })
            .run(AuditKind.GET_FROM_SERVER, "x", true, null)
        assertEquals("Unknown", gw.entries[0].userName)
    }

    @Test
    fun `token factory failure is swallowed and sends nothing`() = runTest {
        val gw = FakeGateway().apply { admins = listOf(AdminToken("a", "t1")) }
        AuditPipeline(config, gw, { throw java.io.IOException("credentials boom") },
            { _, admin, msg -> sent.add(admin to msg) }, { 7000L }, { block -> runBlocking { block() } })
            .run(AuditKind.GET_FROM_SERVER, "x", true, null)
        gw.commitAll()                          // must not throw
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `one admin send failure does not stop the others`() = runTest {
        val gw = FakeGateway().apply { admins = listOf(AdminToken("a", "t1"), AdminToken("b", "t2")) }
        AuditPipeline(config, gw, { "tok" },
            { _, admin, msg -> if (admin.userId == "a") throw RuntimeException("send boom") else sent.add(admin to msg) },
            { 7000L }, { block -> runBlocking { block() } })
            .run(AuditKind.GET_FROM_SERVER, "x", true, null)
        gw.commitAll()
        assertEquals(listOf("b"), sent.map { it.first.userId })
    }

    @Test
    fun `runFailure writes failure entry and no message`() = runTest {
        val gw = FakeGateway().apply { admins = listOf(AdminToken("a", "t1")) }
        pipeline(gw).runFailure("{\"a\":1}", 500, "/harvest", "server error", "boom",
            AuditReportInfo("40068", null, null))
        assertEquals(1, gw.entries.size)
        assertEquals("SyncFailures", gw.entries[0].collection)
        assertEquals("failure", gw.entries[0].payload["status"])
        assertEquals(500, gw.entries[0].payload["httpStatus"])
        assertEquals(0, gw.messages.size)
        gw.commitAll()
        assertTrue(sent.isEmpty())
    }
}
