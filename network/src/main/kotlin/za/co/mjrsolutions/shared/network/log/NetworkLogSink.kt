package za.co.mjrsolutions.shared.network.log

/** Pluggable destination for network telemetry. Implementations must be thread-safe. */
interface NetworkLogSink {
    fun onCall(record: NetworkLogRecord)
    fun onError(record: NetworkLogRecord, cause: Throwable)
}
