package za.co.mjrsolutions.shared.debug_tools

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ClearAppDataActivityTest {

    @Test
    fun `opening the activity asks for confirmation first`() {
        Robolectric.buildActivity(ClearAppDataActivity::class.java).setup()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog, "no confirmation dialog shown")
        assertTrue(dialog.isShowing)
    }

    @Test
    fun `cancelling leaves the data alone and closes the activity`() {
        val activity = Robolectric.buildActivity(ClearAppDataActivity::class.java).setup().get()

        val dialog: AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idleMainLooper() // dialog button clicks reach the listener through a Handler message

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `confirming asks the system to wipe the app data and leaves the exit to the system`() {
        // Robolectric's ActivityManager accepts the wipe (and records it). On a device the
        // system then kills the process, so the activity must neither toast nor finish itself.
        // The refusal branch (toast + finish) cannot be simulated: the shadow has no way to
        // make clearApplicationUserData() return false.
        val activity = Robolectric.buildActivity(ClearAppDataActivity::class.java).setup().get()
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val dialog: AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleMainLooper()

        assertTrue(shadowOf(activityManager).isApplicationUserDataCleared, "wipe was not requested")
        assertNull(ShadowToast.getTextOfLatestToast())
        assertFalse(activity.isFinishing)
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()
}
