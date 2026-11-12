package za.co.mjrsolutions.shared.files.pdf

/**
 * What a generated PDF belongs to, for its Storage folder and its console entry.
 * @param reportNumber the report number the app's photos and sends are filed under
 * @param kind "report", "audit", or the app's own name for the PDF
 */
class PdfInfo @JvmOverloads constructor(
    @JvmField val reportNumber: String?,
    @JvmField val reportServerId: String?,
    @JvmField val kind: String?,
    @JvmField val landName: String? = null,
    @JvmField val landId: String? = null
)
