package za.co.mjrsolutions.shared.files.photos

import android.app.Application
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class GalleryPhotosTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `galleryKeyRebuildsUri - one spelling per photo, files get their own`() {
        assertEquals("content://media/external/images/media/1272",
            GalleryPhotos.key("content://media/external/images/media/1272]"))
        assertEquals("content://media/external/images/media/1272",
            GalleryPhotos.key(" content://media/external_primary/images/media/1272 "))
        assertEquals("file:/data/user/0/x/files/images/a.jpg", GalleryPhotos.key("/data/user/0/x/files/images/a.jpg"))
        assertEquals(1272L, GalleryPhotos.mediaId("content://media/external/images/media/1272"))
        assertEquals(-1L, GalleryPhotos.mediaId("null"))
        assertTrue(GalleryPhotos.isGalleryLink("content://media/external/images/media/5"))
        assertFalse(GalleryPhotos.isGalleryLink("/sdcard/a.jpg"))
    }

    @Test
    fun `msFromName - hael and vrugte camera names, nothing else`() {
        assertEquals(1_794_471_300_000L, GalleryPhotos.msFromName("photo_1794471300000.jpg"))
        assertEquals(1_794_471_300_000L, GalleryPhotos.msFromName("photo_2_1794471300000.jpg"))
        assertEquals(0L, GalleryPhotos.msFromName("JPEG_20261112_101500_1.jpg"))
        assertEquals(0L, GalleryPhotos.msFromName(null))
    }

    @Test
    fun `newPhotoValues - photo_ms jpg under Pictures from Android 10`() {
        val q = AgriPhotoCapture.newPhotoValues(1000L, 29)
        assertEquals("photo_1000.jpg", q.getAsString(MediaStore.Images.Media.DISPLAY_NAME))
        assertEquals("image/jpeg", q.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals("Pictures", q.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertFalse(AgriPhotoCapture.newPhotoValues(1000L, 28).containsKey(MediaStore.Images.Media.RELATIVE_PATH))
    }

    @Test
    fun `captureLogRoundTrip - hael's existing values still read`() {
        val uri = "content://media/external/images/media/1272"
        PhotoCaptureLog.record(app, uri, 1_000L, " -26.1,28.0 ")
        val c = PhotoCaptureLog.read(app, uri)!!
        assertEquals(1_000L, c.takenAtMs)
        assertEquals("-26.1,28.0", c.gps)
        assertNull(c.placement)

        // A value hael 10.0.24 wrote: "{ms}|" with no fix at hand.
        app.getSharedPreferences("photo_capture_log", Context.MODE_PRIVATE).edit()
            .putString("content://media/external/images/media/77", "1794471300000|").commit()
        val old = PhotoCaptureLog.read(app, "content://media/external/images/media/77")!!
        assertEquals(1_794_471_300_000L, old.takenAtMs)
        assertNull(old.gps)
    }

    @Test
    fun `placementRoundTrip - where a vrugte photo belongs survives the link being cleared`() {
        val uri = "content://media/external/images/media/88"
        PhotoCaptureLog.record(app, uri, 5L, null, PhotoPlacement().apply {
            reportNumber = "881"; landName = "Blok 3"; landId = "12"; testIndex = "1"; slot = 2
        })
        val p = PhotoCaptureLog.read(app, uri)!!.placement!!
        assertEquals("881", p.reportNumber)
        assertEquals("Blok 3", p.landName)
        assertEquals(2, p.slot)
    }
}
