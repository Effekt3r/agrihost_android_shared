package za.co.mjrsolutions.shared.files.photos

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import za.co.mjrsolutions.shared.audit.AuditFormat
import za.co.mjrsolutions.shared.files.internal.Report

/** Where a photo belongs, recorded at capture for apps that clear a test's links after sending it (vrugte). */
class PhotoPlacement {
    @JvmField var reportNumber: String? = null
    @JvmField var reportServerId: String? = null
    @JvmField var clientName: String? = null
    @JvmField var farmName: String? = null
    @JvmField var landName: String? = null
    @JvmField var landId: String? = null
    @JvmField var crop: String? = null
    @JvmField var testIndex: String? = null
    @JvmField var slot: Int = 0
}

/**
 * When and where each photo was taken (hael's PhotoCaptureLog, prefs "photo_capture_log"), kept
 * by photo key until its Storage copy carries it to the console. Written the moment the camera
 * returns a photo: the gallery's own date can be the file's write time and holds no position, and
 * a copy can run days later. Values: "{ms}|{lat,lng}" (hael) and, when a placement is known,
 * "|{placement json}" after that.
 */
object PhotoCaptureLog {

    private const val PREFS = "photo_capture_log"
    private const val SEPARATOR = "|"
    private val gson = Gson()

    class Capture(@JvmField val takenAtMs: Long, @JvmField val gps: String?, @JvmField val placement: PhotoPlacement?)

    @JvmStatic
    @JvmOverloads
    fun record(context: Context, photoUri: String, takenAtMs: Long, gps: String?, placement: PhotoPlacement? = null) {
        try {
            var value = takenAtMs.toString() + SEPARATOR + (gps?.trim() ?: "")
            if (placement != null) value += SEPARATOR + gson.toJson(placement)
            prefs(context).edit().putString(GalleryPhotos.key(photoUri), value).apply()
        } catch (e: Exception) {
            Report.exception(e)
        }
    }

    @JvmStatic
    fun read(context: Context, photoUri: String): Capture? = try {
        prefs(context).getString(GalleryPhotos.key(photoUri), null)?.let { parse(it) }
    } catch (e: Exception) {
        null
    }

    internal fun parse(value: String): Capture? {
        val parts = value.split(SEPARATOR, limit = 3)
        val takenAt = parts[0].toLongOrNull() ?: return null
        val gps = parts.getOrNull(1)?.takeUnless { AuditFormat.isBlank(it) }
        val placement = parts.getOrNull(2)?.let { runCatching { gson.fromJson(it, PhotoPlacement::class.java) }.getOrNull() }
        return Capture(takenAt, gps, placement)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
