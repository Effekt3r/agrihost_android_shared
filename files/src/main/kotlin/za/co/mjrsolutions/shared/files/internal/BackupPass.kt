package za.co.mjrsolutions.shared.files.internal

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import za.co.mjrsolutions.shared.audit.AuditFormat
import za.co.mjrsolutions.shared.audit.PhotoLogEntry
import za.co.mjrsolutions.shared.files.FilesConfig
import za.co.mjrsolutions.shared.files.StoragePaths
import za.co.mjrsolutions.shared.files.backup.LinkedPhoto
import za.co.mjrsolutions.shared.files.photos.GalleryPhotos
import za.co.mjrsolutions.shared.files.photos.MediaInfo
import za.co.mjrsolutions.shared.files.photos.PhotoCaptureLog
import za.co.mjrsolutions.shared.files.photos.PhotoEncoder
import java.io.File

/**
 * One copy pass (hael's PhotoBackup.runOnce, shared). Photos the app's tests and lands link to go
 * first - not yet sent to the server before sent - then photos this app took that nothing links
 * to, then PDFs (PdfCopier). One file at a time; a file is copied once (its key is remembered
 * with its Storage path in prefs "photo_backup"); the pass stops at the first failed upload and
 * the next pass carries on.
 */
internal class BackupPass(
    private val context: Context,
    private val config: FilesConfig,
    private val deps: BackupDeps
) {
    enum class Result { COPIED, SKIPPED, STOP }

    private val done: SharedPreferences = context.getSharedPreferences(DONE_PREFS, Context.MODE_PRIVATE)
    private val photoRoot = StoragePaths.photoRoot(config.app)

    /**
     * wifiOnly: an automatic pass - it starts only on Wi-Fi and stops as soon as the tablet leaves
     * it. onlyReport: a Sync tap - just that report's linked photos and PDFs, on any network.
     */
    fun run(wifiOnly: Boolean, onlyReport: String?) {
        if (AuditFormat.isBlank(login())) {
            Log.i(TAG, "pass skipped: nobody logged in")
            return
        }
        if (!networkOk(wifiOnly)) {
            Log.i(TAG, if (wifiOnly) "pass skipped: waiting for Wi-Fi" else "pass skipped: no connection")
            return
        }
        if (!copyPhotos(wifiOnly, onlyReport)) return
        PdfCopier(context, config, deps).copy(wifiOnly, onlyReport)
    }

    /** False when the pass must end here (an upload failed, or Wi-Fi went). */
    private fun copyPhotos(wifiOnly: Boolean, onlyReport: String?): Boolean {
        // Below Android 10 the apps cannot take gallery photos (the capture needs RELATIVE_PATH).
        if (deps.sdkInt < Build.VERSION_CODES.Q) return true
        val source = config.photoSource
        val listed: List<LinkedPhoto> = if (source == null) emptyList() else {
            val result = try {
                source.linkedPhotos(context)
            } catch (e: Exception) {
                Report.exception(e)
                null
            }
            if (result == null) {
                Log.i(TAG, "pass skipped: the linked photos could not all be read")
                return false // never guess; the next pass tries again
            }
            result
        }
        val linked = LinkedHashMap<String, LinkedPhoto>()
        for (p in listed) {
            if (!isUsableLink(p.uri)) continue
            linked.putIfAbsent(GalleryPhotos.key(p.uri), p)
        }
        val order = linked.entries.filter { !it.value.sentToServer } + linked.entries.filter { it.value.sentToServer }
        val wanted = if (onlyReport == null) order else order.filter { onlyReport == it.value.reportNumber?.trim() }
        Log.i(TAG, (if (wifiOnly) "Wi-Fi pass: " else "Sync pass for report $onlyReport: ") +
            "${wanted.size} linked photos, ${wanted.count { !done.contains(it.key) }} not copied yet")
        var copied = 0
        for ((key, p) in wanted) {
            if (done.contains(key)) continue
            if (wifiOnly && !deps.network.isOnWifi()) {
                Log.i(TAG, "pass paused after $copied photos: off Wi-Fi")
                return false
            }
            when (copyLinked(key, p)) {
                Result.STOP -> {
                    Log.i(TAG, "pass stopped after $copied photos")
                    return false
                }
                Result.COPIED -> copied++
                Result.SKIPPED -> Unit
            }
        }
        if (onlyReport == null) {
            val linkedIds = linked.values.filter { GalleryPhotos.isGalleryLink(it.uri) }
                .map { GalleryPhotos.mediaId(it.uri) }.toHashSet()
            if (!copyUnlinked(linkedIds, wifiOnly)) return false
        }
        return true
    }

    private fun copyLinked(key: String, p: LinkedPhoto): Result {
        val gallery = GalleryPhotos.isGalleryLink(p.uri)
        val readUri = GalleryPhotos.readUri(p.uri)
        val capture = PhotoCaptureLog.read(context, p.uri)
        val taken: Taken
        val number: Long
        var photoId: String? = null
        var sizeOnDevice: Long
        if (gallery) {
            val info = deps.gallery.read(readUri)
            if (info == null) {
                Log.i(TAG, "skipped for now, the gallery could not be asked: $key")
                return Result.SKIPPED
            }
            if (!info.exists) return skip(key, SKIP_MISSING)
            if (!info.isOurs(context.packageName)) return skip(key, SKIP_NOT_OURS)
            if (info.size <= 0) return skip(key, SKIP_EMPTY)
            taken = Taken.of(capture, info)
            number = info.id
            sizeOnDevice = info.size
        } else {
            val file = File(key.removePrefix("file:"))
            if (!file.isFile) return skip(key, SKIP_MISSING)
            if (file.length() <= 0) return skip(key, SKIP_EMPTY)
            taken = if (capture != null && capture.takenAtMs > 0) Taken(capture.takenAtMs, "capture")
            else Taken(file.lastModified(), "file_modified")
            number = file.absolutePath.hashCode().toLong() and 0xffffffffL
            photoId = file.name.substringBeforeLast('.')
            sizeOnDevice = file.length()
        }
        val gps: String
        val gpsSource: String
        when {
            capture?.gps != null -> { gps = capture.gps; gpsSource = "capture" }
            !AuditFormat.isBlank(p.testGps) -> { gps = p.testGps!!; gpsSource = "test" }
            else -> { gps = ""; gpsSource = "none" }
        }
        val lines = p.overlayLines ?: PhotoEncoder.overlayLines(p.reportServerId, p.clientName, p.farmName,
            p.landName, p.testIndex, p.testGps)
        val jpeg = deps.encoder(readUri, lines)
        if (jpeg == null) {
            Log.i(TAG, "skipped for now, could not be read: $key ($sizeOnDevice bytes)")
            return Result.SKIPPED
        }
        val path = StoragePaths.photoPath(photoRoot, config.clientId, p.reportNumber, p.landName, p.landId,
            p.testIndex, p.slot, taken.ms, number)
        val metadata = linkedMapOf(
            "reportNumber" to nz(p.reportNumber),
            "reportServerId" to nz(p.reportServerId),
            "landId" to nz(p.landId),
            "landName" to nz(p.landName),
            "farmName" to nz(p.farmName),
            "testIndex" to nz(p.testIndex),
            "photoSlot" to p.slot.toString(),
            "takenAt" to StoragePaths.stamp(taken.ms),
            "gps" to gps,
            "username" to nz(login()),
            "mediaId" to number.toString(),
            "appVersion" to config.appVersion
        )
        val upload = deps.uploader.upload(path, jpeg, "image/jpeg", metadata)
        if (!upload.stored) return Result.STOP
        done.edit().putString(key, path).apply()
        deps.logPhoto(PhotoLogEntry().apply {
            reportNumber = p.reportNumber
            reportServerId = p.reportServerId
            landId = p.landId
            landName = p.landName
            farmName = p.farmName
            crop = p.crop
            testIndex = p.testIndex
            slot = p.slot
            mediaId = if (gallery) number else 0
            this.photoId = photoId
            takenAtMs = taken.ms
            takenAtSource = taken.source
            this.gps = gps
            this.gpsSource = gpsSource
            sentToServer = p.sentToServer
            storagePath = path
            photoUrl = upload.url
            sizeBytes = jpeg.size.toLong()
        })
        Log.i(TAG, "copied $key -> $path")
        return Result.COPIED
    }

    /** Photos this app took that nothing links to; false when the pass must end here. */
    private fun copyUnlinked(linkedIds: Set<Long>, wifiOnly: Boolean): Boolean {
        for (info in deps.gallery.ownPhotos()) {
            if (info.id in linkedIds) continue
            val key = info.uri.toString()
            if (done.contains(key)) continue
            if (wifiOnly && !deps.network.isOnWifi()) {
                Log.i(TAG, "unlinked photos paused: off Wi-Fi")
                return false
            }
            val capture = PhotoCaptureLog.read(context, key)
            val taken = Taken.of(capture, info)
            val gps = capture?.gps ?: ""
            val placement = capture?.placement
            val path: String
            val lines: Array<String?>
            val entry = PhotoLogEntry()
            val metadata = LinkedHashMap<String, String>()
            if (placement != null) {
                path = StoragePaths.photoPath(photoRoot, config.clientId, placement.reportNumber, placement.landName,
                    placement.landId, placement.testIndex, placement.slot, taken.ms, info.id)
                lines = PhotoEncoder.overlayLines(placement.reportServerId, placement.clientName, placement.farmName,
                    placement.landName, placement.testIndex, gps)
                metadata["reportNumber"] = nz(placement.reportNumber)
                metadata["reportServerId"] = nz(placement.reportServerId)
                metadata["landId"] = nz(placement.landId)
                metadata["landName"] = nz(placement.landName)
                metadata["farmName"] = nz(placement.farmName)
                metadata["testIndex"] = nz(placement.testIndex)
                metadata["photoSlot"] = placement.slot.toString()
                entry.reportNumber = placement.reportNumber
                entry.reportServerId = placement.reportServerId
                entry.landId = placement.landId
                entry.landName = placement.landName
                entry.farmName = placement.farmName
                entry.crop = placement.crop
                entry.testIndex = placement.testIndex
                entry.slot = placement.slot
            } else {
                path = StoragePaths.unlinkedPhotoPath(photoRoot, config.clientId, login(), taken.ms, info.id)
                lines = arrayOf("Ongekoppelde foto", "Assessor: " + nz(fullName()), "Geneem: " + StoragePaths.stamp(taken.ms))
                metadata["unlinked"] = "true"
                entry.reportNumber = StoragePaths.UNLINKED
            }
            val jpeg = deps.encoder(info.uri, lines) ?: continue
            metadata["takenAt"] = StoragePaths.stamp(taken.ms)
            metadata["gps"] = gps
            metadata["username"] = nz(login())
            metadata["mediaId"] = info.id.toString()
            metadata["appVersion"] = config.appVersion
            val upload = deps.uploader.upload(path, jpeg, "image/jpeg", metadata)
            if (!upload.stored) return false
            done.edit().putString(key, path).apply()
            entry.mediaId = info.id
            entry.takenAtMs = taken.ms
            entry.takenAtSource = taken.source
            entry.gps = gps
            entry.gpsSource = if (gps.isEmpty()) "none" else "capture"
            entry.storagePath = path
            entry.photoUrl = upload.url
            entry.sizeBytes = jpeg.size.toLong()
            deps.logPhoto(entry)
            Log.i(TAG, "copied unlinked $key -> $path")
        }
        return true
    }

    private fun isUsableLink(uri: String?): Boolean {
        if (AuditFormat.isBlank(uri)) return false
        val text = uri!!.trim()
        if (text.startsWith("content://")) return GalleryPhotos.mediaId(text) >= 0
        return text.startsWith("file://") || File(text).isAbsolute
    }

    private fun skip(key: String, why: String): Result {
        Log.i(TAG, "skipped ($why): $key")
        done.edit().putString(key, why).apply()
        return Result.SKIPPED
    }

    private fun networkOk(wifiOnly: Boolean) = if (wifiOnly) deps.network.isOnWifi() else deps.network.isOnline()

    private fun login(): String? = runCatching { config.loginIdProvider() }.getOrNull()

    private fun fullName(): String? = runCatching { config.fullNameProvider() }.getOrNull()

    private fun nz(value: String?) = value ?: ""

    /** When a photo was taken, and what says so. */
    class Taken(val ms: Long, val source: String) {
        companion object {
            // The capture record is the camera returning; the file name is the camera opening
            // (photo_{ms}.jpg, device clock); the gallery's dates come last.
            fun of(capture: PhotoCaptureLog.Capture?, info: MediaInfo): Taken = when {
                capture != null && capture.takenAtMs > 0 -> Taken(capture.takenAtMs, "capture")
                info.nameMs > 0 -> Taken(info.nameMs, "file_name")
                info.dateTakenMs > 0 -> Taken(info.dateTakenMs, "gallery")
                else -> Taken(info.dateAddedMs, "gallery_added")
            }
        }
    }

    companion object {
        const val TAG = "AgriBackup"
        /** Photo key -> its Storage path, or why it was skipped (hael's prefs name: no re-copies after the update). */
        const val DONE_PREFS = "photo_backup"
        const val SKIP_PREFIX = "skip:"
        const val SKIP_MISSING = SKIP_PREFIX + "not_in_gallery"
        const val SKIP_NOT_OURS = SKIP_PREFIX + "not_taken_by_this_app"
        const val SKIP_EMPTY = SKIP_PREFIX + "empty_file"
    }
}
