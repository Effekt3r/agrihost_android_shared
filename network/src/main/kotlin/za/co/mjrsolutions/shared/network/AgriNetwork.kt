package za.co.mjrsolutions.shared.network

import android.app.Application
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
import java.io.IOException
import java.util.concurrent.TimeUnit

object AgriNetwork {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    @Volatile private var app: Application? = null
    @Volatile private var config: AgriNetworkConfig? = null
    @Volatile private var client: OkHttpClient? = null
    @Volatile private var retrofit: Retrofit? = null

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

    private fun ensureTrailingSlash(url: String): String = if (url.endsWith("/")) url else "$url/"
}
