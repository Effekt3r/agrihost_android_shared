package za.co.mjrsolutions.shared.files.internal

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.files.FilesConfig
import za.co.mjrsolutions.shared.files.StoragePaths
import za.co.mjrsolutions.shared.files.backup.LinkedPhoto
import za.co.mjrsolutions.shared.files.backup.PhotoSource
import za.co.mjrsolutions.shared.files.photos.PhotoCaptureLog
import za.co.mjrsolutions.shared.files.photos.PhotoPlacement
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BackupPassTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val ours get() = app.packageName
    private val uploader = FakeUploader()
    private val recorded = Recorded()

    @Before
    fun clearPrefs() {
        listOf("photo_backup", "photo_capture_log", "pdf_backup").forEach {
            app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun linked(id: Long, report: String = "7721", sent: Boolean = false, slot: Int = 1) = LinkedPhoto().apply {
        uri = "content://media/external/images/media/$id"; this.slot = slot; sentToServer = sent
        reportNumber = report; reportServerId = "s$report"; landName = "Land A"; landId = "30135"; testIndex = "1"
    }

    private fun config(source: PhotoSource?) = FilesConfig(
        app = "hael", clientId = "AGRIHOST_TEST", loginIdProvider = { "janb" }, fullNameProvider = { "Jan Botha" },
        appVersion = "10.0.24", versionCode = 1, applicationId = "x", photoSource = source
    )

    private fun pass(source: PhotoSource?, gallery: FakeGallery, network: FakeNetwork = FakeNetwork(), sdk: Int = 33) =
        BackupPass(app, config(source), fakeDeps(uploader, network, gallery, recorded, sdk))

    @Test
    fun `doneSetSkipsCopied - photos hael already copied are not uploaded again after the update`() {
        app.getSharedPreferences("photo_backup", Context.MODE_PRIVATE).edit()
            .putString("content://media/external/images/media/5", "hael_photos/AGRIHOST_TEST/7721/x.jpg").commit()
        pass({ listOf(linked(5)) }, FakeGallery(mapOf(5L to galleryRow(5, ours)))).run(true, null)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `unsentFirst - photos the server doesn't have yet go before sent ones`() {
        val gallery = FakeGallery(mapOf(6L to galleryRow(6, ours), 7L to galleryRow(7, ours)))
        pass({ listOf(linked(6, sent = true), linked(7, sent = false)) }, gallery).run(true, null)
        assertEquals(listOf("7", "6"), uploader.calls.map { it.metadata["mediaId"] })
        assertEquals(2, recorded.photos.size)
        assertTrue(recorded.photos[0].storagePath!!.startsWith("hael_photos/AGRIHOST_TEST/7721/Land_A_30135/test_1/photo1_"))
    }

    @Test
    fun `stopsAtFirstFailedUpload - nothing marked, the next pass carries on`() {
        uploader.failOnCall = 0
        val gallery = FakeGallery(mapOf(6L to galleryRow(6, ours), 7L to galleryRow(7, ours)))
        pass({ listOf(linked(6), linked(7)) }, gallery).run(true, null)
        assertEquals(1, uploader.calls.size)
        assertTrue(recorded.photos.isEmpty())
        assertTrue(app.getSharedPreferences("photo_backup", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun `stopsWhenWifiLost - a pass that leaves Wi-Fi stops after the photo in hand`() {
        val gallery = FakeGallery(mapOf(6L to galleryRow(6, ours), 7L to galleryRow(7, ours)))
        // Wi-Fi at the start and before the first photo, gone before the second.
        pass({ listOf(linked(6), linked(7)) }, gallery, FakeNetwork(wifiChecks = 2)).run(true, null)
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun `skipsNotOursAndMissing - remembered, never retried`() {
        val gallery = FakeGallery(mapOf(9L to galleryRow(9, "some.other.app")))
        pass({ listOf(linked(8), linked(9)) }, gallery).run(true, null)
        val done = app.getSharedPreferences("photo_backup", Context.MODE_PRIVATE)
        assertEquals("skip:not_in_gallery", done.getString("content://media/external/images/media/8", null))
        assertEquals("skip:not_taken_by_this_app", done.getString("content://media/external/images/media/9", null))
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `runForReportOnlyThatReport - a Sync tap copies that report, nothing unlinked`() {
        val gallery = FakeGallery(
            mapOf(6L to galleryRow(6, ours), 7L to galleryRow(7, ours)),
            own = listOf(galleryRow(6, ours), galleryRow(7, ours), galleryRow(30, ours))
        )
        pass({ listOf(linked(6, report = "1"), linked(7, report = "2")) }, gallery).run(false, "2")
        assertEquals(listOf("7"), uploader.calls.map { it.metadata["mediaId"] })
    }

    @Test
    fun `unlinkedUsesPlacement - a vrugte photo whose link was cleared still lands in its report folder`() {
        val uri = "content://media/external/images/media/20"
        PhotoCaptureLog.record(app, uri, 1_794_471_300_000L, "-33.9,18.4", PhotoPlacement().apply {
            reportNumber = "881"; reportServerId = "5881"; landName = "Blok 3"; landId = "12"; testIndex = "1"; slot = 2
        })
        pass({ emptyList() }, FakeGallery(own = listOf(galleryRow(20, ours)))).run(true, null)
        assertEquals(
            StoragePaths.photoPath("hael_photos", "AGRIHOST_TEST", "881", "Blok 3", "12", "1", 2, 1_794_471_300_000L, 20),
            uploader.calls.single().path
        )
        assertEquals("881", recorded.photos.single().reportNumber)
        assertEquals("capture", recorded.photos.single().gpsSource)
    }

    @Test
    fun `unlinkedWithoutPlacement - hael's _unlinked folder under the login id`() {
        pass({ emptyList() }, FakeGallery(own = listOf(galleryRow(21, ours)))).run(true, null)
        assertEquals("hael_photos/AGRIHOST_TEST/_unlinked/janb/20261112-101500_21.jpg", uploader.calls.single().path)
        assertEquals("_unlinked", recorded.photos.single().reportNumber)
        assertEquals("true", uploader.calls.single().metadata["unlinked"])
    }

    @Test
    fun `sourceReturningNullSkipsPass - never guess when the tests could not be read`() {
        pass({ null }, FakeGallery(own = listOf(galleryRow(22, ours)))).run(true, null)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `fileLinkCopied - mr photos kept as files before the move still go`() {
        val dir = File(app.filesDir, "images").apply { mkdirs() }
        val photo = File(dir, "40068_12_test_image_1_1794471300000.jpg").apply { writeBytes(ByteArray(500)) }
        val p = linked(0).apply { uri = photo.absolutePath; reportNumber = "40068" }
        pass({ listOf(p) }, FakeGallery()).run(true, null)
        assertEquals(1, uploader.calls.size)
        val entry = recorded.photos.single()
        assertEquals("40068_12_test_image_1_1794471300000", entry.photoId)
        assertEquals(0L, entry.mediaId)
        assertTrue(app.getSharedPreferences("photo_backup", Context.MODE_PRIVATE).contains("file:" + photo.absolutePath))
    }

    @Test
    fun `nobodyLoggedInCopiesNothing`() {
        val cfg = config({ listOf(linked(6)) }).copy(loginIdProvider = { null })
        BackupPass(app, cfg, fakeDeps(uploader, FakeNetwork(), FakeGallery(mapOf(6L to galleryRow(6, ours))), recorded)).run(true, null)
        assertTrue(uploader.calls.isEmpty())
    }
}
