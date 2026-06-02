package za.co.mjrsolutions.shared.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import za.co.mjrsolutions.shared.network.log.NetworkLogRecord
import za.co.mjrsolutions.shared.network.log.NetworkLogSink
import java.io.IOException
import java.util.UUID

/**
 * Records one [NetworkLogRecord] per call. Added as the INNERMOST application interceptor
 * (see AgriNetwork) so it observes the final request after auth headers and transport
 * routing are applied — which is exactly why header values are redacted here.
 *
 * @param debugEnabled evaluated per call; when true, the (redacted) request headers are
 *        attached to the record. Wired to AppConfig's debugLoggingEnabled in later phases.
 */
internal class LoggingInterceptor(
    private val sink: NetworkLogSink,
    private val debugEnabled: () -> Boolean
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val correlationId = UUID.randomUUID().toString()
        val reqBytes = request.body?.contentLength()?.coerceAtLeast(0) ?: 0
        val headers = if (debugEnabled()) SensitiveData.redactHeaders(request.headers) else emptyMap()
        val started = System.nanoTime()

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            sink.onError(
                NetworkLogRecord(
                    correlationId, request.method, request.url.toString(),
                    code = null, durationMs = elapsedMs(started),
                    requestBytes = reqBytes, responseBytes = 0, redactedHeaders = headers
                ),
                e
            )
            throw e
        }

        sink.onCall(
            NetworkLogRecord(
                correlationId, request.method, request.url.toString(),
                code = response.code, durationMs = elapsedMs(started),
                requestBytes = reqBytes,
                responseBytes = response.body?.contentLength()?.coerceAtLeast(0) ?: 0,
                redactedHeaders = headers
            )
        )
        return response
    }

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000
}
