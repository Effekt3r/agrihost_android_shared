package za.co.mjrsolutions.shared.app_config

import android.content.DialogInterface
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AppUpdateUiTest {

    private fun activity(): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light)
        return controller.setup().get()
    }

    private fun tapUpdate() {
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `the Update button runs the app's update instead of opening the store page`() {
        val activity = activity()
        var updates = 0
        AppUpdateUi.showUpdateDialog(
            activity, "Update required", "Please update.", "Update",
            laterButtonText = null,
            onUpdate = Runnable { updates++ },
        )
        tapUpdate()
        assertEquals(1, updates)
        assertNull(shadowOf(activity).nextStartedActivity, "the store page must not open")
    }

    @Test
    fun `without an update of the app's own the Update button opens the store page`() {
        val activity = activity()
        AppUpdateUi.showUpdateDialog(activity, "Update required", "Please update.", "Update")
        tapUpdate()
        val page = shadowOf(activity).nextStartedActivity
        assertTrue(page?.dataString == "market://details?id=${activity.packageName}", "store page: ${page?.dataString}")
    }
}
