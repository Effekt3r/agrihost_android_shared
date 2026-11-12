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
    private val consoleFactory = ConsoleEntryFactory(config)
    private val lastId = java.util.concurrent.atomic.AtomicLong()

    /**
     * A photo, photo-upload or PDF entry: built by [build] with the full name (console folder),
     * the login id and the clock, then written. No messages doc and no admin push.
     */
    fun runConsole(build: (factory: ConsoleEntryFactory, userName: String, loginId: String, millis: Long) -> AuditEntry) {
        val userName = AuditEntryFactory.normaliseUserName(
            runCatching { config.userNameProvider() }.getOrNull()
        )
        val loginId = runCatching { config.loginIdProvider() }.getOrNull() ?: ""
        val entry = build(consoleFactory, userName, loginId, clock())
        runCatching { gateway.writeAuditEntry(entry) }.onFailure { Crash.record(it) }
    }

    /** Device time in ms, like the send entries' ids, but never the same id twice. */
    fun nextId(): Long {
        val now = clock()
        while (true) {
            val last = lastId.get()
            val next = maxOf(now, last + 1)
            if (lastId.compareAndSet(last, next)) return next
        }
    }

    fun run(kind: AuditKind, payload: String?, success: Boolean, report: AuditReportInfo?,
            endpoint: String? = null) {
        val userName = AuditEntryFactory.normaliseUserName(
            runCatching { config.userNameProvider() }.getOrNull()
        )
        val millis = clock()
        val entry = factory.entry(kind, payload, success, report, userName, millis, endpoint)
        val message = factory.message(kind, success, report, userName, entry.path, millis)

        runCatching { gateway.writeAuditEntry(entry) }.onFailure { Crash.record(it) }
        runCatching {
            gateway.writeMessage(message) {
                notifyLauncher { notifyAdmins(message) }
            }
        }.onFailure { Crash.record(it) }
    }

    /** Sync failure: writes ONLY the failure entry. No messages doc and no admin push
     *  (failures surface in the console Sync Failures tab, not as per-failure pushes). */
    fun runFailure(requestBody: String?, httpStatus: Int, endpoint: String?,
                   reason: String?, serverMessage: String?, report: AuditReportInfo?) {
        val userName = AuditEntryFactory.normaliseUserName(
            runCatching { config.userNameProvider() }.getOrNull()
        )
        val entry = factory.failureEntry(
            requestBody, httpStatus, endpoint, reason, serverMessage, report, userName, clock()
        )
        runCatching { gateway.writeAuditEntry(entry) }.onFailure { Crash.record(it) }
    }

    private suspend fun notifyAdmins(message: AuditMessage) {
        runCatching {
            val admins = gateway.queryAdminTokens()
            if (admins.isEmpty()) return
            val json = gateway.fetchServiceAccountJson() ?: return
            val token = tokenFactory.token(json)
            if (token == null) {
                Crash.record(Exception("FCM access token null"))   // legacy parity: recorded, not silent
                return
            }
            admins.forEach { admin ->
                runCatching { sendFn(token, admin, message) }.onFailure { Crash.record(it) }
            }
        }.onFailure { Crash.record(it) }
    }
}
