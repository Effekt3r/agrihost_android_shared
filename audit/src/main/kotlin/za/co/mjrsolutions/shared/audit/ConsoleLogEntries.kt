package za.co.mjrsolutions.shared.audit

/**
 * One photo's copy is in Storage: the console's "photo" entry (doc photo_{photoId or mediaId}).
 * Plain fields so Java callers fill it like hael's PhotoSyncLog.PhotoEntry.
 */
class PhotoLogEntry {
    @JvmField var reportNumber: String? = null
    @JvmField var reportServerId: String? = null
    @JvmField var landId: String? = null
    @JvmField var landName: String? = null
    @JvmField var farmName: String? = null
    @JvmField var crop: String? = null
    @JvmField var testIndex: String? = null
    @JvmField var slot: Int = 0
    @JvmField var mediaId: Long = 0
    /** Set for photos outside the gallery (no MediaStore id); the entry id then uses it. */
    @JvmField var photoId: String? = null
    @JvmField var takenAtMs: Long = 0
    @JvmField var takenAtSource: String? = null
    @JvmField var gps: String? = null
    @JvmField var gpsSource: String? = null
    @JvmField var sentToServer: Boolean = false
    @JvmField var storagePath: String? = null
    @JvmField var photoUrl: String? = null
    @JvmField var sizeBytes: Long = 0
}

/** One photo upload to the server finished: what was sent and what the server said ("photo_upload"). */
class PhotoUploadLogEntry {
    @JvmField var reportNumber: String? = null
    @JvmField var reportServerId: String? = null
    @JvmField var landId: String? = null
    @JvmField var landName: String? = null
    @JvmField var testIndex: String? = null
    @JvmField var uitstel: Boolean = false
    /** One small map per image sent (slot, size...), never the image itself. */
    @JvmField var images: List<Map<String, Any?>>? = null
    @JvmField var accepted: Boolean = false
    @JvmField var serverResponse: String? = null
    @JvmField var endpoint: String? = null
}

/** One PDF's copy is in Storage: the console's "pdf" entry (doc pdf_{file name}). */
class PdfLogEntry {
    @JvmField var reportNumber: String? = null
    @JvmField var reportServerId: String? = null
    @JvmField var fileName: String? = null
    /** What the PDF is: "report", "audit", or the app's own name for it. */
    @JvmField var kind: String? = null
    @JvmField var landName: String? = null
    @JvmField var landId: String? = null
    @JvmField var storagePath: String? = null
    @JvmField var pdfUrl: String? = null
    @JvmField var sizeBytes: Long = 0
    @JvmField var contentHash: String? = null
    @JvmField var generatedAtMs: Long = 0
}
