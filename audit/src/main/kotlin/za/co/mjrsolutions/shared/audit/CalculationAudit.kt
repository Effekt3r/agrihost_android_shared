package za.co.mjrsolutions.shared.audit

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/** Who and what a calculation run is for. Every id as the app stores it (strings). */
data class CalcScope @JvmOverloads constructor(
    val userServerId: String? = null,
    val username: String? = null,
    val reportServerId: String? = null,
    val reportLocalId: String? = null,
    val reportNumber: String? = null,
    val landName: String? = null,
    val landLocalId: String? = null,
    val landServerId: String? = null,
    val testIndex: String? = null,
    val testServerId: String? = null
)

/**
 * One calculation run's trace for the console's Calculations tab (hael's CalculationAuditLogger,
 * shared): [begin], a [record] per traced step, then [flush] writes one calculation_audit_logs
 * document. Search fields: user + reportServerId (reportId before sync) + testIndex;
 * reportNumber, landIds and landNames are the console's indexed lookups.
 *
 * Never blocks the caller, never throws into the calculation; a failed write is reported to
 * Crashlytics and the run is dropped. Firestore queues the write while offline.
 */
class CalculationAudit internal constructor(
    private val writer: (Map<String, Any?>) -> Unit,
    private val clock: () -> Long,
    private val configProvider: () -> AuditConfig?
) {
    /** For the apps: writes to Firestore, versions and client from [AgriAudit.init]'s config. */
    constructor() : this(
        { doc -> FirebaseFirestore.getInstance().collection(COLLECTION).add(doc) },
        System::currentTimeMillis,
        { AgriAudit.config }
    )

    private var scope: CalcScope? = null
    private var calcClass: String? = null
    private var traces: MutableList<Map<String, Any?>>? = null
    private var startTime: Long = 0

    fun begin(scope: CalcScope?, calcClass: String?) {
        this.scope = scope
        this.calcClass = calcClass
        this.traces = ArrayList()
        this.startTime = clock()
    }

    fun record(trace: Map<String, Any?>?) {
        val list = traces ?: return
        if (trace != null) list.add(trace)
    }

    fun flush() {
        val tracesSnapshot = traces
        val s = scope ?: CalcScope()
        val calcClassSnapshot = calcClass
        val start = startTime
        traces = null
        scope = null
        calcClass = null
        try {
            if (tracesSnapshot.isNullOrEmpty()) return
            val config = configProvider()
            val end = clock()
            val doc = HashMap<String, Any?>()
            doc["user"] = s.userServerId
            doc["username"] = s.username
            doc["reportServerId"] = s.reportServerId
            doc["reportId"] = s.reportLocalId
            doc["landName"] = s.landName
            doc["landId"] = s.landLocalId
            doc["reportNumber"] = s.reportNumber
            doc["landIdServer"] = s.landServerId
            doc["landIds"] = if (s.landServerId.isNullOrEmpty()) emptyList<String>() else listOf(s.landServerId)
            doc["landNames"] = if (s.landName.isNullOrEmpty()) emptyList<String>() else listOf(s.landName)
            doc["testIndex"] = s.testIndex
            doc["testServerId"] = s.testServerId
            doc["calcClass"] = calcClassSnapshot
            doc["appVersion"] = config?.appVersion ?: ""
            doc["applicationId"] = config?.applicationId ?: ""
            doc["client"] = config?.clientId ?: ""
            doc["timestamp"] = FieldValue.serverTimestamp()
            doc["clientStartTimeMs"] = start
            doc["clientEndTimeMs"] = end
            doc["durationMs"] = end - start
            doc["traceCount"] = tracesSnapshot.size
            doc["traces"] = tracesSnapshot
            writer(doc)
        } catch (t: Throwable) {
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                    .recordException(RuntimeException("CalculationAudit.flush failed", t))
            } catch (ignored: Throwable) {
                try { Log.e("CalculationAudit", "flush failed", t) } catch (alsoIgnored: Throwable) { }
            }
        }
    }

    companion object {
        const val COLLECTION = "calculation_audit_logs"
    }
}
