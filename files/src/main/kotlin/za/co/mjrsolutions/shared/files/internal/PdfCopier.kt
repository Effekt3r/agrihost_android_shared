package za.co.mjrsolutions.shared.files.internal

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import za.co.mjrsolutions.shared.audit.AuditFormat
import za.co.mjrsolutions.shared.audit.PdfLogEntry
import za.co.mjrsolutions.shared.files.FilesConfig
import za.co.mjrsolutions.shared.files.StoragePaths
import za.co.mjrsolutions.shared.files.pdf.PdfInfo
import java.io.File

/** A PDF waiting for its copy: what it belongs to and when it was generated. */
internal class PendingPdf(
    val reportNumber: String? = null,
    val reportServerId: String? = null,
    val kind: String? = null,
    val landName: String? = null,
    val landId: String? = null,
    val generatedAtMs: Long = 0
)

/**
 * PDF copies (prefs "pdf_backup"): each generated PDF is recorded as pending by its path; a pass
 * copies it to {app}_pdfs/{client}/{report}/{file} unless its content hash equals the last copy's,
 * then writes its console entry. The same path every time, so a changed PDF replaces its copy.
 */
internal class PdfCopier(
    private val context: Context,
    private val config: FilesConfig,
    private val deps: BackupDeps
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** False when the pass must end here (an upload failed, or Wi-Fi went). */
    fun copy(wifiOnly: Boolean, onlyReport: String?): Boolean {
        val pending = prefs.all.filterKeys { it.startsWith(PENDING) }
        for ((key, value) in pending) {
            val json = value as? String ?: continue
            val info = runCatching { gson.fromJson(json, PendingPdf::class.java) }.getOrNull()
            if (info == null) {
                prefs.edit().remove(key).apply()
                continue
            }
            if (onlyReport != null && onlyReport != info.reportNumber?.trim()) continue
            if (wifiOnly && !deps.network.isOnWifi()) {
                Log.i(BackupPass.TAG, "PDFs paused: off Wi-Fi")
                return false
            }
            val file = File(key.removePrefix(PENDING))
            if (!file.isFile || file.length() <= 0) {
                removeIfSame(key, json)
                continue
            }
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                Report.exception(e)
                continue
            }
            val hash = PdfHash.of(bytes)
            val doneKey = DONE + file.absolutePath
            if (hash == prefs.getString(doneKey, null)) {
                Log.i(BackupPass.TAG, "PDF unchanged, not copied again: ${file.name}")
                removeIfSame(key, json)
                continue
            }
            val path = StoragePaths.pdfPath(StoragePaths.pdfRoot(config.app), config.clientId, info.reportNumber, file.name)
            val metadata = linkedMapOf(
                "reportNumber" to nz(info.reportNumber),
                "reportServerId" to nz(info.reportServerId),
                "kind" to nz(info.kind),
                "landName" to nz(info.landName),
                "landId" to nz(info.landId),
                "generatedAt" to StoragePaths.stamp(info.generatedAtMs),
                "username" to nz(runCatching { config.loginIdProvider() }.getOrNull()),
                "appVersion" to config.appVersion,
                "contentHash" to hash
            )
            // No link: PDFs hold client details and signatures, so only console admins can read
            // them (storage.rules); the console makes its own link from storagePath.
            val upload = deps.uploader.upload(path, bytes, "application/pdf", metadata, withLink = false)
            if (!upload.stored) return false
            prefs.edit().putString(doneKey, hash).apply()
            removeIfSame(key, json)
            deps.logPdf(PdfLogEntry().apply {
                reportNumber = info.reportNumber
                reportServerId = info.reportServerId
                fileName = file.name
                kind = info.kind
                landName = info.landName
                landId = info.landId
                storagePath = path
                pdfUrl = upload.url
                sizeBytes = bytes.size.toLong()
                contentHash = hash
                generatedAtMs = info.generatedAtMs
            })
            Log.i(BackupPass.TAG, "copied PDF ${file.name} -> $path")
        }
        return true
    }

    /** A PDF regenerated while it was being copied stays pending for the next pass. */
    private fun removeIfSame(key: String, json: String) {
        if (prefs.getString(key, null) == json) prefs.edit().remove(key).apply()
    }

    private fun nz(value: String?) = value ?: ""

    companion object {
        const val PREFS = "pdf_backup"
        const val PENDING = "pending:"
        const val DONE = "done:"
        private val gson = Gson()

        /** Records a generated PDF as waiting for its copy (the newest generation wins). */
        fun markPending(context: Context, pdf: File, info: PdfInfo, nowMs: Long) {
            val pending = PendingPdf(info.reportNumber, info.reportServerId, info.kind, info.landName, info.landId, nowMs)
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PENDING + pdf.absolutePath, gson.toJson(pending))
                .apply()
        }

        fun isBlankReport(info: PdfInfo) = AuditFormat.isBlank(info.reportNumber)
    }
}
