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
    private val scope = CoroutineScope(
        SupervisorJob() + java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "agri-audit").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    )

    @JvmStatic
    fun init(app: Application, config: AuditConfig) {
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

    /** GetFromServer: call from get-reports response handlers (success and failure). */
    @JvmStatic
    fun logGetFromServer(payload: String?, success: Boolean) =
        log(AuditKind.GET_FROM_SERVER, payload, success, null)

    /** SyncToServer: call from save-report response handlers (success and failure). */
    @JvmStatic
    fun logSyncToServer(payload: String?, success: Boolean, report: AuditReportInfo) =
        log(AuditKind.SYNC_TO_SERVER, payload, success, report)

    private fun log(kind: AuditKind, payload: String?, success: Boolean, report: AuditReportInfo?) {
        val p = pipeline ?: return   // pre-init call: deliberate silent no-op
        scope.launch {
            runCatching { p.run(kind, payload, success, report) }.onFailure { Crash.record(it) }
        }
    }

    @VisibleForTesting
    internal fun resetForTest() { pipeline = null }
}
