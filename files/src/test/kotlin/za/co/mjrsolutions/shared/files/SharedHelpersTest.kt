package za.co.mjrsolutions.shared.files

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.files.backup.AgriBackup
import za.co.mjrsolutions.shared.files.pdf.PdfInfo
import za.co.mjrsolutions.shared.files.photos.GalleryPhotos
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SharedHelpersTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @After
    fun reset() = AgriFiles.resetForTest()

    @Test
    fun `readBytes - an old file-path link reads from the file`() {
        val f = File(app.filesDir, "images/a.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        assertContentEquals(byteArrayOf(1, 2, 3), GalleryPhotos.readBytes(app, f.absolutePath))
        assertNull(GalleryPhotos.readBytes(app, File(app.filesDir, "missing.jpg").absolutePath))
        assertNull(GalleryPhotos.readBytes(app, null))
    }

    @Test
    fun `pdfCreated without a context records the PDF for its copy`() {
        AgriFiles.init(app, FilesConfig("mr", "AGRIHOST_TEST_mr", { "williesnr" }, { "Willie Snr" }, "9.0.2", 9002, "x"))
        val pdf = File(app.filesDir, "x.pdf").apply { writeBytes(byteArrayOf(1)) }
        AgriBackup.pdfCreated(pdf, PdfInfo("40068", "40068", "emergence_report"))
        val pending = app.getSharedPreferences("pdf_backup", Context.MODE_PRIVATE).all.keys
        assertTrue(pending.contains("pending:" + pdf.absolutePath), pending.toString())
    }

    @Test
    fun `pdfCreated before init does nothing and never throws`() {
        AgriBackup.pdfCreated(File(app.filesDir, "y.pdf"), PdfInfo("1", null, "report"))
        assertTrue(app.getSharedPreferences("pdf_backup", Context.MODE_PRIVATE).all.isEmpty())
    }
}
