package za.co.mjrsolutions.shared.files

import org.junit.Test
import kotlin.test.assertEquals

class StoragePathsTest {

    // 2026-11-05 10:12:30 SAST = 08:12:30 UTC (hael PhotoSlotsTest)
    private val taken = 1_793_866_350_000L

    @Test
    fun `haelPhotoPathUnchanged - one folder per report, land and test`() {
        assertEquals("hael_photos/GEMOEDSRUS/4806/K2_154594/test_1/photo1_20261105-101230_1000003776.jpg",
            StoragePaths.photoPath("hael_photos", "GEMOEDSRUS", "4806", "K2", "154594", "1", 1, taken, 1000003776L))
        assertEquals("hael_photos/GEMOEDSRUS/4806/V12A_Noord_unknown/test_2/photo2_unknown_5.jpg",
            StoragePaths.photoPath("hael_photos", "GEMOEDSRUS", "4806", "V12A Noord", null, "2", 2, 0, 5))
        assertEquals("hael_photos/GEMOEDSRUS/_unlinked/JanEras/20261105-101230_7.jpg",
            StoragePaths.unlinkedPhotoPath("hael_photos", "GEMOEDSRUS", "JanEras", taken, 7))
    }

    @Test
    fun `rootsPerApp - hael keeps hael_photos, the others get their own`() {
        assertEquals("hael_photos", StoragePaths.photoRoot("hael"))
        assertEquals("mr_photos", StoragePaths.photoRoot("mr"))
        assertEquals("vrugte_pdfs", StoragePaths.pdfRoot("vrugte"))
    }

    @Test
    fun `pdfPathPerApp - one folder per report, the file under its own name`() {
        assertEquals("mr_pdfs/AGRIHOST_TEST_mr/40068/Emergence_40068.pdf",
            StoragePaths.pdfPath("mr_pdfs", "AGRIHOST_TEST_mr", "40068", "Emergence_40068.pdf"))
        assertEquals("hael_pdfs/AGRIHOST_TEST/Unknown/report_12.pdf",
            StoragePaths.pdfPath("hael_pdfs", "AGRIHOST_TEST", null, "report_12.pdf"))
        assertEquals("vrugte_pdfs/SKADEGENADE_vrugte/881/audit_Blok_3_12_Plaas_881_2.pdf",
            StoragePaths.pdfPath("vrugte_pdfs", "SKADEGENADE_vrugte", "881", "audit_Blok 3_12_Plaas_881_2.pdf"))
    }

    @Test
    fun `dbExportPath - hael's db_exports layout`() {
        assertEquals("db_exports/AGRIHOST_TEST_mr/williesnr/agrihost_db_AGRIHOST_TEST_mr_Willie_65_20261112_101500.zip",
            StoragePaths.dbExportPath("AGRIHOST_TEST_mr", "williesnr",
                "agrihost_db_AGRIHOST_TEST_mr_Willie_65_20261112_101500.zip"))
        assertEquals("db_exports/AGRIHOST_TEST/unknown/a.zip", StoragePaths.dbExportPath("AGRIHOST_TEST", null, "a.zip"))
    }

    @Test
    fun `segmentNeverEscapesItsFolder`() {
        assertEquals("unknown", StoragePaths.segment(".."))
        assertEquals("a_b", StoragePaths.segment("a/b"))
        assertEquals("Erasmus_Jan", StoragePaths.segment("Erasmus, Jan"))
        assertEquals("unknown", StoragePaths.segment(null))
    }
}
