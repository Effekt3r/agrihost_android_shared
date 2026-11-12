package za.co.mjrsolutions.shared.audit.internal

import com.google.firebase.firestore.FieldValue
import com.google.gson.GsonBuilder
import za.co.mjrsolutions.shared.audit.AuditConfig
import za.co.mjrsolutions.shared.audit.AuditFormat
import za.co.mjrsolutions.shared.audit.AuditKind
import za.co.mjrsolutions.shared.audit.PdfLogEntry
import za.co.mjrsolutions.shared.audit.PhotoLogEntry
import za.co.mjrsolutions.shared.audit.PhotoUploadLogEntry

/**
 * The photo, photo-upload and PDF entries the console reads next to a report's sends:
 * SyncToServer/{client}/{full name}/{report}/entries/{id}. Field names and order are hael's
 * PhotoSyncLog's (frozen: the console's Photos lane reads them). No messages doc, no push.
 */
internal class ConsoleEntryFactory(private val config: AuditConfig) {

    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    fun photo(p: PhotoLogEntry, userName: String, loginId: String, millis: Long): AuditEntry {
        val summary = linkedMapOf<String, Any?>(
            "type" to TYPE_PHOTO,
            "report_number" to p.reportNumber,
            "report_server_id" to p.reportServerId,
            "land_id" to p.landId,
            "land_name" to p.landName,
            "farm_name" to p.farmName,
            "crop" to p.crop,
            "test_index" to p.testIndex,
            "photo_slot" to p.slot,
            "taken_at" to AuditFormat.stamp(p.takenAtMs),
            "taken_at_source" to p.takenAtSource,
            "gps" to p.gps,
            "gps_source" to p.gpsSource,
            "sent_to_server" to p.sentToServer,
            "photo_url" to p.photoUrl,
            "storage_path" to p.storagePath,
            "size_bytes" to p.sizeBytes,
            "media_id" to p.mediaId.toString()
        )
        val doc = base(TYPE_PHOTO, summary, p.reportNumber, p.reportServerId, userName, loginId)
        doc["landId"] = nz(p.landId)
        doc["landName"] = nz(p.landName)
        doc["farmName"] = nz(p.farmName)
        doc["crop"] = nz(p.crop)
        doc["testIndex"] = nz(p.testIndex)
        doc["photoSlot"] = p.slot
        doc["mediaId"] = p.mediaId.toString()
        doc["takenAtMs"] = p.takenAtMs
        doc["takenAtSource"] = nz(p.takenAtSource)
        doc["gps"] = nz(p.gps)
        doc["gpsSource"] = nz(p.gpsSource)
        doc["sentToServer"] = p.sentToServer
        doc["photoUrl"] = nz(p.photoUrl)
        doc["storagePath"] = nz(p.storagePath)
        doc["sizeBytes"] = p.sizeBytes
        doc["backedUpAtMs"] = millis
        val id = if (AuditFormat.isBlank(p.photoId)) p.mediaId.toString() else AuditFormat.segment(p.photoId)
        return entry(userName, p.reportNumber, "photo_$id", doc)
    }

    fun photoUpload(u: PhotoUploadLogEntry, userName: String, loginId: String, docId: String): AuditEntry {
        val images = u.images ?: emptyList()
        val summary = linkedMapOf<String, Any?>(
            "type" to TYPE_SERVER_UPLOAD,
            "endpoint" to u.endpoint,
            "report_id" to u.reportServerId,
            "report_number" to u.reportNumber,
            "land_id" to u.landId,
            "land_name" to u.landName,
            "test_index" to u.testIndex,
            "uitstel" to u.uitstel,
            "images" to images
        )
        val doc = base(TYPE_SERVER_UPLOAD, summary, u.reportNumber, u.reportServerId, userName, loginId)
        doc["landId"] = nz(u.landId)
        doc["landName"] = nz(u.landName)
        doc["testIndex"] = nz(u.testIndex)
        doc["uitstel"] = u.uitstel
        doc["imageCount"] = images.size
        doc["success"] = u.accepted
        doc["status"] = if (u.accepted) "success" else "failure"
        doc["response"] = AuditFormat.serverMessage(u.serverResponse)
        return entry(userName, u.reportNumber, docId, doc)
    }

    fun pdf(p: PdfLogEntry, userName: String, loginId: String, millis: Long): AuditEntry {
        val summary = linkedMapOf<String, Any?>(
            "type" to TYPE_PDF,
            "report_number" to p.reportNumber,
            "report_server_id" to p.reportServerId,
            "file_name" to p.fileName,
            "kind" to p.kind,
            "land_name" to p.landName,
            "land_id" to p.landId,
            "generated_at" to AuditFormat.stamp(p.generatedAtMs),
            "pdf_url" to p.pdfUrl,
            "storage_path" to p.storagePath,
            "size_bytes" to p.sizeBytes,
            "content_hash" to p.contentHash
        )
        val doc = base(TYPE_PDF, summary, p.reportNumber, p.reportServerId, userName, loginId)
        doc["fileName"] = nz(p.fileName)
        doc["kind"] = nz(p.kind)
        doc["landName"] = nz(p.landName)
        doc["landId"] = nz(p.landId)
        doc["generatedAtMs"] = p.generatedAtMs
        doc["pdfUrl"] = nz(p.pdfUrl)
        doc["storagePath"] = nz(p.storagePath)
        doc["sizeBytes"] = p.sizeBytes
        doc["contentHash"] = nz(p.contentHash)
        doc["backedUpAtMs"] = millis
        return entry(userName, p.reportNumber, "pdf_" + AuditFormat.segment(p.fileName), doc)
    }

    private fun base(type: String, summary: Map<String, Any?>, reportNumber: String?, reportServerId: String?,
                     userName: String, loginId: String): MutableMap<String, Any?> = linkedMapOf(
        "type" to type,
        "request" to gson.toJson(summary),
        "timestamp" to FieldValue.serverTimestamp(),
        "client" to config.clientId,
        "username" to loginId,
        "userName" to userName,
        "appVersion" to config.appVersion,
        "reportNumber" to nz(reportNumber),
        "reportServerId" to nz(reportServerId)
    )

    private fun entry(userName: String, reportNumber: String?, docId: String, doc: Map<String, Any?>) =
        AuditEntry(AuditKind.SYNC_TO_SERVER.collectionName, config.clientId, userName,
            AuditFormat.reportSegment(reportNumber), docId, doc)

    private fun nz(value: String?): String = value ?: ""

    companion object {
        const val TYPE_PHOTO = "photo"
        const val TYPE_SERVER_UPLOAD = "photo_upload"
        const val TYPE_PDF = "pdf"
    }
}
