package za.co.mjrsolutions.shared.network.log

/** Default-safe sink that discards everything (used in tests and when logging is off). */
object NoopLogSink : NetworkLogSink {
    override fun onCall(record: NetworkLogRecord) {}
    override fun onError(record: NetworkLogRecord, cause: Throwable) {}
}
