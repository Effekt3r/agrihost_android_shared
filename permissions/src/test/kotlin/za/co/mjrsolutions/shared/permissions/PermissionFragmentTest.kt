package za.co.mjrsolutions.shared.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import za.co.mjrsolutions.shared.permissions.internal.PermissionFragment
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class PermissionFragmentTest {

    @Test fun `concurrent requests for the same type fire one prompt and both callbacks`() {
        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val frag = PermissionFragment.attach(activity)
                val callCount = AtomicInteger(0)
                val cb1 = object : PermissionCallback {
                    override fun onGranted() { callCount.incrementAndGet() }
                    override fun onDenied(permanentlyDenied: List<String>) {}
                }
                val cb2 = object : PermissionCallback {
                    override fun onGranted() { callCount.incrementAndGet() }
                    override fun onDenied(permanentlyDenied: List<String>) {}
                }
                frag.requestNow(PermissionType.CAMERA, cb1)
                frag.requestNow(PermissionType.CAMERA, cb2)
                // Simulate system grant
                frag.onRequestPermissionsResultInternal(
                    PermissionType.CAMERA,
                    arrayOf(Manifest.permission.CAMERA),
                    intArrayOf(PackageManager.PERMISSION_GRANTED)
                )
                assert(callCount.get() == 2) { "Both queued callbacks must fire; got ${callCount.get()}" }
            }
        }
    }
}
