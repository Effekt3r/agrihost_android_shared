package za.co.mjrsolutions.shared.network.outbox

/** Called after a successful upload so the app can write the server id back to its local model. */
fun interface OutboxReconciler {
    fun onUploaded(localId: String, opType: String, serverId: String?)

    companion object {
        val NONE = OutboxReconciler { _, _, _ -> }
    }
}
