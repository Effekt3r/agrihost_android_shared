package za.co.mjrsolutions.shared.files.internal

import android.content.ContentUris
import android.net.Uri
import za.co.mjrsolutions.shared.audit.PdfLogEntry
import za.co.mjrsolutions.shared.audit.PhotoLogEntry
import za.co.mjrsolutions.shared.files.photos.Gallery
import za.co.mjrsolutions.shared.files.photos.MediaInfo

internal class FakeUploader : Uploader {
    class Call(val path: String, val size: Int, val contentType: String, val metadata: Map<String, String>, val withLink: Boolean)
    val calls = mutableListOf<Call>()
    var failOnCall = -1
    override fun upload(path: String, bytes: ByteArray, contentType: String, metadata: Map<String, String>,
                        withLink: Boolean): UploadResult {
        val i = calls.size
        calls.add(Call(path, bytes.size, contentType, metadata, withLink))
        return if (i == failOnCall) UploadResult(false, "")
        else UploadResult(true, if (withLink) "https://firebasestorage.googleapis.com/v0/b/agrihost-7970a/o/$i" else "")
    }
}

/** Wi-Fi for the first [wifiChecks] questions, then gone. */
internal class FakeNetwork(var wifiChecks: Int = Int.MAX_VALUE, var online: Boolean = true) : Network {
    var asked = 0
    override fun isOnWifi(): Boolean = ++asked <= wifiChecks
    override fun isOnline(): Boolean = online
}

internal class FakeGallery(private val rows: Map<Long, MediaInfo> = emptyMap(), private val own: List<MediaInfo> = emptyList()) : Gallery {
    override fun read(uri: Uri): MediaInfo? = rows[ContentUris.parseId(uri)] ?: MediaInfo.missing(uri)
    override fun ownPhotos(): List<MediaInfo> = own
}

internal class Recorded {
    val photos = mutableListOf<PhotoLogEntry>()
    val pdfs = mutableListOf<PdfLogEntry>()
}

internal fun fakeDeps(uploader: Uploader, network: Network, gallery: Gallery, recorded: Recorded, sdkInt: Int = 33) =
    BackupDeps(
        uploader = uploader, network = network, gallery = gallery,
        encoder = { _, _ -> ByteArray(10) },
        logPhoto = { recorded.photos.add(it) }, logPdf = { recorded.pdfs.add(it) },
        clock = { 1_794_471_300_000L }, sdkInt = sdkInt
    )

internal fun galleryRow(id: Long, owner: String, size: Long = 1000, name: String = "photo_1794471300000.jpg") =
    MediaInfo(ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), id,
        exists = true, owner = owner, displayName = name, size = size)
