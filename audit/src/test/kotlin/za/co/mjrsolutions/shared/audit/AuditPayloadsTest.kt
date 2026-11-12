package za.co.mjrsolutions.shared.audit

import org.junit.Test
import za.co.mjrsolutions.shared.audit.internal.AuditEntryFactory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuditPayloadsTest {

    private fun base64(chars: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder(chars)
        for (i in 0 until chars) sb.append(alphabet[(i * 7 + 3) % alphabet.length])
        return sb.toString()
    }

    @Test
    fun `stripsLongBase64StringValue - a photo becomes hael's note`() {
        val photo = base64(120_000)
        val json = "{\"land\":\"OS7\",\"landClosedPhoto1\":\"$photo\",\"n\":1}"
        // KB = round(120000 * 3 / 4 / 1024) = round(87.89) = 88
        assertEquals(
            "{\"land\":\"OS7\",\"landClosedPhoto1\":\"[photo, 88 KB, not kept in this log]\",\"n\":1}",
            AuditPayloads.forLog(json)
        )
    }

    @Test
    fun `keepsBase64Signature - values under the photo size stay`() {
        val signature = base64(40_000)
        val json = "{\"insuredSignature\":\"$signature\"}"
        assertEquals(json, AuditPayloads.forLog(json))
    }

    @Test
    fun `stripsEscapedSlashAndNewlineBase64 - org json and Base64 DEFAULT spellings`() {
        // org.json escapes '/' as \/ and Base64.DEFAULT wraps lines with \n (escaped in JSON).
        val chunk = base64(76).replace("/", "\\/")
        val sb = StringBuilder()
        while (sb.length < 130_000) sb.append(chunk).append("\\n")
        val value = sb.toString()
        val json = "{\"photo_json\":\"$value\"}"
        val out = AuditPayloads.forLog(json)!!
        assertTrue(out.startsWith("{\"photo_json\":\"[photo, "), out.take(80))
        assertTrue(out.endsWith(" KB, not kept in this log]\"}"), out.takeLast(80))
    }

    @Test
    fun `keepsNonBase64LongText - long prose is not a photo`() {
        val prose = "Geen skade. ".repeat(12_000) // 144,000 chars with spaces and dots
        val json = "{\"generalComment\":\"$prose\"}"
        assertEquals(json, AuditPayloads.forLog(json))
    }

    @Test
    fun `stripsMoreThanOnePhotoAndKeepsTheRest`() {
        val a = base64(110_000)
        val b = base64(200_000)
        val json = "[{\"image\":\"$a\",\"slot\":1},{\"image\":\"$b\",\"slot\":2}]"
        assertEquals(
            "[{\"image\":\"[photo, 81 KB, not kept in this log]\",\"slot\":1}," +
                "{\"image\":\"[photo, 146 KB, not kept in this log]\",\"slot\":2}]",
            AuditPayloads.forLog(json)
        )
    }

    @Test
    fun `escapedQuotesInsideStringsDoNotConfuseTheScan`() {
        val photo = base64(100_000)
        val json = "{\"note\":\"he said \\\"ok\\\"\",\"p\":\"$photo\"}"
        assertEquals(
            "{\"note\":\"he said \\\"ok\\\"\",\"p\":\"[photo, 73 KB, not kept in this log]\"}",
            AuditPayloads.forLog(json)
        )
    }

    @Test
    fun `capsOversizedBody - nothing is lost to the 1 MiB document limit`() {
        val text = "x".repeat(1_200_000)
        val out = AuditPayloads.forLog(text)!!
        assertEquals("x".repeat(900_000) + "\n[log cut at 900000 of 1200000 characters]", out)
    }

    @Test
    fun `nullAndEmptyPassThrough`() {
        assertNull(AuditPayloads.forLog(null))
        assertEquals("", AuditPayloads.forLog(""))
        assertEquals("plain", AuditPayloads.forLog("plain"))
    }

    @Test
    fun `entryFactoryAppliesSanitizer to the request of every kind`() {
        val factory = AuditEntryFactory(AuditConfig(clientId = "AGRIHOST_TEST", userNameProvider = { "Jan" }))
        val json = "{\"landClosedPhoto1\":\"${base64(120_000)}\"}"
        val expected = "{\"landClosedPhoto1\":\"[photo, 88 KB, not kept in this log]\"}"
        val sync = factory.entry(AuditKind.SYNC_TO_SERVER, json, true,
            AuditReportInfo("7721", "6089", "12"), "Jan", 1L)
        assertEquals(expected, sync.payload["request"])
        val get = factory.entry(AuditKind.GET_FROM_SERVER, json, true, null, "Jan", 1L)
        assertEquals(expected, get.payload["request"])
        val failure = factory.failureEntry(json, 500, "/x", "r", "m",
            AuditReportInfo("7721", null, null), "Jan", 1L)
        assertEquals(expected, failure.payload["request"])
    }
}
