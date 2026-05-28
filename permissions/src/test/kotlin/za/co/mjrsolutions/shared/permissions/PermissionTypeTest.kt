package za.co.mjrsolutions.shared.permissions

import android.Manifest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionTypeTest {

    @Test fun `LOCATION_FINE declares fine and coarse manifest permissions`() {
        val perms = PermissionType.LOCATION_FINE.manifestPermissions
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in perms)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in perms)
    }

    @Test fun `CAMERA declares CAMERA only`() {
        assertEquals(listOf(Manifest.permission.CAMERA), PermissionType.CAMERA.manifestPermissions.toList())
    }

    @Test fun `NOTIFICATIONS minSdk is 33`() {
        assertEquals(33, PermissionType.NOTIFICATIONS.minSdk)
    }

    @Test fun `BLUETOOTH minSdk is 31`() {
        assertEquals(31, PermissionType.BLUETOOTH.minSdk)
    }

    @Test fun `all types have non-blank rationale keys`() {
        for (type in PermissionType.entries) {
            assertTrue(type.rationaleTitleKey.isNotBlank(), "${type.name} missing rationaleTitleKey")
            assertTrue(type.rationaleBodyKey.isNotBlank(), "${type.name} missing rationaleBodyKey")
        }
    }

    @Test fun `NOTIFICATIONS declares POST_NOTIFICATIONS exactly`() {
        assertEquals(
            listOf("android.permission.POST_NOTIFICATIONS"),
            PermissionType.NOTIFICATIONS.manifestPermissions
        )
    }

    @Test fun `BLUETOOTH declares BLUETOOTH_CONNECT and BLUETOOTH_SCAN`() {
        val perms = PermissionType.BLUETOOTH.manifestPermissions
        assertTrue("android.permission.BLUETOOTH_CONNECT" in perms)
        assertTrue("android.permission.BLUETOOTH_SCAN" in perms)
    }

    @Test fun `MEDIA_IMAGES declares non-empty list on any SDK`() {
        // Robolectric defaults to recent SDK; verify the list is non-empty regardless.
        assertTrue(PermissionType.MEDIA_IMAGES.manifestPermissions.isNotEmpty())
    }
}
