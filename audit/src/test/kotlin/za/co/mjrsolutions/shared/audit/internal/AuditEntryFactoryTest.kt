package za.co.mjrsolutions.shared.audit.internal

import org.junit.Test
import za.co.mjrsolutions.shared.audit.AuditConfig
import za.co.mjrsolutions.shared.audit.AuditKind
import za.co.mjrsolutions.shared.audit.AuditReportInfo
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuditEntryFactoryTest {

    private val config = AuditConfig(clientId = "AGRIHOST_TEST_vrugte", userNameProvider = { "Jan Botha" })
    private val factory = AuditEntryFactory(config)
    private val report = AuditReportInfo(
        reportNumber = "R-123", reportServerId = "srv-9", reportLocalId = "loc-4",
        landIds = listOf("L1"), landNames = listOf("Kamp 3"), landClaimIds = listOf("LC7")
    )

    @Test
    fun `getFromServer success entry has frozen path and unified payload`() {
        val e = factory.entry(AuditKind.GET_FROM_SERVER, "body", success = true, report = null,
            userName = "Jan Botha", millis = 1000L)
        assertEquals("/GetFromServer/AGRIHOST_TEST_vrugte/Jan Botha/success/entries/1000", e.path)
        assertEquals("body", e.payload["request"])
        assertEquals("AGRIHOST_TEST_vrugte", e.payload["client"])
        assertEquals("Jan Botha", e.payload["userName"])
        assertEquals("success", e.payload["status"])
        assertNull(e.payload["reportNumber"])   // get entries carry no report fields
    }

    @Test
    fun `failure maps to failure segment and status`() {
        val e = factory.entry(AuditKind.GET_FROM_SERVER, null, success = false, report = null,
            userName = "Jan Botha", millis = 1000L)
        assertEquals("failure", e.segment)
        assertEquals("failure", e.payload["status"])
        assertEquals("", e.payload["request"])  // null payload normalised to ""
    }

    @Test
    fun `syncToServer entry keyed by reportNumber with report metadata`() {
        val e = factory.entry(AuditKind.SYNC_TO_SERVER, "req", success = true, report = report,
            userName = "Jan Botha", millis = 2000L)
        assertEquals("/SyncToServer/AGRIHOST_TEST_vrugte/Jan Botha/R-123/entries/2000", e.path)
        assertEquals("success", e.payload["status"])
        assertEquals("R-123", e.payload["reportNumber"])
        assertEquals("srv-9", e.payload["reportServerId"])
        assertEquals("loc-4", e.payload["reportLocalId"])
        assertEquals(listOf("L1"), e.payload["landIds"])
        assertEquals(listOf("Kamp 3"), e.payload["landNames"])
        assertEquals(listOf("LC7"), e.payload["landClaimIds"])
    }

    @Test
    fun `blank reportNumber falls back to Unknown segment`() {
        val e = factory.entry(AuditKind.SYNC_TO_SERVER, "req", success = false,
            report = report.copy(reportNumber = ""), userName = "Jan Botha", millis = 1L)
        assertEquals("Unknown", e.segment)
    }

    @Test
    fun `getFromServer message uses vrugte wording`() {
        val ok = factory.message(AuditKind.GET_FROM_SERVER, success = true, report = null,
            userName = "Jan Botha", entryPath = "/p", millis = 5L)
        assertEquals("Received from Server", ok.title)
        assertEquals("AGRIHOST_TEST_vrugte - Jan Botha received data", ok.body)
        assertEquals("/p", ok.data); assertEquals(5L, ok.timestamp); assertEquals("App", ok.sender)
        val fail = factory.message(AuditKind.GET_FROM_SERVER, success = false, report = null,
            userName = "Jan Botha", entryPath = "/p", millis = 5L)
        assertEquals("AGRIHOST_TEST_vrugte - Jan Botha failed to receive data", fail.body)
    }

    @Test
    fun `syncToServer message includes report number unless Unknown`() {
        val ok = factory.message(AuditKind.SYNC_TO_SERVER, success = true, report = report,
            userName = "Jan Botha", entryPath = "/p", millis = 5L)
        assertEquals("Synced to Server", ok.title)
        assertEquals("AGRIHOST_TEST_vrugte - Jan Botha synced report R-123", ok.body)
        val unknown = factory.message(AuditKind.SYNC_TO_SERVER, success = false,
            report = report.copy(reportNumber = null), userName = "Jan Botha", entryPath = "/p", millis = 5L)
        assertEquals("AGRIHOST_TEST_vrugte - Jan Botha failed to sync report", unknown.body)
    }

    @Test
    fun `null or blank userName normalises to Unknown`() {
        assertEquals("Unknown", AuditEntryFactory.normaliseUserName(null))
        assertEquals("Unknown", AuditEntryFactory.normaliseUserName("  "))
        assertEquals("Jan", AuditEntryFactory.normaliseUserName("Jan"))
    }
}
