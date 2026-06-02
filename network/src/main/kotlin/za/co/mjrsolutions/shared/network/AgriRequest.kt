package za.co.mjrsolutions.shared.network

/**
 * A backend call expressed independently of OkHttp/Retrofit so call sites and the
 * outbox can describe requests without depending on the HTTP library.
 *
 * @param path appended to the configured baseUrl (leading slash optional).
 * @param bodyJson JSON body for POST/PUT; null for GET/DELETE.
 * @param extraHeaders per-request headers (merged on top of auth headers).
 */
data class AgriRequest(
    val method: HttpMethod,
    val path: String,
    val bodyJson: String? = null,
    val extraHeaders: Map<String, String> = emptyMap()
)
