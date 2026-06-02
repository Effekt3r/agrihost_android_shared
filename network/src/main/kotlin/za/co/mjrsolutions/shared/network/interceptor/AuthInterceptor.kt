package za.co.mjrsolutions.shared.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injects auth headers from [headerProvider] (wired to AgriAuth.getAuthHeaders() in later
 * phases). A header already present on the request wins, so per-call overrides are honoured.
 * Keeping this a lambda is what frees :network from any compile dependency on :auth.
 */
internal class AuthInterceptor(
    private val headerProvider: () -> Map<String, String>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        for ((name, value) in headerProvider()) {
            if (original.header(name) == null) {
                builder.header(name, value)
            }
        }
        return chain.proceed(builder.build())
    }
}
