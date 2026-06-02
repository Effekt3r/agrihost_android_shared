package za.co.mjrsolutions.shared.network

/**
 * Outcome of a network call. Mapped to localized, user-facing strings via AgriLang
 * at the call site in later phases — never here.
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    /** A write was accepted into the offline outbox; [localId] is its outbox row id. */
    data class Queued(val localId: String) : NetworkResult<Nothing>()
    /** Server returned a non-2xx status. */
    data class ServerError(val code: Int, val body: String?) : NetworkResult<Nothing>()
    /** Transport failure (timeout, reset) that is not a clean "no connectivity". */
    data class NetworkError(val cause: Throwable) : NetworkResult<Nothing>()
    /** Response received but could not be parsed. */
    data class ParseError(val cause: Throwable) : NetworkResult<Nothing>()
    /** No usable connectivity (host unresolved / connection refused). */
    object Offline : NetworkResult<Nothing>()
}
