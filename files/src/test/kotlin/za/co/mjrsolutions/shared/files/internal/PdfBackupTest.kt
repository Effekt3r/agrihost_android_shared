package za.co.mjrsolutions.shared.files.internal

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.files.FilesConfig
import za.co.mjrsolutions.shared.files.pdf.PdfInfo
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PdfBackupTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val uploader = FakeUploader()
    private val recorded = Recorded()
    private val config = FilesConfig("vrugte", "SKADEGENADE_vrugte", { "janb" }, { "Jan Botha" }, "8.0.70", 1, "x")

    @Before
    fun clearPrefs() {
        listOf("photo_backup", "pdf_backup").forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    /** iText's shape: content, then an info dictionary with dates, then the trailer /ID. */
    private fun pdf(content: String, created: String, id: String): ByteArray =
        ("%PDF-1.4\n1 0 obj<</Length 20>>stream\n$content\nendstream endobj\n" +
            "2 0 obj<</Producer(iText 5.1.0)/CreationDate(D:$created+02'00')/ModDate(D:$created+02'00')>>endobj\n" +
            "trailer<</Root 3 0 R/Info 2 0 R/ID [<$id><$id>]>>\n%%EOF").toByteArray(Charsets.ISO_8859_1)

    private fun pass() = BackupPass(app, config, fakeDeps(uploader, FakeNetwork(), FakeGallery(), recorded))

    private fun write(name: String, bytes: ByteArray): File =
        File(app.getExternalFilesDir("Documents"), name).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    @Test
    fun `hashIgnoresDatesAndId - a rebuild of the same report hashes the same`() {
        val a = pdf("BT (Skade 12%) Tj ET", "20261112101500", "0A1B2C3D4E5F60718293A4B5C6D7E8F9")
        val b = pdf("BT (Skade 12%) Tj ET", "20261113080000", "FFEEDDCCBBAA99887766554433221100")
        assertEquals(PdfHash.of(a), PdfHash.of(b))
    }

    @Test
    fun `hashSeesContentChange`() {
        val a = pdf("BT (Skade 12%) Tj ET", "20261112101500", "0A")
        val b = pdf("BT (Skade 13%) Tj ET", "20261112101500", "0A")
        assertNotEquals(PdfHash.of(a), PdfHash.of(b))
    }

    @Test
    fun `unchangedPdfNotCopiedAgain - vrugte's resume rebuild sends nothing`() {
        val file = write("881.pdf", pdf("BT (Skade 12%) Tj ET", "20261112101500", "0A"))
        PdfCopier.markPending(app, file, PdfInfo("881", "5881", "report"), 1L)
        pass().run(true, null)
        assertEquals(1, uploader.calls.size)
        assertEquals("vrugte_pdfs/SKADEGENADE_vrugte/881/881.pdf", uploader.calls[0].path)
        assertEquals("application/pdf", uploader.calls[0].contentType)
        assertEquals(false, uploader.calls[0].withLink) // admin-only reads: the console makes the link
        assertEquals("", recorded.pdfs[0].pdfUrl)
        assertEquals("vrugte_pdfs/SKADEGENADE_vrugte/881/881.pdf", recorded.pdfs[0].storagePath)

        write("881.pdf", pdf("BT (Skade 12%) Tj ET", "20261113090000", "FF")) // rebuilt, same content
        PdfCopier.markPending(app, file, PdfInfo("881", "5881", "report"), 2L)
        pass().run(true, null)
        assertEquals(1, uploader.calls.size)
        assertTrue(app.getSharedPreferences("pdf_backup", Context.MODE_PRIVATE).all.keys.none { it.startsWith("pending:") })
    }

    @Test
    fun `samePathOverwritten - a changed PDF replaces its copy and its entry`() {
        val file = write("881.pdf", pdf("BT (Skade 12%) Tj ET", "20261112101500", "0A"))
        PdfCopier.markPending(app, file, PdfInfo("881", "5881", "report"), 1L)
        pass().run(true, null)
        write("881.pdf", pdf("BT (Skade 20%) Tj ET", "20261113090000", "FF"))
        PdfCopier.markPending(app, file, PdfInfo("881", "5881", "report"), 2L)
        pass().run(true, null)
        assertEquals(2, uploader.calls.size)
        assertEquals(uploader.calls[0].path, uploader.calls[1].path)
        assertEquals(2, recorded.pdfs.size)
        assertEquals("881.pdf", recorded.pdfs[1].fileName)
        assertEquals("report", recorded.pdfs[1].kind)
        assertNotEquals(recorded.pdfs[0].contentHash, recorded.pdfs[1].contentHash)
    }

    @Test
    fun `missingFileDropsPending`() {
        val file = File(app.getExternalFilesDir("Documents"), "gone.pdf")
        PdfCopier.markPending(app, file, PdfInfo("1", null, "audit"), 1L)
        pass().run(true, null)
        assertTrue(uploader.calls.isEmpty())
        assertTrue(app.getSharedPreferences("pdf_backup", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun `syncTapForAnotherReportLeavesThePdfPending`() {
        val file = write("audit_x.pdf", pdf("BT (A) Tj ET", "20261112101500", "0A"))
        PdfCopier.markPending(app, file, PdfInfo("881", null, "audit"), 1L)
        pass().run(false, "900")
        assertTrue(uploader.calls.isEmpty())
        pass().run(false, "881")
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun `failedUploadKeepsThePdfPending`() {
        uploader.failOnCall = 0
        val file = write("881.pdf", pdf("BT (A) Tj ET", "20261112101500", "0A"))
        PdfCopier.markPending(app, file, PdfInfo("881", null, "report"), 1L)
        pass().run(true, null)
        assertTrue(recorded.pdfs.isEmpty())
        assertTrue(app.getSharedPreferences("pdf_backup", Context.MODE_PRIVATE).all.keys.any { it.startsWith("pending:") })
    }
}
