package za.co.mjrsolutions.shared.audit.internal

import za.co.mjrsolutions.shared.audit.AuditConfig
import za.co.mjrsolutions.shared.audit.AuditKind
import za.co.mjrsolutions.shared.audit.AuditReportInfo

/**
 * The whole audit pipeline for one log call. Each step is individually error-isolated:
 * a failed step is crash-reported and the next step still runs (legacy per-step
 * try-catch behaviour, spec §Internal Architecture).
 *
 * The admin push is NOT run inline: it is launched via [notifyLauncher] from the
 * messages-write commit callback (legacy parity — an offline sync defers its push until
 * connectivity returns, and a failed messages write sends no push at all). This also keeps
 * the audit dispatcher free: an offline commit pending for hours never blocks later logs.
 */
internal class AuditPipeline(
    private val config: AuditConfig,
    private val gateway: FirestoreGateway,
    private val tokenFactory: AccessTokenFactory,
    private val sendFn: (accessToken: String, admin: AdminToken, message: AuditMessage) -> Unit,
    private val clock: () -> Long,
    private val notifyLauncher: (suspend () -> Unit) -> Unit
) {
    private val factory = AuditEntryFactory(config)

    fun run(kind: AuditKind, payload: String?, success: Boolean, report: AuditReportInfo?) {
        val userName = AuditEntryFactory.normaliseUserName(
            runCatching { config.userNameProvider() }.getOrNull()
        )
        val millis = clock()
        val entry = factory.entry(kind, payload, success, report, userName, millis)
        val message = factory.message(kind, success, report, userName, entry.path, millis)

        runCatching { gateway.writeAuditEntry(entry) }.onFailure { Crash.record(it) }
        runCatching {
            gateway.writeMessage(message) {
                notifyLauncher { notifyAdmins(message) }
            }
        }.onFailure { Crash.record(it) }
    }

    private suspend fun notifyAdmins(message: AuditMessage) {
        runCatching {
            val admins = gateway.queryAdminTokens()
            if (admins.isEmpty()) return
            val json = gateway.fetchServiceAccountJson() ?: return
            val token = tokenFactory.token(json) ?: return
            admins.forEach { admin ->
                runCatching { sendFn(token, admin, message) }.onFailure { Crash.record(it) }
            }
        }.onFailure { Crash.record(it) }
    }
}
