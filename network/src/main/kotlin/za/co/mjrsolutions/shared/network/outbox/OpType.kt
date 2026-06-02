package za.co.mjrsolutions.shared.network.outbox

/**
 * Well-known operation tags. opType is a free String on OutboxOperation/OutboxEntry so apps
 * may add their own; these are the common upload kinds the apps share.
 */
object OpType {
    const val SAVE_REPORT = "SAVE_REPORT"
    const val SAVE_EXPENSE = "SAVE_EXPENSE"
    const val UPLOAD_PHOTO = "UPLOAD_PHOTO"
    const val SIGN_OFF = "SIGN_OFF"
}
