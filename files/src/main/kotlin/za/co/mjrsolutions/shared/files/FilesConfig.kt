package za.co.mjrsolutions.shared.files

import za.co.mjrsolutions.shared.files.backup.PhotoSource

/**
 * Init-time configuration of [AgriFiles]. Providers are read when they are needed (a pass, an
 * export), so init order against the app's user store does not matter.
 *
 * @param app "hael", "mr" or "vrugte": the Storage roots {app}_photos and {app}_pdfs.
 * @param clientId the audit client id (hael FLAVOR, mr FLAVOR+"_mr", vrugte FLAVOR+"_vrugte").
 * @param loginIdProvider the login id (Storage folder of unlinked photos and DB exports).
 * @param fullNameProvider the assessor's full name (unlinked-photo overlay, DB export name).
 * @param photoSource the photos the app's tests and lands link to; null = only unlinked photos.
 * @param legacyJobIds JobScheduler ids an older app version queued for the same work (hael's
 *        PhotoBackupJobService used 711302017): cancelled at init so they can't block the new job.
 */
data class FilesConfig @JvmOverloads constructor(
    val app: String,
    val clientId: String,
    val loginIdProvider: () -> String?,
    val fullNameProvider: () -> String?,
    val appVersion: String,
    val versionCode: Long,
    val applicationId: String,
    val userServerIdProvider: () -> String? = { null },
    val dbVersionProvider: () -> Int = { 0 },
    val photoSource: PhotoSource? = null,
    val legacyJobIds: List<Int> = emptyList()
)
