package za.co.mjrsolutions.shared.files.photos

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import za.co.mjrsolutions.shared.files.backup.AgriBackup
import za.co.mjrsolutions.shared.files.internal.Report

/**
 * Taking a photo the way hael does: the system camera writes into a new gallery row
 * photo_{ms}.jpg under Pictures (no storage permission from Android 10 up), the app keeps the
 * row's content URI as the link, and the moment the camera returns, when and where it was taken is
 * recorded and a Wi-Fi copy is queued.
 *
 *   val uri = AgriPhotoCapture.newPhotoUri(context)        // EXTRA_OUTPUT for ACTION_IMAGE_CAPTURE
 *   ... RESULT_OK:       AgriPhotoCapture.onCaptured(context, uri, gps)
 *   ... anything else:   AgriPhotoCapture.onCancelled(context, uri)
 */
object AgriPhotoCapture {

    /** What a new photo's gallery row is created with. */
    @JvmStatic
    @JvmOverloads
    fun newPhotoValues(nowMs: Long = System.currentTimeMillis(), sdkInt: Int = Build.VERSION.SDK_INT): ContentValues =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "photo_$nowMs.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (sdkInt >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

    /** A new, empty gallery row for the camera to write into, or null when the gallery refused. */
    @JvmStatic
    fun newPhotoUri(context: Context): Uri? = try {
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newPhotoValues())
    } catch (e: Exception) {
        Report.exception(e)
        null
    }

    /** The camera returned the photo: record when and where, and queue its Wi-Fi copy. */
    @JvmStatic
    @JvmOverloads
    fun onCaptured(context: Context, uri: Uri, gps: String?, placement: PhotoPlacement? = null) {
        PhotoCaptureLog.record(context, uri.toString(), System.currentTimeMillis(), gps, placement)
        AgriBackup.requestWifiPass(context)
    }

    /** The camera came back without a photo: the empty row goes, so the gallery holds no blank picture. */
    @JvmStatic
    fun onCancelled(context: Context, uri: Uri?) {
        if (uri == null) return
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Report.exception(e)
        }
    }
}
