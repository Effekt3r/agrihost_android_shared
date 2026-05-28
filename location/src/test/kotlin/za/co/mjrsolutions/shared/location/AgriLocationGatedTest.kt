package za.co.mjrsolutions.shared.location

import android.Manifest
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import za.co.mjrsolutions.shared.permissions.AgriPermissions
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class AgriLocationGatedTest {

    @Test fun `startUpdates with permission granted invokes onReady without NPE`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        AgriPermissions.init(app)
        AgriLocation.configure(LocationConfig())
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val readyFired = AtomicBoolean(false)
        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AgriLocation.startUpdates(
                    activity,
                    object : LocationListener {
                        override fun onLocationUpdate(fix: AgriFix) {}
                    },
                    object : LocationGate {
                        override fun onReady() { readyFired.set(true) }
                        override fun onPermissionDenied(permanent: Boolean) {}
                        override fun onLocationDisabled() {}
                    }
                )
            }
        }
        assert(readyFired.get()) { "onReady should fire when permission granted" }
    }

    @Test fun `process-restoration scenario does not crash with NPE`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Simulate post-process-death state: clear all static refs, then re-init.
        AgriLocation.configure(LocationConfig())
        AgriPermissions.init(app)
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // The bug pre-fix: this call NPE'd because LocationHelper.appContext was null.
                // After fix: activity.applicationContext is supplied locally; no static field
                // to be null, regardless of init order.
                runCatching {
                    AgriLocation.startUpdates(
                        activity,
                        object : LocationListener {
                            override fun onLocationUpdate(fix: AgriFix) {}
                        },
                        object : LocationGate {
                            override fun onReady() {}
                            override fun onPermissionDenied(permanent: Boolean) {}
                            override fun onLocationDisabled() {}
                        }
                    )
                }.onFailure { error("NPE regression — startUpdates threw: $it") }
            }
        }
    }
}
