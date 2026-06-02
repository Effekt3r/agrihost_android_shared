package za.co.mjrsolutions.shared.network.outbox

/** Pulls the server-assigned id out of a success response body. App-specific; default: none. */
fun interface ServerIdExtractor {
    fun extract(opType: String, responseBody: String?): String?

    companion object {
        val NONE = ServerIdExtractor { _, _ -> null }
    }
}
