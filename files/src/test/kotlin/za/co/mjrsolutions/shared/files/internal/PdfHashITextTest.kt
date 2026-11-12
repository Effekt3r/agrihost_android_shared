package za.co.mjrsolutions.shared.files.internal

import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * PdfHash against what the apps' iText (5.1.0, the jar in hael, mr and vrugte) really writes.
 * hael rebuilds a land's audit PDF every time its row is shown, and vrugte rebuilds every report
 * PDF on each main-screen resume: an unchanged rebuild must hash the same or it is copied again.
 */
class PdfHashITextTest {

    private fun build(landName: String): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document()
        PdfWriter.getInstance(document, out)
        document.open()
        document.addTitle("Audit")
        document.addCreationDate()
        document.add(Paragraph("Land: $landName"))
        val table = PdfPTable(2)
        table.addCell("Damage")
        table.addCell("12%")
        document.add(table)
        document.close()
        return out.toByteArray()
    }

    @Test
    fun `the same PDF built twice hashes the same`() {
        val first = build("Kamp 3")
        Thread.sleep(1100) // the next second, so the dates iText writes differ too
        val second = build("Kamp 3")

        // iText writes a new /CreationDate, /ModDate and /ID on every build...
        assertNotEquals(String(first, Charsets.ISO_8859_1), String(second, Charsets.ISO_8859_1))
        // ...and the fingerprint leaves exactly those out.
        assertEquals(PdfHash.of(first), PdfHash.of(second))
    }

    @Test
    fun `a PDF that shows something else hashes differently`() {
        assertNotEquals(PdfHash.of(build("Kamp 3")), PdfHash.of(build("Kamp 4")))
    }
}
