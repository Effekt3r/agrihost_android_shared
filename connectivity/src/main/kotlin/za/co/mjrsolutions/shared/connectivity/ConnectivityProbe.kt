package za.co.mjrsolutions.shared.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

/**
 * Walks a ladder of increasingly expensive checks and stops at the first one that fails:
 *
 *     no active network                 -> NO_TRANSPORT        (0 sockets)
 *     not NET_CAPABILITY_VALIDATED      -> NO_INTERNET         (0 sockets)
 *     HEAD baseUrl answered             -> ONLINE
 *     HEAD baseUrl failed               -> neutral 204 check decides between
 *                                          SERVER_UNREACHABLE and NO_INTERNET
 *
 * The first two rungs do no network I/O at all, which is what a field tablet sitting with
 * its radios off needs: it costs nothing to keep saying "data is off".
 *
 * The internet verdict is only needed when the server ping has ALREADY failed, and it is
 * the same answer for every tick of a sustained outage, so it is cached for
 * [INTERNET_VERDICT_TTL_MS]. [invalidateInternetVerdict] drops it when the default network
 * changes, because a verdict taken on the previous network says nothing about this one.
 */
class ConnectivityProbe internal constructor(
    private val transport: () -> TransportStatus,
    private val head: (url: String, expectedCode: Int) -> Boolean,
    private val clock: () -> Long,
    private val onError: (Throwable) -> Unit,
) : ConnectivityProber {

    constructor(context: Context) : this(
        transport = { systemTransport(context.applicationContext) },
        head = { url, code -> respondsToHead(url, code) },
        clock = { System.currentTimeMillis() },
        onError = { recordNonFatal(it) },
    )

    @Volatile private var cachedHasInternet = false
    @Volatile private var cachedHasInternetAt = 0L

    /** Background thread only: the last two rungs open sockets. */
    override fun probe(baseUrl: String): ConnectivityState {
        val status = try {
            transport()
        } catch (t: Throwable) {
            onError(t)
            TransportStatus.NONE
        }
        return when (status) {
            TransportStatus.NONE -> ConnectivityState.NO_TRANSPORT
            TransportStatus.ATTACHED_UNVALIDATED -> ConnectivityState.NO_INTERNET
            TransportStatus.VALIDATED -> when {
                head(baseUrl, ANY_RESPONSE) -> ConnectivityState.ONLINE
                // VALIDATED can be optimistic (a metered network out of bundle, a carrier
                // redirect), so confirm against a neutral endpoint before blaming the
                // office's server rather than the assessor's own connection.
                hasRealInternet() -> ConnectivityState.SERVER_UNREACHABLE
                else -> ConnectivityState.NO_INTERNET
            }
        }
    }

    override fun invalidateInternetVerdict() {
        cachedHasInternetAt = 0L
    }

    private fun hasRealInternet(): Boolean {
        val takenAt = cachedHasInternetAt
        if (takenAt != 0L && clock() - takenAt < INTERNET_VERDICT_TTL_MS) {
            return cachedHasInternet
        }
        // A blocked check endpoint reads as "no internet". That is the safer of the two wrong
        // answers: it points the assessor at their own signal instead of at the office.
        val reachable = head(INTERNET_CHECK_URL, HttpURLConnection.HTTP_NO_CONTENT)
        cachedHasInternet = reachable
        cachedHasInternetAt = clock()
        return reachable
    }

    companion object {
        /**
         * Android's own captive-portal endpoint. A bare 204 is the only proof that traffic
         * really leaves the network: a captive portal answers 200 with a login page, and a
         * blackholed network answers nothing.
         */
        const val INTERNET_CHECK_URL = "https://connectivitycheck.gstatic.com/generate_204"

        const val INTERNET_VERDICT_TTL_MS = 120_000L

        /** Pass as expectedCode to accept any HTTP response: the host answered at all. */
        const val ANY_RESPONSE = -1

        private const val TIMEOUT_MS = 5_000

        /** Rungs one and two, from the OS. Exceptions propagate to the caller's error sink. */
        internal fun systemTransport(context: Context): TransportStatus {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return TransportStatus.NONE
            val active = cm.activeNetwork ?: return TransportStatus.NONE
            // Can come back null if the network dropped between the two calls - same as no transport.
            val capabilities = cm.getNetworkCapabilities(active) ?: return TransportStatus.NONE
            return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                TransportStatus.VALIDATED
            } else {
                TransportStatus.ATTACHED_UNVALIDATED
            }
        }

        /**
         * HEADs a URL. [ANY_RESPONSE] accepts any HTTP response (some endpoints reject HEAD
         * with 405, which still proves the host answered); a specific code requires it
         * exactly. Redirects are not followed: a captive portal's 302 to its login page is
         * the answer we want to see, not the 200 we would get after chasing it.
         */
        internal fun respondsToHead(urlString: String, expectedCode: Int): Boolean {
            var connection: HttpURLConnection? = null
            return try {
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = false
                }
                val code = connection.responseCode
                expectedCode < 0 || code == expectedCode
            } catch (e: Exception) {
                false
            } finally {
                connection?.disconnect()
            }
        }
    }
}
