package za.co.mjrsolutions.shared.files.photos

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import za.co.mjrsolutions.shared.audit.AuditFormat
import za.co.mjrsolutions.shared.files.internal.Report
import java.io.File

/**
 * Photo links as the apps store them, and what the gallery knows about one picture. A link is a
 * gallery content URI (hael, vrugte, mr from now on) or, for mr photos taken before the move to
 * the gallery, an absolute file path.
 */
object GalleryPhotos {

    /** The MediaStore row id at the end of a content URI, or -1. */
    @JvmStatic
    fun mediaId(uri: String?): Long {
        if (AuditFormat.isBlank(uri)) return -1
        val text = uri!!.trim()
        val tail = text.substring(text.lastIndexOf('/') + 1).replace("]", "").trim()
        return tail.toLongOrNull() ?: -1
    }

    /** A gallery link with a row id ("content://.../123"). */
    @JvmStatic
    fun isGalleryLink(stored: String?): Boolean =
        stored != null && stored.trim().startsWith("content://") && mediaId(stored) >= 0

    /** The gallery URI a stored link names (links are rebuilt from their row id). */
    @JvmStatic
    fun galleryUri(stored: String?): Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId(stored))

    /** The one spelling a gallery link is remembered by on the tablet: its rebuilt gallery URI. */
    @JvmStatic
    fun galleryKey(stored: String?): String = galleryUri(stored?.trim() ?: "").toString()

    /**
     * The key a photo is remembered by (copy done-set, capture log): the rebuilt gallery URI, or
     * "file:" + absolute path for a photo kept as a file.
     */
    @JvmStatic
    fun key(stored: String?): String {
        val text = stored?.trim() ?: ""
        if (isGalleryLink(text)) return galleryKey(text)
        val path = if (text.startsWith("file://")) Uri.parse(text).path ?: text else text
        return "file:$path"
    }

    /** The URI to read a stored link's picture from (a gallery URI or a file URI). */
    @JvmStatic
    fun readUri(stored: String?): Uri {
        val text = stored?.trim() ?: ""
        if (isGalleryLink(text)) return galleryUri(text)
        if (text.startsWith("file://")) return Uri.parse(text)
        return Uri.fromFile(File(text))
    }

    /** photo_{ms}.jpg (hael) or photo_{n}_{ms}.jpg (vrugte) -> ms; 0 when the name holds no time. */
    @JvmStatic
    fun msFromName(name: String?): Long {
        if (name == null || !name.startsWith("photo_")) return 0
        val match = Regex("(\\d{12,})").findAll(name.substringBeforeLast('.')).lastOrNull() ?: return 0
        return match.value.toLongOrNull() ?: 0
    }
}

/** What the gallery knows about one picture (hael's PhotoBackup.MediaInfo). */
class MediaInfo(
    val uri: Uri,
    val id: Long,
    val exists: Boolean,
    val owner: String? = null,
    val displayName: String? = null,
    val size: Long = 0,
    val dateTakenMs: Long = 0,
    val dateAddedMs: Long = 0
) {
    val nameMs: Long get() = GalleryPhotos.msFromName(displayName)

    /** Taken by this app: the gallery's owner, or with no owner recorded, this app's own file names. */
    fun isOurs(packageName: String): Boolean =
        if (owner != null) owner == packageName else displayName?.startsWith("photo_") == true

    companion object {
        fun missing(uri: Uri) = MediaInfo(uri, -1, exists = false)
    }
}

/** Gallery queries behind a seam (Robolectric has no MediaStore). */
internal interface Gallery {
    /** The row, a missing info when the gallery has no such row, or null if asking failed. */
    fun read(uri: Uri): MediaInfo?

    /** Every non-empty picture this app put in the gallery, oldest first. */
    fun ownPhotos(): List<MediaInfo>
}

internal class ContentResolverGallery(private val context: Context) : Gallery {

    private val projection: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(
        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.OWNER_PACKAGE_NAME
    ) else arrayOf(
        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
        MediaStore.Images.ImageColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED
    )

    override fun read(uri: Uri): MediaInfo? = try {
        context.contentResolver.query(uri, projection, null, null, null).use { cursor ->
            when {
                cursor == null -> null
                !cursor.moveToFirst() -> MediaInfo.missing(uri)
                else -> from(cursor)
            }
        }
    } catch (e: Exception) {
        Report.exception(e)
        null
    }

    override fun ownPhotos(): List<MediaInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList() // no owner column
        val out = ArrayList<MediaInfo>()
        val selection = MediaStore.MediaColumns.OWNER_PACKAGE_NAME + " = ? AND " + MediaStore.MediaColumns.SIZE + " > 0"
        try {
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection,
                arrayOf(context.packageName), MediaStore.MediaColumns._ID + " ASC").use { cursor ->
                while (cursor != null && cursor.moveToNext()) out.add(from(cursor))
            }
        } catch (e: Exception) {
            Report.exception(e)
        }
        return out
    }

    private fun from(cursor: Cursor): MediaInfo {
        val id = cursor.getLong(0)
        return MediaInfo(
            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
            id = id,
            exists = true,
            owner = if (cursor.columnCount > 5) cursor.getString(5) else null,
            displayName = cursor.getString(1),
            size = if (cursor.isNull(2)) 0 else cursor.getLong(2),
            dateTakenMs = if (cursor.isNull(3)) 0 else cursor.getLong(3),
            dateAddedMs = if (cursor.isNull(4)) 0 else cursor.getLong(4) * 1000L // seconds
        )
    }
}
