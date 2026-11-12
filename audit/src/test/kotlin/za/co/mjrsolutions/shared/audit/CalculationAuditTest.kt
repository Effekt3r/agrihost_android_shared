package za.co.mjrsolutions.shared.audit

import com.google.firebase.firestore.FieldValue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculationAuditTest {

    private val docs = mutableListOf<Map<String, Any?>>()
    private var now = 1_000L
    private val config = AuditConfig("AGRIHOST_TEST", { "Jan Botha" }, appVersion = "10.0.24",
        applicationId = "za.co.x.agrihostnewapi")

    private fun audit() = CalculationAudit({ docs.add(it) }, { now }, { config })

    private val scope = CalcScope(
        userServerId = "166", username = "FrancoL", reportServerId = "6089", reportLocalId = "12",
        reportNumber = "7721", landName = "Land A", landLocalId = "88", landServerId = "30135",
        testIndex = "2", testServerId = "991"
    )

    @Test
    fun `flushWritesHaelFields - the calculation_audit_logs document hael writes today`() {
        val a = audit()
        a.begin(scope, "SojaPerTestCalculation")
        a.record(mapOf("method" to "calcStand", "returned" to 4.0))
        a.record(mapOf("method" to "calcStem", "returned" to 0.0))
        now = 1_500L
        a.flush()
        val doc = docs.single()
        assertEquals(
            setOf("user", "username", "reportServerId", "reportId", "landName", "landId", "reportNumber",
                "landIdServer", "landIds", "landNames", "testIndex", "testServerId", "calcClass", "appVersion",
                "applicationId", "client", "timestamp", "clientStartTimeMs", "clientEndTimeMs", "durationMs",
                "traceCount", "traces"),
            doc.keys
        )
        assertEquals("166", doc["user"])
        assertEquals("12", doc["reportId"])
        assertEquals("88", doc["landId"])
        assertEquals("30135", doc["landIdServer"])
        assertEquals(listOf("30135"), doc["landIds"])
        assertEquals(listOf("Land A"), doc["landNames"])
        assertEquals("SojaPerTestCalculation", doc["calcClass"])
        assertEquals("AGRIHOST_TEST", doc["client"])
        assertEquals("10.0.24", doc["appVersion"])
        assertEquals(1_000L, doc["clientStartTimeMs"])
        assertEquals(1_500L, doc["clientEndTimeMs"])
        assertEquals(500L, doc["durationMs"])
        assertEquals(2, doc["traceCount"])
        assertEquals(FieldValue.serverTimestamp(), doc["timestamp"])
    }

    @Test
    fun `blank land id and name give empty search arrays`() {
        val a = audit()
        a.begin(scope.copy(landServerId = "", landName = null), "X")
        a.record(mapOf("m" to 1))
        a.flush()
        assertEquals(emptyList<String>(), docs.single()["landIds"])
        assertEquals(emptyList<String>(), docs.single()["landNames"])
    }

    @Test
    fun `emptyTracesWriteNothing`() {
        val a = audit()
        a.begin(scope, "X")
        a.flush()
        assertTrue(docs.isEmpty())
    }

    @Test
    fun `flushResetsState - a second flush writes nothing`() {
        val a = audit()
        a.begin(scope, "X")
        a.record(mapOf("m" to 1))
        a.flush()
        a.flush()
        assertEquals(1, docs.size)
    }

    @Test
    fun `recordBeforeBeginIsIgnored`() {
        val a = audit()
        a.record(mapOf("m" to 1))
        a.flush()
        assertTrue(docs.isEmpty())
    }

    @Test
    fun `writerFailureNeverThrows into the calculation`() {
        val a = CalculationAudit({ throw RuntimeException("firestore down") }, { now }, { config })
        a.begin(scope, "X")
        a.record(mapOf("m" to 1))
        a.flush() // must not throw
    }
}
