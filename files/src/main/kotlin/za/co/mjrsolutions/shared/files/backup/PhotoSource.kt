package za.co.mjrsolutions.shared.files.backup

import android.content.Context

/**
 * The app's answer to "which photos do my tests and lands link to?" for the copy pass. Runs on
 * the backup thread. Return null when the data could not all be read: the pass then copies
 * nothing linked rather than guess, and the next pass tries again.
 */
fun interface PhotoSource {
    fun linkedPhotos(context: Context): List<LinkedPhoto>?
}

/**
 * One linked photo and what the console and the folder name need to place it.
 * [uri] is the stored link (a gallery content URI, or for mr's older photos a file path).
 */
class LinkedPhoto {
    @JvmField var uri: String = ""
    @JvmField var slot: Int = 0
    @JvmField var sentToServer: Boolean = false
    @JvmField var reportNumber: String? = null
    @JvmField var reportServerId: String? = null
    @JvmField var clientName: String? = null
    @JvmField var farmName: String? = null
    @JvmField var landName: String? = null
    @JvmField var landId: String? = null
    @JvmField var crop: String? = null
    @JvmField var testIndex: String? = null
    @JvmField var testGps: String? = null
    /** Text stamped on the copy (null = hael's overlay from the fields above; empty = none). */
    @JvmField var overlayLines: Array<String?>? = null
}
