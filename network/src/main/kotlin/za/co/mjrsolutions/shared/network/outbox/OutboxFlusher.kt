package za.co.mjrsolutions.shared.network.outbox

import za.co.mjrsolutions.shared.network.AgriRequest
import za.co.mjrsolutions.shared.network.HttpMethod
import za.co.mjrsolutions.shared.network.NetworkResult

/**
 * Drains PENDING outbox rows in createdAt order. A row with an unmet dependency (its
 * dependsOnId is not yet DONE) is skipped this pass and retried next time, so a parent
 * (e.g. report create) always precedes its dependents (photos/expenses).
 *
 * Each send carries an "Idempotency-Key" header equal to the row id. Transient failures
 * (5xx / network / offline) keep the row PENDING and bump attemptCount; client errors (4xx)
 * and parse errors are terminal FAILED. WorkManager handles WHEN to re-run.
 *
 * @param now injected for deterministic tests; defaults to the wall clock.
 */
internal class OutboxFlusher(
    private val dao: OutboxDao,
    private val executor: RequestExecutor,
    private val reconciler: OutboxReconciler,
    private val serverIdExtractor: ServerIdExtractor,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    data class Summary(val sent: Int, val failed: Int, val skipped: Int, val remaining: Int) {
        val hadTransientFailure: Boolean get() = remaining > 0
    }

    suspend fun flush(): Summary {
        var sent = 0; var failed = 0; var skipped = 0

        for (entry in dao.pendingOrdered()) {
            if (!dependencySatisfied(entry)) { skipped++; continue }

            dao.markInFlight(entry.id, now())
            val request = AgriRequest(
                method = HttpMethod.valueOf(entry.method),
                path = entry.path,
                bodyJson = entry.payloadJson,
                extraHeaders = mapOf("Idempotency-Key" to entry.id)
            )

            when (val result = executor.execute(request)) {
                is NetworkResult.Success -> {
                    val serverId = serverIdExtractor.extract(entry.opType, result.data)
                    dao.markDone(entry.id, serverId, now())
                    reconciler.onUploaded(entry.id, entry.opType, serverId)
                    sent++
                }
                is NetworkResult.ServerError -> {
                    if (result.code in 400..499) { dao.markFailed(entry.id, "HTTP ${result.code}", now()); failed++ }
                    else dao.bumpAttempt(entry.id, "HTTP ${result.code}", now())
                }
                is NetworkResult.ParseError -> { dao.markFailed(entry.id, "parse: ${result.cause.message}", now()); failed++ }
                is NetworkResult.NetworkError -> dao.bumpAttempt(entry.id, "network: ${result.cause.message}", now())
                NetworkResult.Offline -> dao.bumpAttempt(entry.id, "offline", now())
                is NetworkResult.Queued -> { /* not produced by execute(); ignore */ }
            }
        }

        val remaining = dao.countByStatus("PENDING")
        return Summary(sent, failed, skipped, remaining)
    }

    private suspend fun dependencySatisfied(entry: OutboxEntry): Boolean {
        val depId = entry.dependsOnId ?: return true
        return dao.getById(depId)?.status == "DONE"
    }
}
