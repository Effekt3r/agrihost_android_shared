package za.co.mjrsolutions.shared.files

import android.Manifest
import org.junit.Test
import kotlin.test.assertContentEquals

class StoragePermissionsTest {

    @Test
    fun `android 9 and older - write and read, the camera's gallery row needs write access`() {
        val both = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        assertContentEquals(both, StoragePermissions.required(24))
        assertContentEquals(both, StoragePermissions.required(28))
    }

    @Test
    fun `android 10 to 12 - read only, never write (declared to Android 9 only, it would come back denied)`() {
        val read = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertContentEquals(read, StoragePermissions.required(29))
        assertContentEquals(read, StoragePermissions.required(30))
        assertContentEquals(read, StoragePermissions.required(32))
    }

    @Test
    fun `android 13 and up - nothing, read external storage does not exist for the app there`() {
        assertContentEquals(emptyArray(), StoragePermissions.required(33))
        assertContentEquals(emptyArray(), StoragePermissions.required(34))
        assertContentEquals(emptyArray(), StoragePermissions.required(36))
    }
}
