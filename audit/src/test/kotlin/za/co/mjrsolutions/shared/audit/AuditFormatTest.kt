package za.co.mjrsolutions.shared.audit

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditFormatTest {

    @Test
    fun `isServerAccepted - success false in a 200 is a failure (hael PhotoSlotsTest cases)`() {
        assertFalse(AuditFormat.isServerAccepted("{\"success\":false,\"message\":\"Land not found.\",\"land_id\":\"1\"}"))
        assertFalse(AuditFormat.isServerAccepted("{\"success\":\"false\"}"))
        assertFalse(AuditFormat.isServerAccepted("{\"success\":0}"))
        assertTrue(AuditFormat.isServerAccepted("{\"success\":true,\"message\":\"Image stored successfully!\"}"))
        assertTrue(AuditFormat.isServerAccepted(""))
        assertTrue(AuditFormat.isServerAccepted(null))
        assertTrue(AuditFormat.isServerAccepted("not json"))
        assertTrue(AuditFormat.isServerAccepted("{\"message\":\"ok\"}"))
    }

    @Test
    fun `serverMessage - the message, or the raw text cut at 500`() {
        assertEquals("Land not found.", AuditFormat.serverMessage("{\"success\":false,\"message\":\"Land not found.\"}"))
        assertEquals("plain", AuditFormat.serverMessage("plain"))
        assertEquals("", AuditFormat.serverMessage(null))
    }

    @Test
    fun `segment and stamp - hael's Storage name rules`() {
        assertEquals("unknown", AuditFormat.segment(".."))
        assertEquals("Erasmus_Jan", AuditFormat.segment("Erasmus, Jan"))
        assertEquals("20261105-101230", AuditFormat.stamp(1_793_866_350_000L))
        assertEquals("unknown", AuditFormat.stamp(0))
    }
}
