package za.co.mjrsolutions.shared.audit.internal

import com.google.firebase.firestore.FieldValue
import org.junit.Test
import za.co.mjrsolutions.shared.audit.AuditConfig
import za.co.mjrsolutions.shared.audit.PdfLogEntry
import za.co.mjrsolutions.shared.audit.PhotoLogEntry
import za.co.mjrsolutions.shared.audit.PhotoUploadLogEntry
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleEntryFactoryTest {

    private val config = AuditConfig(
        clientId = "AGRIHOST_TEST", userNameProvider = { "Jan Botha" },
        loginIdProvider = { "janb" }, appVersion = "10.0.24", applicationId = "za.co.x.agrihostnewapi"
    )
    private val factory = ConsoleEntryFactory(config)

    // 2026-11-12 10:15:00 SAST
    private val takenAt = 1_794_471_300_000L

    private fun photo() = PhotoLogEntry().apply {
        reportNumber = "7721"; reportServerId = "6089"; landId = "30135"; landName = "Land A"
        farmName = "Plaas"; crop = "Mielies"; testIndex = "1"; slot = 1; mediaId = 1272L
        takenAtMs = takenAt; takenAtSource = "capture"; gps = "-26.1,28.0"; gpsSource = "capture"
        sentToServer = false; storagePath = "hael_photos/AGRIHOST_TEST/7721/x.jpg"
        photoUrl = "https://firebasestorage.googleapis.com/v0/b/agrihost-7970a/o/x"; sizeBytes = 394_000L
    }

    @Test
    fun `photoEntryMatchesHaelShape - same path, doc id and fields as hael's PhotoSyncLog`() {
        val e = factory.photo(photo(), userName = "Jan Botha", loginId = "janb", millis = 9_000L)
        assertEquals("/SyncToServer/AGRIHOST_TEST/Jan Botha/7721/entries/photo_1272", e.path)
        assertEquals(
            setOf("type", "request", "timestamp", "client", "username", "userName", "appVersion",
                "reportNumber", "reportServerId", "landId", "landName", "farmName", "crop", "testIndex",
                "photoSlot", "mediaId", "takenAtMs", "takenAtSource", "gps", "gpsSource", "sentToServer",
                "photoUrl", "storagePath", "sizeBytes", "backedUpAtMs"),
            e.payload.keys
        )
        assertEquals("photo", e.payload["type"])
        assertEquals("AGRIHOST_TEST", e.payload["client"])
        assertEquals("janb", e.payload["username"])
        assertEquals("Jan Botha", e.payload["userName"])
        assertEquals("10.0.24", e.payload["appVersion"])
        assertEquals("1272", e.payload["mediaId"])
        assertEquals(1, e.payload["photoSlot"])
        assertEquals(9_000L, e.payload["backedUpAtMs"])
        assertEquals(FieldValue.serverTimestamp(), e.payload["timestamp"])
        val request = e.payload["request"] as String
        assertTrue(request.contains("\"type\": \"photo\""), request)
        assertTrue(request.contains("\"taken_at\": \"20261112-101500\""), request)
        assertTrue(request.contains("\"media_id\": \"1272\""), request)
        assertTrue(request.indexOf("\"report_number\"") < request.indexOf("\"media_id\""), "hael's field order")
    }

    @Test
    fun `photoEntryUsesPhotoIdWhenSet - photos outside the gallery get their own id`() {
        val p = photo().apply { mediaId = 0L; photoId = "mass_12_3_1762935300000" }
        val e = factory.photo(p, "Jan Botha", "janb", 1L)
        assertEquals("photo_mass_12_3_1762935300000", e.docId)
    }

    @Test
    fun `blankReportGoesToUnknown - blank, spaces and the text null`() {
        for (r in listOf(null, "", "   ", "null")) {
            val e = factory.photo(photo().apply { reportNumber = r }, "Jan Botha", "janb", 1L)
            assertEquals("Unknown", e.segment, "report '$r'")
        }
        val unlinked = factory.photo(photo().apply { reportNumber = "_unlinked" }, "Jan Botha", "janb", 1L)
        assertEquals("_unlinked", unlinked.segment)
    }

    @Test
    fun `photoUploadEntryShape - what was sent and what the server said`() {
        val upload = PhotoUploadLogEntry().apply {
            reportNumber = "7721"; reportServerId = "6089"; landId = "30135"; landName = "Land A"
            testIndex = "1_u"; uitstel = true
            images = listOf(mapOf("slot" to 1, "kb" to 380))
            accepted = false
            serverResponse = "{\"success\":false,\"message\":\"Report type report land not found.\"}"
            endpoint = "https://dev.agrihost.co.za/api/report/store-image"
        }
        val e = factory.photoUpload(upload, "Jan Botha", "janb", docId = "8000")
        assertEquals("/SyncToServer/AGRIHOST_TEST/Jan Botha/7721/entries/8000", e.path)
        assertEquals("photo_upload", e.payload["type"])
        assertEquals(1, e.payload["imageCount"])
        assertEquals(false, e.payload["success"])
        assertEquals("failure", e.payload["status"])
        assertEquals(true, e.payload["uitstel"])
        assertEquals("1_u", e.payload["testIndex"])
        assertEquals("Report type report land not found.", e.payload["response"])
        val request = e.payload["request"] as String
        assertTrue(request.contains("\"report_id\": \"6089\""), request)
        assertTrue(request.contains("\"endpoint\": \"https://dev.agrihost.co.za/api/report/store-image\""), request)
    }

    @Test
    fun `photoUploadResponseCappedAt500 when the server sent no message`() {
        val upload = PhotoUploadLogEntry().apply { reportNumber = "1"; serverResponse = "e".repeat(800); accepted = true }
        val e = factory.photoUpload(upload, "Jan Botha", "janb", "1")
        assertEquals(500, (e.payload["response"] as String).length)
        assertEquals("success", e.payload["status"])
        assertEquals(0, e.payload["imageCount"])
    }

    @Test
    fun `pdfEntryShape - one entry per file name, rewritten when the PDF changes`() {
        val pdf = PdfLogEntry().apply {
            reportNumber = "40068"; reportServerId = "40068"; fileName = "Emergence _ 40068 _ Kliënt.pdf"
            kind = "report"; landName = null; landId = null
            storagePath = "mr_pdfs/AGRIHOST_TEST_mr/40068/Emergence___40068___Kli_nt.pdf"
            pdfUrl = "https://firebasestorage.googleapis.com/v0/b/agrihost-7970a/o/y"
            sizeBytes = 120_000L; contentHash = "abc123"; generatedAtMs = takenAt
        }
        val e = factory.pdf(pdf, "Jan Botha", "janb", 5_000L)
        assertEquals("pdf_Emergence___40068___Kli_nt.pdf", e.docId)
        assertEquals("/SyncToServer/AGRIHOST_TEST/Jan Botha/40068/entries/pdf_Emergence___40068___Kli_nt.pdf", e.path)
        assertEquals("pdf", e.payload["type"])
        assertEquals("Emergence _ 40068 _ Kliënt.pdf", e.payload["fileName"])
        assertEquals("report", e.payload["kind"])
        assertEquals("abc123", e.payload["contentHash"])
        assertEquals(takenAt, e.payload["generatedAtMs"])
        assertEquals(5_000L, e.payload["backedUpAtMs"])
        assertEquals("", e.payload["landName"])
        val request = e.payload["request"] as String
        assertTrue(request.contains("\"generated_at\": \"20261112-101500\""), request)
    }
}
