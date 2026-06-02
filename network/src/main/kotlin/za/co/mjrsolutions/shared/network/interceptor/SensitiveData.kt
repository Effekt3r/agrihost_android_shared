package za.co.mjrsolutions.shared.network.interceptor

import okhttp3.Headers

/** Masks credentials before any header map is handed to a log sink. */
internal object SensitiveData {

    const val REDACTED = "***"

    private val SENSITIVE_NAMES = setOf("authorization", "x-api-key")

    fun isSensitive(name: String): Boolean {
        val n = name.lowercase()
        return n in SENSITIVE_NAMES || n.contains("password")
    }

    fun redactHeaders(headers: Headers): Map<String, String> {
        val out = LinkedHashMap<String, String>(headers.size)
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            out[name] = if (isSensitive(name)) REDACTED else headers.value(i)
        }
        return out
    }
}
