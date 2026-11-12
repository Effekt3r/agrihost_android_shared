package za.co.mjrsolutions.shared.audit

import android.app.Application
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import za.co.mjrsolutions.shared.audit.internal.AccessTokenFactory
import za.co.mjrsolutions.shared.audit.internal.AuditPipeline
import za.co.mjrsolutions.shared.audit.internal.Crash
import za.co.mjrsolutions.shared.audit.internal.FcmSender
import za.co.mjrsolutions.shared.audit.internal.FirebaseFirestoreGateway
import za.co.mjrsolutions.shared.audit.internal.FirestoreGateway

/**
 * Shared Firestore audit log: per server interaction writes the GetFromServer/SyncToServer
 * entry, the staff `messages` doc, and pushes to admin users. Fire-and-forget; never
 * throws into the caller; an audit failure must never break a sync.
 *
 * The only exception: [logSyncToServer]'s report is non-null by contract — Java callers
 * must construct an [AuditReportInfo] (fields may be null) rather than pass null.
 *
 * Init from Application.onCreate:
 *   AgriAudit.init(this, AuditConfig(BuildConfig.FLAVOR + "_vrugte", { userName() }))
 */
object AgriAudit {

    @Volatile private var pipeline: AuditPipeline? = null

    /** The config given to [init]; read by AgriUserRecord and CalculationAudit. */
    @Volatile internal var config: AuditConfig? = null
        private set
    private val scope = CoroutineScope(
        SupervisorJob() + java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "agri-audit").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    )

    @JvmStatic
    fun init(app: Application, config: AuditConfig) {
        this.config = config
        val gateway: FirestoreGateway = FirebaseFirestoreGateway()
        val sender = FcmSender(onStaleToken = { userId ->
            gateway.clearStaleToken(userId, config.bundleId, config.staleBundleTokenField)
        })
        pipeline = AuditPipeline(
            config = config, gateway = gateway,
            tokenFactory = AccessTokenFactory.GOOGLE,
            sendFn = { token, admin, message -> sender.send(token, admin, message) },
            clock = System::currentTimeMillis,
            notifyLauncher = { block ->
                scope.launch { runCatching { block() }.onFailure { Crash.record(it) } }
            }
        )
    }

    /** GetFromServer: call from get-reports response handlers (success and failure).
     *  [endpoint] is the request URL — surfaced in the console so a response can be tied
     *  to the API call it came from. Older 2-arg callers keep working (endpoint = null). */
    @JvmOverloads
    @JvmStatic
    fun logGetFromServer(payload: String?, success: Boolean, endpoint: String? = null) =
        log(AuditKind.GET_FROM_SERVER, payload, success, null, endpoint)

    /** SyncToServer: call from save-report response handlers (success and failure). */
    @JvmStatic
    fun logSyncToServer(payload: String?, success: Boolean, report: AuditReportInfo) =
        log(AuditKind.SYNC_TO_SERVER, payload, success, report)

    /** SyncFailure: call from sync error handlers (actionable failures only). Writes the
     *  failure entry to SyncFailures/...; no messages doc / no admin push. */
    @JvmStatic
    fun logSyncFailure(requestBody: String?, httpStatus: Int, endpoint: String?,
                       reason: String?, serverMessage: String?, report: AuditReportInfo) {
        val p = pipeline ?: return
        scope.launch {
            runCatching {
                p.runFailure(requestBody, httpStatus, endpoint, reason, serverMessage, report)
            }.onFailure { Crash.record(it) }
        }
    }

    /** A photo's copy is in Storage: its "photo" entry on the report's console timeline. */
    @JvmStatic
    fun logPhoto(entry: PhotoLogEntry) = console { p ->
        p.runConsole { f, user, login, ms -> f.photo(entry, user, login, ms) }
    }

    /** A photo upload to the server finished: a "photo_upload" entry with the server's answer. */
    @JvmStatic
    fun logPhotoUpload(entry: PhotoUploadLogEntry) = console { p ->
        p.runConsole { f, user, login, _ -> f.photoUpload(entry, user, login, p.nextId().toString()) }
    }

    /** A PDF's copy is in Storage: its "pdf" entry, rewritten when a changed PDF is copied again. */
    @JvmStatic
    fun logPdf(entry: PdfLogEntry) = console { p ->
        p.runConsole { f, user, login, ms -> f.pdf(entry, user, login, ms) }
    }

    /** Built and written on the caller's thread (the Firestore write itself is asynchronous), so a
     *  caller that reuses its entry object afterwards cannot change what was logged. */
    private fun console(block: (AuditPipeline) -> Unit) {
        val p = pipeline ?: return   // pre-init call: deliberate silent no-op
        runCatching { block(p) }.onFailure { Crash.record(it) }
    }

    private fun log(kind: AuditKind, payload: String?, success: Boolean, report: AuditReportInfo?,
                    endpoint: String? = null) {
        val p = pipeline ?: return   // pre-init call: deliberate silent no-op
        scope.launch {
            runCatching { p.run(kind, payload, success, report, endpoint) }.onFailure { Crash.record(it) }
        }
    }

    @VisibleForTesting
    internal fun resetForTest() { pipeline = null; config = null }
}
