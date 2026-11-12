package za.co.mjrsolutions.shared.files

import za.co.mjrsolutions.shared.audit.AuditFormat

/**
 * Where each app's copies live in Firebase Storage (hael's layout, one root per app; storage.rules
 * has a match block per root). Plain functions, so the names are the same in tests and on a tablet.
 */
object StoragePaths {

    /** Folder and console report segment for photos no test points at any more. */
    const val UNLINKED = "_unlinked"
    const val DB_EXPORTS_ROOT = "db_exports"

    /** hael_photos, mr_photos, vrugte_photos. */
    @JvmStatic fun photoRoot(app: String): String = "${app}_photos"

    /** hael_pdfs, mr_pdfs, vrugte_pdfs. */
    @JvmStatic fun pdfRoot(app: String): String = "${app}_pdfs"

    @JvmStatic fun segment(value: String?): String = AuditFormat.segment(value)

    @JvmStatic fun stamp(ms: Long): String = AuditFormat.stamp(ms)

    /** {root}/{client}/{report}/{land}_{landId}/test_{n}/photo{slot}_{taken}_{mediaId}.jpg */
    @JvmStatic
    fun photoPath(root: String, clientId: String, reportNumber: String?, landName: String?, landId: String?,
                  testIndex: String?, slot: Int, takenAtMs: Long, mediaId: Long): String =
        root + "/" + segment(clientId) + "/" + segment(reportNumber) + "/" +
            segment(landName) + "_" + segment(landId) + "/test_" + segment(testIndex) + "/photo" + slot +
            "_" + stamp(takenAtMs) + "_" + mediaId + ".jpg"

    /** {root}/{client}/_unlinked/{username}/{taken}_{mediaId}.jpg - a photo no test points at. */
    @JvmStatic
    fun unlinkedPhotoPath(root: String, clientId: String, username: String?, takenAtMs: Long, mediaId: Long): String =
        root + "/" + segment(clientId) + "/" + UNLINKED + "/" + segment(username) + "/" + stamp(takenAtMs) +
            "_" + mediaId + ".jpg"

    /** {root}/{client}/{report}/{file}.pdf - the same name each time, so a changed PDF replaces the copy. */
    @JvmStatic
    fun pdfPath(root: String, clientId: String, reportNumber: String?, fileName: String?): String =
        root + "/" + segment(clientId) + "/" + segment(AuditFormat.reportSegment(reportNumber)) + "/" + segment(fileName)

    /** db_exports/{client}/{username}/{zip} (storage.rules: db_exports/{client}/{user}/{fileName}). */
    @JvmStatic
    fun dbExportPath(clientId: String, username: String?, fileName: String): String =
        DB_EXPORTS_ROOT + "/" + clientId + "/" + segment(username) + "/" + fileName
}
