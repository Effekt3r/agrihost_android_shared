package za.co.mjrsolutions.shared.network.interceptor

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import za.co.mjrsolutions.shared.network.Transport

/**
 * The transport swap seam. In DIRECT mode it is a pass-through. In FUNCTIONS_GATEWAY mode
 * (deferred — Phase H) it rewrites scheme/host/port to [functionsBaseUrl] and prefixes the
 * original encoded path, so the call lands on a Cloud Function instead of the REST host —
 * with zero changes at the call site. Implemented and tested now; no app enables it yet.
 */
internal class TransportInterceptor(
    private val transport: Transport,
    private val functionsBaseUrl: String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (transport != Transport.FUNCTIONS_GATEWAY || functionsBaseUrl == null) {
            return chain.proceed(request)
        }
        val base = functionsBaseUrl.toHttpUrlOrNull() ?: return chain.proceed(request)
        val original = request.url

        val rewritten = original.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(joinPaths(base.encodedPath, original.encodedPath))
            .build()

        return chain.proceed(request.newBuilder().url(rewritten).build())
    }

    private fun joinPaths(prefix: String, path: String): String {
        val p = prefix.trimEnd('/')
        val s = if (path.startsWith("/")) path else "/$path"
        return "$p$s"
    }
}
