package za.co.mjrsolutions.shared.network.log

/** One line of structured telemetry per call. Header values are already redacted. */
data class NetworkLogRecord(
    val correlationId: String,
    val method: String,
    val url: String,
    val code: Int?,
    val durationMs: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val redactedHeaders: Map<String, String>
)
