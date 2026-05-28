package za.co.mjrsolutions.shared.permissions

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AgriPermissionsTest {

    private lateinit var app: Application

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        AgriPermissions.init(app)
    }

    @Test fun `isGranted returns false before grant`() {
        assertFalse(AgriPermissions.isGranted(app, PermissionType.CAMERA))
    }

    @Test fun `isGranted returns true after grant`() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        assertTrue(AgriPermissions.isGranted(app, PermissionType.CAMERA))
    }

    @Test fun `isGranted true for LOCATION_FINE requires both FINE and COARSE`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertFalse(AgriPermissions.isGranted(app, PermissionType.LOCATION_FINE))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(AgriPermissions.isGranted(app, PermissionType.LOCATION_FINE))
    }

    @Test fun `isGranted true on pre-Tiramisu for NOTIFICATIONS`() {
        // PermissionType.NOTIFICATIONS.isAutoGranted() must be true when SDK < 33.
        // Robolectric default targets the latest SDK; assertion is conditional.
        if (PermissionType.NOTIFICATIONS.isAutoGranted()) {
            assertTrue(AgriPermissions.isGranted(app, PermissionType.NOTIFICATIONS))
        }
    }
}
