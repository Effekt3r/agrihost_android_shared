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
        for (type in PermissionType.values()) {
            assertTrue(type.rationaleTitleKey.isNotBlank(), "${type.name} missing rationaleTitleKey")
            assertTrue(type.rationaleBodyKey.isNotBlank(), "${type.name} missing rationaleBodyKey")
        }
    }
}
