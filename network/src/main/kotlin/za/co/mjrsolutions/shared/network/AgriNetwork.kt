package za.co.mjrsolutions.shared.network

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import za.co.mjrsolutions.shared.network.interceptor.AuthInterceptor
import za.co.mjrsolutions.shared.network.interceptor.LoggingInterceptor
import za.co.mjrsolutions.shared.network.interceptor.TransportInterceptor
import za.co.mjrsolutions.shared.network.outbox.ConnectivityMonitor
import za.co.mjrsolutions.shared.network.outbox.OutboxDao
import za.co.mjrsolutions.shared.network.outbox.OutboxDatabase
import za.co.mjrsolutions.shared.network.outbox.OutboxEntry
import za.co.mjrsolutions.shared.network.outbox.OutboxFlusher
import za.co.mjrsolutions.shared.network.outbox.OutboxOperation
import za.co.mjrsolutions.shared.network.outbox.OutboxReconciler
import za.co.mjrsolutions.shared.network.outbox.ServerIdExtractor
import za.co.mjrsolutions.shared.network.outbox.SyncWorker
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

object AgriNetwork {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    @Volatile private var app: Application? = null
    @Volatile private var config: AgriNetworkConfig? = null
    @Volatile private var client: OkHttpClient? = null
    @Volatile private var retrofit: Retrofit? = null

    @Volatile private var db: OutboxDatabase? = null
    @Volatile private var flusher: OutboxFlusher? = null
    @Volatile private var connectivity: ConnectivityMonitor? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Apps register these in later phases; defaults are safe no-ops. */
    @Volatile var reconciler: OutboxReconciler = OutboxReconciler.NONE
    @Volatile var serverIdExtractor: ServerIdExtractor = ServerIdExtractor.NONE

    @JvmStatic
    fun init(application: Application, cfg: AgriNetworkConfig) {
        app = application
        config = cfg

        // Interceptor add-order matters: Auth and Transport run on the way out FIRST, then
        // Logging (innermost) observes the final, fully-headed, fully-routed request — which
        // is why LoggingInterceptor redacts. Response bubbles back out in reverse.
        val okhttp = OkHttpClient.Builder()
            .connectTimeout(cfg.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(cfg.readTimeoutMs, TimeUnit.MILLISECONDS)
            // CLEARTEXT is required: some tenants use http:// UAT/test backends, and unit tests
            // hit MockWebServer over http. Production tenant URLs are https (MODERN/COMPATIBLE).
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .addInterceptor(AuthInterceptor(cfg.authHeaderProvider))
            .addInterceptor(TransportInterceptor(cfg.transport, cfg.functionsBaseUrl))
            .addInterceptor(LoggingInterceptor(cfg.logSink, cfg.debugLoggingProvider))
            .build()
        client = okhttp

        retrofit = Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(cfg.baseUrl))
            .client(okhttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val database = OutboxDatabase.get(application)
        db = database
        flusher = OutboxFlusher(
            dao = database.outboxDao(),
            executor = { request -> execute(request) },
            // Read the live reconciler/extractor at flush time, so apps may set them after init().
            reconciler = { localId, opType, serverId -> reconciler.onUploaded(localId, opType, serverId) },
            serverIdExtractor = { opType, body -> serverIdExtractor.extract(opType, body) }
        )

        schedulePeriodicSync(application, cfg.syncIntervalMinutesProvider())

        connectivity?.stop()
        connectivity = ConnectivityMonitor(application) { syncNow() }.also { it.start() }
    }

    /** Typed Retrofit service. Adopted per-endpoint by apps in later phases. */
    @JvmStatic
    fun <S> service(api: Class<S>): S =
        (retrofit ?: error("AgriNetwork.init(...) was not called")).create(api)

    /**
     * Synchronous call. MUST be invoked off the main thread (it blocks on I/O).
     * Reads use this directly; the outbox flusher uses it for writes.
     */
    @JvmStatic
    fun execute(request: AgriRequest): NetworkResult<String> {
        val cfg = config ?: return NetworkResult.NetworkError(IllegalStateException("not initialized"))
        val httpClient = client ?: return NetworkResult.NetworkError(IllegalStateException("not initialized"))

        val url = ensureTrailingSlash(cfg.baseUrl).trimEnd('/') + "/" + request.path.trimStart('/')
        val body = request.bodyJson?.toRequestBody(JSON)
        val builder = Request.Builder().url(url)
        when (request.method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.DELETE -> builder.delete(body)
            HttpMethod.POST -> builder.post(body ?: "".toRequestBody(JSON))
            HttpMethod.PUT -> builder.put(body ?: "".toRequestBody(JSON))
        }
        for ((k, v) in request.extraHeaders) builder.header(k, v)

        return try {
            httpClient.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string()
                if (resp.isSuccessful) NetworkResult.Success(text ?: "")
                else ErrorClassifier.fromResponse(resp.code, text)
            }
        } catch (e: IOException) {
            ErrorClassifier.fromException(e)
        }
    }

    /** Queue a write. Returns the local outbox id immediately; the upload happens later. */
    @JvmStatic
    fun enqueue(op: OutboxOperation): String {
        val database = db ?: error("AgriNetwork.init(...) was not called")
        val id = UUID.randomUUID().toString()
        val entry = OutboxEntry(
            id = id, createdAt = System.currentTimeMillis(), opType = op.opType,
            method = op.method.name, path = op.path, tenant = op.tenant,
            payloadJson = op.payloadJson, payloadFileRef = op.payloadFileRef,
            dependsOnId = op.dependsOnLocalId, status = "PENDING", attemptCount = 0,
            lastError = null, lastAttemptAt = null, serverIdResult = null
        )
        ioScope.launch { database.outboxDao().insert(entry) }
        syncNow()
        return id
    }

    /** Observe all outbox rows (newest first) — drive a "pending uploads" badge/list. */
    @JvmStatic
    fun observeOutbox(): LiveData<List<OutboxEntry>> =
        (db ?: error("AgriNetwork.init(...) was not called")).outboxDao().observeAll()

    /** Observe the count of not-yet-synced rows — drive the Sync button's visibility. */
    @JvmStatic
    fun observePendingCount(): LiveData<Int> =
        (db ?: error("AgriNetwork.init(...) was not called")).outboxDao().observePendingCount()

    /** Request an immediate drain (also called automatically on enqueue and on reconnect). */
    @JvmStatic
    fun syncNow() {
        val application = app ?: return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        try {
            WorkManager.getInstance(application)
                .enqueueUniqueWork(SyncWorker.UNIQUE_ONESHOT, ExistingWorkPolicy.REPLACE, request)
        } catch (e: IllegalStateException) {
            // WorkManager not initialised (e.g. some unit tests). Periodic/connectivity paths still cover sync.
        }
    }

    internal fun internalFlusher(): OutboxFlusher? = flusher
    internal fun outboxDaoForTest(): OutboxDao =
        (db ?: error("AgriNetwork.init(...) was not called")).outboxDao()

    private fun schedulePeriodicSync(application: Application, minutes: Long) {
        val safeMinutes = if (minutes < 15L) 15L else minutes   // WorkManager periodic floor
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(safeMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        try {
            WorkManager.getInstance(application)
                .enqueueUniquePeriodicWork(SyncWorker.UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        } catch (e: IllegalStateException) {
            // WorkManager not initialised in this context — safe to skip; init() still completes.
        }
    }

    private fun ensureTrailingSlash(url: String): String = if (url.endsWith("/")) url else "$url/"
}
