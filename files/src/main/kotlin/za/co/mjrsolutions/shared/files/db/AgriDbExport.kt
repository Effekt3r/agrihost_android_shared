package za.co.mjrsolutions.shared.files.db

import android.content.Context
import android.net.Uri
import android.os.Build
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import za.co.mjrsolutions.shared.files.AgriFiles
import za.co.mjrsolutions.shared.files.StoragePaths
import za.co.mjrsolutions.shared.files.internal.DbZipper
import za.co.mjrsolutions.shared.files.internal.Report
import java.io.File
import java.io.IOException

/**
 * Database exports, the same in every app (hael's DatabaseExporter + DbExportUploader, shared):
 * the whole database in one zip the assessor can share, sent to Storage
 * db_exports/{client}/{username}/{zip} and listed in the console's DB exports tab (Firestore
 * db_exports, with a download link). Assessors are often offline, so a zip not sent yet stays on
 * the tablet and goes on the next [uploadPending] (call it on the main screen's resume).
 */
object AgriDbExport {

    /** Exports made from the menu (the previous one is replaced). */
    const val EXPORT_DIR = "db_export"
    /** The data an import replaced, zipped before the import. Kept for good, and sent. */
    const val BACKUP_DIR = "db_backup"
    /** hael's automatic backups before Receive Order and a send, until 2026-11-09; still sent. */
    const val AUTO_BACKUP_DIR = "db_auto_backup"
    /** Marks a zip that has been sent. */
    const val UPLOADED_SUFFIX = ".uploaded"
    const val INFO_ENTRY = DbZipper.INFO_ENTRY

    private const val COLLECTION = "db_exports"
    private const val EXPORT_PREFIX = "agrihost_db_"
    private const val IMPORT_BACKUP_PREFIX = "agrihost_db_backup_"

    /** Told, on the main thread, how one zip's upload ended. */
    interface Listener {
        fun onUploaded(zip: File)
        fun onFailed(zip: File, e: Exception)
    }

    /** Zips with an upload running, so a resume during an upload doesn't start a second one. */
    private val inFlight = HashSet<String>()

    /** The menu export: builds the zip and returns it. Blocking file IO - call off the main thread. */
    @JvmStatic
    @Throws(IOException::class)
    fun export(context: Context, spec: DbExportSpec, user: ExportUser?): File =
        DbZipper.export(context, spec, user, clientId(), EXPORT_DIR, EXPORT_PREFIX, replacePrevious = true)

    /** Snapshot of the data an import is about to replace; never deleted, and sent. Blocking. */
    @JvmStatic
    @Throws(IOException::class)
    fun exportBackup(context: Context, spec: DbExportSpec, user: ExportUser?): File =
        DbZipper.export(context, spec, user, clientId(), BACKUP_DIR, IMPORT_BACKUP_PREFIX, replacePrevious = false)

    /** What a zip is, from its name: the kind the console shows. */
    @JvmStatic
    fun kindOf(zipName: String): String = when {
        zipName.startsWith(IMPORT_BACKUP_PREFIX) -> "pre_import_backup"
        zipName.startsWith(EXPORT_PREFIX + "receive_") -> "pre_receive_order_backup"
        zipName.startsWith(EXPORT_PREFIX + "send_") -> "pre_send_backup"
        else -> "export"
    }

    /** Every zip not sent yet. */
    @JvmStatic
    fun pendingZips(context: Context): List<File> =
        listOf(EXPORT_DIR, BACKUP_DIR, AUTO_BACKUP_DIR).flatMap { name ->
            File(context.filesDir, name).listFiles { _, n -> n.endsWith(".zip") }?.toList() ?: emptyList()
        }.filter { !File(it.path + UPLOADED_SUFFIX).exists() }

    /** Starts an upload for every zip not sent yet. Cheap when there is none; safe on the main thread. */
    @JvmStatic
    fun uploadPending(context: Context, listener: Listener?) {
        pendingZips(context).forEach { upload(it, listener) }
    }

    private fun upload(zip: File, listener: Listener?) {
        synchronized(inFlight) { if (!inFlight.add(zip.path)) return }
        val config = AgriFiles.config()
        val username = config?.let { runCatching { it.loginIdProvider() }.getOrNull() }
        val storagePath = StoragePaths.dbExportPath(clientId(), username, zip.name)
        try {
            FirebaseStorage.getInstance().getReference(storagePath)
                .putFile(Uri.fromFile(zip), StorageMetadata.Builder().setContentType("application/zip").build())
                .addOnSuccessListener {
                    finished(zip)
                    try {
                        FirebaseFirestore.getInstance().collection(COLLECTION).add(logDoc(zip, storagePath))
                            .addOnFailureListener { Report.exception(it) }
                    } catch (e: Exception) {
                        Report.exception(e)
                    }
                    markSent(zip)
                    listener?.onUploaded(zip)
                }
                .addOnFailureListener { e ->
                    finished(zip)
                    // No network for the whole retry window is the normal offline case - it is
                    // tried again on the next resume, so it doesn't flood Crashlytics.
                    if (e !is StorageException || e.errorCode != StorageException.ERROR_RETRY_LIMIT_EXCEEDED) {
                        Report.exception(e)
                    }
                    listener?.onFailed(zip, e)
                }
        } catch (e: RuntimeException) {
            finished(zip)
            Report.exception(e)
            listener?.onFailed(zip, e)
        }
    }

    /** The console's record of a sent zip (hael's db_exports fields). */
    @JvmStatic
    fun logDoc(zip: File, storagePath: String): Map<String, Any?> {
        val config = AgriFiles.config()
        fun read(provider: (() -> String?)?) = provider?.let { runCatching { it() }.getOrNull() } ?: ""
        return hashMapOf(
            "kind" to kindOf(zip.name),
            "client" to clientId(),
            "applicationId" to (config?.applicationId ?: ""),
            "username" to read(config?.loginIdProvider),
            "userName" to read(config?.fullNameProvider),
            "userServerId" to read(config?.userServerIdProvider),
            "appVersion" to (config?.appVersion ?: ""),
            "versionCode" to (config?.versionCode ?: 0L),
            "dbVersion" to (config?.let { runCatching { it.dbVersionProvider() }.getOrNull() } ?: 0),
            "fileName" to zip.name,
            "storagePath" to storagePath,
            "sizeBytes" to zip.length(),
            "exportedAtMs" to zip.lastModified(),
            "device" to Build.MANUFACTURER + " " + Build.MODEL,
            "android" to Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    private fun finished(zip: File) {
        synchronized(inFlight) { inFlight.remove(zip.path) }
    }

    /** Nothing is deleted: a menu export stays for the share sheet, an import's backup for good. */
    private fun markSent(zip: File) {
        try {
            File(zip.path + UPLOADED_SUFFIX).createNewFile()
        } catch (e: IOException) {
            Report.exception(e)
        }
    }

    private fun clientId(): String = AgriFiles.config()?.clientId ?: "unknown"
}
