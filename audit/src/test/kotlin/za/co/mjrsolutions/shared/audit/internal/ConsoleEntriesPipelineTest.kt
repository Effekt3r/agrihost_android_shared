package za.co.mjrsolutions.shared.audit.internal

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.audit.AuditConfig
import za.co.mjrsolutions.shared.audit.PdfLogEntry
import za.co.mjrsolutions.shared.audit.PhotoLogEntry
import za.co.mjrsolutions.shared.audit.PhotoUploadLogEntry
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ConsoleEntriesPipelineTest {

    private class FakeGateway : FirestoreGateway {
        val entries = mutableListOf<AuditEntry>()
        val messages = mutableListOf<AuditMessage>()
        override fun writeAuditEntry(entry: AuditEntry) { entries.add(entry) }
        override fun writeMessage(message: AuditMessage, onCommitted: () -> Unit) { messages.add(message) }
        override suspend fun queryAdminTokens(): List<AdminToken> = emptyList()
        override suspend fun fetchServiceAccountJson(): String? = null
        override fun clearStaleToken(userId: String, bundleId: String?, bundleField: String?) {}
    }

    private fun pipeline(gw: FakeGateway, fullName: String? = "Jan Botha") = AuditPipeline(
        config = AuditConfig("AGRIHOST_TEST_mr", { fullName }, loginIdProvider = { "janb" }),
        gateway = gw, tokenFactory = { null }, sendFn = { _, _, _ -> },
        clock = { 7000L }, notifyLauncher = { block -> runBlocking { block() } }
    )

    @Test
    fun `photoEntriesWriteNoMessage - photo, upload and pdf entries never notify admins`() {
        val gw = FakeGateway()
        val p = pipeline(gw)
        p.runConsole { f, user, login, ms -> f.photo(PhotoLogEntry().apply { reportNumber = "40068"; mediaId = 5 }, user, login, ms) }
        p.runConsole { f, user, login, _ -> f.photoUpload(PhotoUploadLogEntry().apply { reportNumber = "40068" }, user, login, p.nextId().toString()) }
        p.runConsole { f, user, login, ms -> f.pdf(PdfLogEntry().apply { reportNumber = "40068"; fileName = "a.pdf" }, user, login, ms) }
        assertEquals(3, gw.entries.size)
        assertTrue(gw.messages.isEmpty())
        assertEquals("/SyncToServer/AGRIHOST_TEST_mr/Jan Botha/40068/entries/photo_5", gw.entries[0].path)
    }

    @Test
    fun `photoUploadEntryIdsNeverRepeat - same clock millisecond, different ids`() {
        val p = pipeline(FakeGateway())
        val a = p.nextId(); val b = p.nextId(); val c = p.nextId()
        assertEquals(listOf(7000L, 7001L, 7002L), listOf(a, b, c))
    }

    @Test
    fun `missing full name files the entry under Unknown`() {
        val gw = FakeGateway()
        pipeline(gw, fullName = "  ").runConsole { f, user, login, ms ->
            f.photo(PhotoLogEntry().apply { reportNumber = "1"; mediaId = 2 }, user, login, ms)
        }
        assertEquals("Unknown", gw.entries.single().userName)
    }
}
