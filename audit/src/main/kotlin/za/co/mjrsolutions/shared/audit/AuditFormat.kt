package za.co.mjrsolutions.shared.audit

import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Small formatting rules every console entry and Storage name shares (hael's PhotoSlots rules). */
object AuditFormat {

    private val SA_TIME: TimeZone = TimeZone.getTimeZone("Africa/Johannesburg")

    /** Blank, spaces only, or the text "null" (what a null String becomes once concatenated). */
    @JvmStatic
    fun isBlank(value: String?): Boolean =
        value == null || value.trim().isEmpty() || value.trim() == "null"

    /** yyyyMMdd-HHmmss in South African time, or "unknown". */
    @JvmStatic
    fun stamp(ms: Long): String {
        if (ms <= 0) return "unknown"
        val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        format.timeZone = SA_TIME
        return format.format(Date(ms))
    }

    /** A path segment: letters, digits, dot, dash and underscore only, never empty, never "." or "..". */
    @JvmStatic
    fun segment(value: String?): String {
        var segment = (value ?: "").trim().replace(Regex("[^A-Za-z0-9._-]+"), "_")
        segment = segment.replace(Regex("^\\.+"), "")
        return segment.ifEmpty { "unknown" }
    }

    /** The server's "message" from a JSON answer, else the raw text cut at 500 characters. */
    @JvmStatic
    fun serverMessage(responseBody: String?): String {
        if (isBlank(responseBody)) return ""
        val body = responseBody!!
        try {
            val root = JsonParser.parseString(body)
            if (root.isJsonObject) {
                val message = root.asJsonObject.get("message")
                if (message != null && message.isJsonPrimitive) return message.asString
            }
        } catch (ignored: RuntimeException) {
            // not JSON: the raw text below
        }
        return if (body.length > 500) body.substring(0, 500) else body
    }

    /**
     * True when the server kept the photos. The Agrihost photo endpoints answer HTTP 200 for their
     * own failures too ("Land not found.", "Image upload failed.", ...) with "success": false in
     * the body, so a 200 alone does not mean the photos arrived. An empty or non-JSON body is
     * taken as accepted (nothing says it failed).
     */
    @JvmStatic
    fun isServerAccepted(responseBody: String?): Boolean {
        if (isBlank(responseBody)) return true
        return try {
            val root = JsonParser.parseString(responseBody)
            if (!root.isJsonObject) return true
            val success = root.asJsonObject.get("success")
            if (success == null || success.isJsonNull || !success.isJsonPrimitive) return true
            val value = success.asJsonPrimitive
            if (value.isBoolean) value.asBoolean
            else value.asString.trim().let { !(it.equals("false", ignoreCase = true) || it == "0") }
        } catch (e: RuntimeException) {
            true // not JSON: the endpoints' own failures are always JSON
        }
    }

    /** A report number as a console path segment: blank becomes "Unknown". */
    @JvmStatic
    fun reportSegment(reportNumber: String?): String =
        if (isBlank(reportNumber)) "Unknown" else reportNumber!!.trim()
}
