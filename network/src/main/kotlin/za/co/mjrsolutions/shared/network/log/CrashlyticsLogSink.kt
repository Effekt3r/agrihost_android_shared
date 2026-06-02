package za.co.mjrsolutions.shared.network.log

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Writes a breadcrumb per call and records a non-fatal on error.
 * firebase-crashlytics is compileOnly here — consumer apps already bundle it.
 */
object CrashlyticsLogSink : NetworkLogSink {

    override fun onCall(record: NetworkLogRecord) {
        FirebaseCrashlytics.getInstance().log(
            "NET ${record.method} ${record.url} -> ${record.code ?: "?"} " +
                "(${record.durationMs}ms, ${record.responseBytes}b) [${record.correlationId}]"
        )
    }

    override fun onError(record: NetworkLogRecord, cause: Throwable) {
        val fc = FirebaseCrashlytics.getInstance()
        fc.log("NET-ERR ${record.method} ${record.url} [${record.correlationId}]")
        fc.recordException(cause)
    }
}
