package za.co.mjrsolutions.shared.files.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import za.co.mjrsolutions.shared.audit.AgriAudit
import za.co.mjrsolutions.shared.audit.PdfLogEntry
import za.co.mjrsolutions.shared.audit.PhotoLogEntry
import za.co.mjrsolutions.shared.files.photos.ContentResolverGallery
import za.co.mjrsolutions.shared.files.photos.Gallery
import za.co.mjrsolutions.shared.files.photos.PhotoEncoder
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class UploadResult(val stored: Boolean, val url: String)

internal interface Uploader {
    /**
     * Blocking (the backup thread). The url is "" if only the link step failed, or when
     * [withLink] is false (PDFs: only console admins may read them, the console makes the link).
     */
    fun upload(path: String, bytes: ByteArray, contentType: String, metadata: Map<String, String>,
               withLink: Boolean = true): UploadResult
}

internal interface Network {
    /** An unmetered network (Wi-Fi, not a phone's hotspot or mobile data) that reaches the internet. */
    fun isOnWifi(): Boolean

    fun isOnline(): Boolean
}

/** Everything a pass touches outside its own logic, so a pass runs the same in tests. */
internal class BackupDeps(
    val uploader: Uploader,
    val network: Network,
    val gallery: Gallery,
    val encoder: (Uri, Array<String?>) -> ByteArray?,
    val logPhoto: (PhotoLogEntry) -> Unit,
    val logPdf: (PdfLogEntry) -> Unit,
    val clock: () -> Long,
    val sdkInt: Int
) {
    companion object {
        fun real(context: Context): BackupDeps {
            val app = context.applicationContext
            return BackupDeps(
                uploader = FirebaseUploader(),
                network = AndroidNetwork(app),
                gallery = ContentResolverGallery(app),
                encoder = { uri, lines -> PhotoEncoder.stampedJpeg(app, uri, lines) },
                logPhoto = { AgriAudit.logPhoto(it) },
                logPdf = { AgriAudit.logPdf(it) },
                clock = System::currentTimeMillis,
                sdkInt = Build.VERSION.SDK_INT
            )
        }
    }
}

internal class AndroidNetwork(private val app: Context) : Network {
    override fun isOnWifi(): Boolean = caps()?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    } ?: false

    override fun isOnline(): Boolean =
        caps()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

    private fun caps(): NetworkCapabilities? = try {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        cm?.getNetworkCapabilities(cm.activeNetwork)
    } catch (e: RuntimeException) {
        null
    }
}

/** hael's upload rules: 15 min for the bytes, 2 min for the link; offline is not a fault. */
internal class FirebaseUploader : Uploader {
    override fun upload(path: String, bytes: ByteArray, contentType: String, metadata: Map<String, String>,
                        withLink: Boolean): UploadResult {
        Log.i(TAG, "uploading ${bytes.size} bytes -> $path")
        val ref = FirebaseStorage.getInstance().getReference(path)
        val builder = StorageMetadata.Builder().setContentType(contentType)
        metadata.forEach { (k, v) -> builder.setCustomMetadata(k, v) }
        try {
            Tasks.await(ref.putBytes(bytes, builder.build()), 15, TimeUnit.MINUTES)
        } catch (e: ExecutionException) {
            val cause = e.cause ?: e
            Log.w(TAG, "upload failed: $path - $cause")
            if (!isOffline(cause)) Report.exception(IOException("Storage copy upload failed: $path", cause))
            return UploadResult(false, "")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return UploadResult(false, "")
        } catch (e: TimeoutException) {
            Log.w(TAG, "upload timed out: $path")
            return UploadResult(false, "")
        }
        if (!withLink) return UploadResult(true, "")
        return try {
            UploadResult(true, Tasks.await(ref.downloadUrl, 2, TimeUnit.MINUTES).toString())
        } catch (e: Exception) {
            // The file is in Storage; the console falls back to its path. The connection going
            // between the upload and the link is the same normal offline case (hael 4816).
            val cause = if (e is ExecutionException && e.cause != null) e.cause!! else e
            Log.w(TAG, "link failed: $path - $cause")
            if (e !is TimeoutException && e !is InterruptedException && !isOffline(cause)) {
                Report.exception(IOException("Storage copy link failed: $path", e))
            }
            if (e is InterruptedException) Thread.currentThread().interrupt()
            UploadResult(true, "")
        }
    }

    /** No connection for the Storage SDK's whole retry window: the normal offline case. */
    private fun isOffline(cause: Throwable): Boolean =
        cause is StorageException && cause.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED

    companion object {
        private const val TAG = "AgriBackup"
    }
}
