package za.co.mjrsolutions.shared.app_config

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.time.Duration
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AppUpdateBannerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fake = FakeAppUpdateManager(context)

    // What the banner sees; each test sets these before building a screen.
    private var installer: String? = AppUpdateRules.PLAY_INSTALLER
    private var hour = 12
    private var minute = 0
    private var dayOffset = 0
    private var wifi = false
    private var online = true
    private var snapshot: AppConfigSnapshot? = null
    private var elapsed = 1_000_000L

    private val play = PlayUpdateCheck(
        managerFor = { fake },
        installerOf = { installer },
        elapsed = { elapsed },
        report = { _, _ -> },
        onChange = { AgriAppUpdate.notifyChanged() },
    )

    private val screens = mutableListOf<ActivityController<ComponentActivity>>()

    /** What the screen was told each time the banner came into view: "KIND fromMinVersion". */
    private val shown = mutableListOf<String>()

    private class Screen(val activity: ComponentActivity, val root: View, val banner: AppUpdateBanner) {
        val text: TextView get() = root.findViewById(R.id.agri_update_banner_text)
        val close: View get() = root.findViewById(R.id.agri_update_banner_close)
        val shown: Boolean get() = root.visibility == View.VISIBLE
    }

    private fun clock(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("Africa/Johannesburg")).apply {
        clear()
        set(2026, Calendar.NOVEMBER, 12 + dayOffset, hour, minute, 0)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** A main screen with the banner, created, started and resumed as Android would. */
    private fun openScreen(): Screen {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        screens += controller
        val activity = controller.get()
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        val root = LayoutInflater.from(activity).inflate(R.layout.agri_app_update_banner, frame, false)
        frame.addView(root)
        // Built in onCreate, as the apps do: Play's result launcher must register before the start.
        val flow = AppUpdateFlow(activity, play, managerFor = { fake }, online = { online }, elapsed = { elapsed })
        val banner = AppUpdateBanner(
            activity, root,
            { kind -> if (kind == UpdateKind.REQUIRED) "Update required." else "Update available." },
            config = { snapshot },
            play = play,
            flow = flow,
            now = ::clock,
            onUnmeteredWifi = { wifi },
            onShown = { kind, fromMinVersion -> shown += "$kind $fromMinVersion" },
        )
        controller.start().resume()
        idle()
        return Screen(activity, root, banner)
    }

    @After
    fun closeScreens() {
        for (controller in screens) controller.pause().stop().destroy()
    }

    private fun minVersionAhead(force: Boolean) = AppConfigSnapshot(
        currentVersion = "10.0.23",
        minVersion = "10.0.24",
        maintenanceMessage = null,
        updateRequired = true,
        forceUpdate = force,
        announcementMessage = null,
        maintenanceMode = false,
        minSyncIntervalMinutes = null,
        debugLoggingEnabled = false,
        extras = emptyMap(),
    )

    private fun storePageOpened(screen: Screen): Intent? = shadowOf(screen.activity).nextStartedActivity

    // ---- What the banner shows ------------------------------------------------------------

    @Test
    fun `a newer version on Play shows a suggested banner with an X in the evening`() {
        fake.setUpdateAvailable(2)
        hour = 19
        val screen = openScreen()
        assertTrue(screen.shown)
        assertEquals("Update available.", screen.text.text.toString())
        assertEquals(View.VISIBLE, screen.close.visibility)
    }

    @Test
    fun `in the day off unmetered Wi-Fi the banner stays hidden`() {
        fake.setUpdateAvailable(2)
        hour = 12
        wifi = false
        assertEquals(false, openScreen().shown)
    }

    @Test
    fun `on unmetered Wi-Fi the banner shows in the day`() {
        fake.setUpdateAvailable(2)
        hour = 12
        wifi = true
        assertTrue(openScreen().shown)
    }

    @Test
    fun `nothing newer means no banner`() {
        fake.setUpdateNotAvailable()
        hour = 20
        wifi = true
        assertEquals(false, openScreen().shown)
    }

    @Test
    fun `a forced minimum version says required, shows the version gap and has no X`() {
        snapshot = minVersionAhead(force = true)
        hour = 20
        val screen = openScreen()
        assertTrue(screen.shown)
        assertEquals("Update required. (v10.0.23 → v10.0.24)", screen.text.text.toString())
        assertEquals(View.GONE, screen.close.visibility)
    }

    @Test
    fun `a minimum version without force is suggested and shows the version gap`() {
        snapshot = minVersionAhead(force = false)
        installer = null
        hour = 20
        val screen = openScreen()
        assertEquals("Update available. (v10.0.23 → v10.0.24)", screen.text.text.toString())
        assertEquals(View.VISIBLE, screen.close.visibility)
    }

    @Test
    fun `a forced update is hidden in the day off unmetered Wi-Fi like any banner`() {
        snapshot = minVersionAhead(force = true)
        hour = 10
        wifi = false
        assertEquals(false, openScreen().shown)
    }

    // ---- The X --------------------------------------------------------------------------

    @Test
    fun `the X hides a suggested update for 24 hours, on this screen and the next`() {
        fake.setUpdateAvailable(2)
        hour = 20
        val first = openScreen()
        first.close.performClick()
        assertEquals(false, first.shown)

        dayOffset = 1
        minute = 0
        hour = 19
        assertEquals(false, openScreen().shown, "23 hours later it is still hidden")

        hour = 20
        assertTrue(openScreen().shown, "24 hours later it is back")
    }

    @Test
    fun `a forced update comes back even after a suggested one was hidden`() {
        fake.setUpdateAvailable(2)
        hour = 20
        openScreen().close.performClick()

        snapshot = minVersionAhead(force = true)
        val screen = openScreen()
        assertTrue(screen.shown)
        assertEquals(View.GONE, screen.close.visibility)
    }

    // ---- Changes while the screen is open --------------------------------------------------

    @Test
    fun `the banner appears by itself at 18 00`() {
        fake.setUpdateAvailable(2)
        hour = 17
        minute = 59
        val screen = openScreen()
        assertEquals(false, screen.shown)

        hour = 18
        minute = 0
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertTrue(screen.shown)
    }

    @Test
    fun `joining unmetered Wi-Fi while the screen is open shows the banner`() {
        fake.setUpdateAvailable(2)
        hour = 12
        val screen = openScreen()
        assertEquals(false, screen.shown)

        wifi = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = ShadowNetwork.newInstance(7)
        for (callback in shadowOf(cm).networkCallbacks.toList()) {
            callback.onCapabilitiesChanged(network, ShadowNetworkCapabilities.newInstance())
        }
        idle()
        assertTrue(screen.shown)
    }

    @Test
    fun `a new console config redraws the banner`() {
        hour = 20
        val screen = openScreen()
        assertEquals(false, screen.shown)

        snapshot = minVersionAhead(force = true)
        AgriAppUpdate.notifyChanged()
        idle()
        assertTrue(screen.shown)
    }

    @Test
    fun `a paused screen leaves no network callback behind`() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        openScreen()
        assertEquals(1, shadowOf(cm).networkCallbacks.size)
        screens.last().pause()
        assertEquals(0, shadowOf(cm).networkCallbacks.size)
        screens.last().resume()
    }

    // ---- Telling the screen the banner appeared (the app's optional pop-up) ------------------

    @Test
    fun `the screen hears nothing while the banner is held back in the day off Wi-Fi`() {
        snapshot = minVersionAhead(force = false)
        hour = 15
        minute = 43
        wifi = false
        openScreen()
        assertEquals(emptyList(), shown)
    }

    @Test
    fun `the screen hears once when the banner appears at 18 00, with where the update came from`() {
        snapshot = minVersionAhead(force = false)
        hour = 17
        minute = 59
        openScreen()
        assertEquals(emptyList(), shown)

        hour = 18
        minute = 0
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertEquals(listOf("SUGGESTED true"), shown)

        AgriAppUpdate.notifyChanged()
        idle()
        assertEquals(listOf("SUGGESTED true"), shown, "a redraw of the same banner says nothing new")
    }

    @Test
    fun `a Play-only update is reported as not from the minimum version`() {
        fake.setUpdateAvailable(2)
        hour = 20
        openScreen()
        assertEquals(listOf("SUGGESTED false"), shown)
    }

    @Test
    fun `a banner hidden with the X tells the screen nothing more`() {
        fake.setUpdateAvailable(2)
        hour = 20
        val screen = openScreen()
        screen.close.performClick()
        AgriAppUpdate.notifyChanged()
        idle()
        assertEquals(listOf("SUGGESTED false"), shown)
    }

    @Test
    fun `the banner coming back into view is heard again`() {
        snapshot = minVersionAhead(force = true)
        hour = 12
        wifi = true
        openScreen()
        assertEquals(listOf("REQUIRED true"), shown)

        wifi = false
        AgriAppUpdate.notifyChanged()
        idle()
        wifi = true
        AgriAppUpdate.notifyChanged()
        idle()
        assertEquals(listOf("REQUIRED true", "REQUIRED true"), shown)
    }

    // ---- The tap ------------------------------------------------------------------------

    @Test
    fun `tapping the banner opens Play's update screen inside the app`() {
        fake.setUpdateAvailable(2)
        hour = 20
        val screen = openScreen()
        screen.root.performClick()
        idle()
        assertTrue(fake.isImmediateFlowVisible)
        assertNull(storePageOpened(screen), "the Play Store page must not open")
    }

    @Test
    fun `the pop-up's Update opens Play's screen even while the banner is hidden`() {
        fake.setUpdateAvailable(2)
        snapshot = minVersionAhead(force = true)
        hour = 10
        wifi = false
        val screen = openScreen()
        assertEquals(false, screen.shown)
        screen.banner.startUpdate()
        idle()
        assertTrue(fake.isImmediateFlowVisible)
    }

    @Test
    fun `a copy that did not come from Play opens the store page`() {
        installer = "dev.firebase.appdistribution"
        snapshot = minVersionAhead(force = false)
        hour = 20
        val screen = openScreen()
        screen.root.performClick()
        idle()
        val page = storePageOpened(screen)
        assertEquals(Intent.ACTION_VIEW, page?.action)
        assertEquals("market://details?id=${context.packageName}", page?.dataString)
        assertEquals(AppUpdateRules.PLAY_INSTALLER, page?.`package`)
    }

    @Test
    fun `when the minimum is ahead but Play has nothing newer yet, the store page opens`() {
        fake.setUpdateNotAvailable()
        snapshot = minVersionAhead(force = true)
        hour = 20
        val screen = openScreen()
        screen.root.performClick()
        idle()
        assertEquals("market://details?id=${context.packageName}", storePageOpened(screen)?.dataString)
    }

    @Test
    fun `a Play update gone by the time of the tap hides the banner and opens nothing`() {
        fake.setUpdateAvailable(2)
        hour = 20
        val screen = openScreen()
        assertTrue(screen.shown)

        fake.setUpdateNotAvailable()
        screen.root.performClick()
        idle()
        assertEquals(false, screen.shown)
        assertNull(storePageOpened(screen))
        assertEquals(false, fake.isImmediateFlowVisible)
    }
}
