package za.co.mjrsolutions.shared.app_config

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PlayUpdateCheckTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fake = FakeAppUpdateManager(context)
    private var now = 100_000L
    private val reported = mutableListOf<String>()
    private var changes = 0

    private fun playCheck(
        installer: String? = AppUpdateRules.PLAY_INSTALLER,
        manager: AppUpdateManager = fake,
    ) = PlayUpdateCheck(
        managerFor = { manager },
        installerOf = { installer },
        elapsed = { now },
        report = { where, _ -> reported += where },
        onChange = { changes++ },
    )

    /** Play's Task listeners run on the main looper, which Robolectric holds until idled. */
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Play's manager, but its update info can be made to fail or to never answer. */
    private class ControlledManager(private val real: AppUpdateManager) : AppUpdateManager by real {
        var fail = false
        var hang = false
        override fun getAppUpdateInfo(): Task<AppUpdateInfo> = when {
            fail -> Tasks.forException(IllegalStateException("Play said no"))
            hang -> TaskCompletionSource<AppUpdateInfo>().task
            else -> real.appUpdateInfo
        }
    }

    @Test
    fun `an update on Play waits, and goes once Play has none`() {
        val play = playCheck()
        fake.setUpdateAvailable(2)
        play.check(context); idle()
        assertTrue(play.waiting)
        assertEquals(1, changes)

        fake.setUpdateNotAvailable()
        play.check(context); idle()
        assertFalse(play.waiting)
        assertEquals(2, changes)
    }

    @Test
    fun `the same answer again is not a change`() {
        val play = playCheck()
        fake.setUpdateAvailable(2)
        play.check(context); idle()
        play.check(context); idle()
        assertTrue(play.waiting)
        assertEquals(1, changes)
    }

    @Test
    fun `an install that did not come from Play never shows a Play update`() {
        val play = playCheck(installer = "dev.firebase.appdistribution")
        fake.setUpdateAvailable(2)
        play.check(context); idle()
        assertFalse(play.waiting)
        assertEquals(0, changes)
    }

    @Test
    fun `a failed check keeps the last answer and Play is not asked again for 15 minutes`() {
        val manager = ControlledManager(fake)
        val play = playCheck(manager = manager)
        fake.setUpdateAvailable(2)
        play.check(context); idle()
        assertTrue(play.waiting)

        manager.fail = true
        now += 1_000L
        play.check(context); idle()
        assertTrue(play.waiting, "a failure keeps the last answer")
        assertEquals(listOf("Play check"), reported)

        manager.fail = false
        fake.setUpdateNotAvailable()
        now += 15 * 60_000L - 1
        play.check(context); idle()
        assertTrue(play.waiting, "Play is not asked again within 15 minutes of a failure")

        now += 1
        play.check(context); idle()
        assertFalse(play.waiting)
    }

    @Test
    fun `a check with no answer is given up after a minute and counts as a failure`() {
        val manager = ControlledManager(fake)
        val play = playCheck(manager = manager)
        fake.setUpdateAvailable(2)
        manager.hang = true
        play.check(context); idle()

        manager.hang = false
        now += 59_999L
        play.check(context); idle()
        assertFalse(play.waiting, "a check still running is not started again")

        now += 1L
        play.check(context); idle()
        assertFalse(play.waiting, "the give-up counts as a failure, so Play waits 15 minutes")
        assertEquals(listOf("Play check"), reported)

        now += 15 * 60_000L
        play.check(context); idle()
        assertTrue(play.waiting)
    }

    @Test
    fun `Play's answer reaches the caller with the manager that gave it`() {
        val play = playCheck()
        fake.setUpdateAvailable(7)
        var seenCode = -1
        var seenManager: AppUpdateManager? = null
        play.check(context) { manager, info ->
            seenManager = manager
            seenCode = info.availableVersionCode()
        }
        idle()
        assertEquals(7, seenCode)
        assertTrue(seenManager === fake)
    }

    @Test
    fun `a throwing caller is reported, not a crash, and the answer still counts`() {
        val play = playCheck()
        fake.setUpdateAvailable(2)
        play.check(context) { _, _ -> throw IllegalStateException("caller broke") }
        idle()
        assertTrue(play.waiting)
        assertEquals(listOf("Play answer"), reported)
    }

    @Test
    fun `the installer is read from the package manager`() {
        // Robolectric's package manager keeps what this deprecated setter stores.
        @Suppress("DEPRECATION")
        context.packageManager.setInstallerPackageName(context.packageName, "com.android.vending")
        assertEquals("com.android.vending", installerOf(context))
    }

    @Test
    @Config(sdk = [30])
    fun `the installer is read from the install source on Android 11 and up`() {
        shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, "com.android.vending", "com.android.vending")
        assertEquals("com.android.vending", installerOf(context))
    }
}
