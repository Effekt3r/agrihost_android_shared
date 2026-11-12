package za.co.mjrsolutions.shared.app_config

import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdateRulesTest {

    // ---- Who installed the app ----------------------------------------------------------

    @Test
    fun `only a Play install is checked with Play`() {
        assertTrue(AppUpdateRules.installedByPlay("com.android.vending"))
        assertTrue(AppUpdateRules.installedByPlay(" com.android.vending "))
        assertFalse(AppUpdateRules.installedByPlay(null))
        assertFalse(AppUpdateRules.installedByPlay(""))
        assertFalse(AppUpdateRules.installedByPlay("com.huawei.appmarket"))
        assertFalse(AppUpdateRules.installedByPlay("dev.firebase.appdistribution"))
        assertFalse(AppUpdateRules.installedByPlay("com.google.android.packageinstaller"))
        assertFalse(AppUpdateRules.installedByPlay("com.android.vending.fake"))
    }

    // ---- Play's answers -----------------------------------------------------------------

    @Test
    fun `Play availability codes map to an answer`() {
        // UpdateAvailability: 0 UNKNOWN, 1 UPDATE_NOT_AVAILABLE, 2 UPDATE_AVAILABLE,
        // 3 DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS.
        assertEquals(PlayAnswer.KEEP, AppUpdateRules.fromPlay(0))
        assertEquals(PlayAnswer.NONE, AppUpdateRules.fromPlay(1))
        assertEquals(PlayAnswer.WAITING, AppUpdateRules.fromPlay(2))
        assertEquals(PlayAnswer.WAITING, AppUpdateRules.fromPlay(3))
        assertEquals(PlayAnswer.KEEP, AppUpdateRules.fromPlay(99))
    }

    @Test
    fun `after a failed Play check the next one waits 15 minutes`() {
        val failedAt = 1_000_000L
        assertTrue(AppUpdateRules.playDue(null, failedAt))
        assertFalse(AppUpdateRules.playDue(failedAt, failedAt))
        assertFalse(AppUpdateRules.playDue(failedAt, failedAt + 15 * 60_000L - 1))
        assertTrue(AppUpdateRules.playDue(failedAt, failedAt + 15 * 60_000L))
    }

    @Test
    fun `a check with no answer after a minute is given up on`() {
        val startedAt = 5_000L
        assertFalse(AppUpdateRules.checkStillRunning(null, startedAt))
        assertTrue(AppUpdateRules.checkStillRunning(startedAt, startedAt))
        assertTrue(AppUpdateRules.checkStillRunning(startedAt, startedAt + 59_999L))
        assertFalse(AppUpdateRules.checkStillRunning(startedAt, startedAt + 60_000L))
    }

    // ---- Which update the banner is about -------------------------------------------------

    @Test
    fun `required only when below the minimum version with force ticked`() {
        assertEquals(UpdateKind.REQUIRED, AppUpdateRules.kind(belowMinVersion = true, forceUpdate = true, playWaiting = false))
        assertEquals(UpdateKind.REQUIRED, AppUpdateRules.kind(belowMinVersion = true, forceUpdate = true, playWaiting = true))
    }

    @Test
    fun `suggested when below the minimum without force, or when Play has a newer version`() {
        assertEquals(UpdateKind.SUGGESTED, AppUpdateRules.kind(belowMinVersion = true, forceUpdate = false, playWaiting = false))
        assertEquals(UpdateKind.SUGGESTED, AppUpdateRules.kind(belowMinVersion = false, forceUpdate = false, playWaiting = true))
        // Force ticked in the console but the app is not below the minimum: only Play counts.
        assertEquals(UpdateKind.SUGGESTED, AppUpdateRules.kind(belowMinVersion = false, forceUpdate = true, playWaiting = true))
    }

    @Test
    fun `nothing when not below the minimum and Play has nothing newer`() {
        assertEquals(UpdateKind.NONE, AppUpdateRules.kind(belowMinVersion = false, forceUpdate = false, playWaiting = false))
        assertEquals(UpdateKind.NONE, AppUpdateRules.kind(belowMinVersion = false, forceUpdate = true, playWaiting = false))
    }

    // ---- When the banner may show ---------------------------------------------------------

    @Test
    fun `evening and night run from 18 00 up to 06 00`() {
        assertTrue(AppUpdateRules.isEveningOrNight(0))
        assertTrue(AppUpdateRules.isEveningOrNight(5))
        assertFalse(AppUpdateRules.isEveningOrNight(6))
        assertFalse(AppUpdateRules.isEveningOrNight(12))
        assertFalse(AppUpdateRules.isEveningOrNight(17))
        assertTrue(AppUpdateRules.isEveningOrNight(18))
        assertTrue(AppUpdateRules.isEveningOrNight(23))
    }

    private val now = 1_700_000_000_000L
    private val noDismissal = 0L

    @Test
    fun `daytime off unmetered Wi-Fi hides every banner, required included`() {
        for (kind in listOf(UpdateKind.SUGGESTED, UpdateKind.REQUIRED)) {
            assertFalse(AppUpdateRules.bannerVisible(kind, 6, onUnmeteredWifi = false, dismissedUntilMs = noDismissal, nowWallMs = now))
            assertFalse(AppUpdateRules.bannerVisible(kind, 12, onUnmeteredWifi = false, dismissedUntilMs = noDismissal, nowWallMs = now))
            assertFalse(AppUpdateRules.bannerVisible(kind, 17, onUnmeteredWifi = false, dismissedUntilMs = noDismissal, nowWallMs = now))
        }
    }

    @Test
    fun `evening or night shows the banner without Wi-Fi`() {
        for (kind in listOf(UpdateKind.SUGGESTED, UpdateKind.REQUIRED)) {
            assertTrue(AppUpdateRules.bannerVisible(kind, 18, onUnmeteredWifi = false, dismissedUntilMs = noDismissal, nowWallMs = now))
            assertTrue(AppUpdateRules.bannerVisible(kind, 2, onUnmeteredWifi = false, dismissedUntilMs = noDismissal, nowWallMs = now))
        }
    }

    @Test
    fun `unmetered Wi-Fi shows the banner in the day`() {
        for (kind in listOf(UpdateKind.SUGGESTED, UpdateKind.REQUIRED)) {
            assertTrue(AppUpdateRules.bannerVisible(kind, 12, onUnmeteredWifi = true, dismissedUntilMs = noDismissal, nowWallMs = now))
        }
    }

    @Test
    fun `no update means no banner at any hour`() {
        assertFalse(AppUpdateRules.bannerVisible(UpdateKind.NONE, 20, onUnmeteredWifi = true, dismissedUntilMs = noDismissal, nowWallMs = now))
    }

    @Test
    fun `the X hides a suggested update until its time runs out`() {
        val until = now + 24 * 3_600_000L
        assertFalse(AppUpdateRules.bannerVisible(UpdateKind.SUGGESTED, 20, true, dismissedUntilMs = until, nowWallMs = now))
        assertFalse(AppUpdateRules.bannerVisible(UpdateKind.SUGGESTED, 20, true, dismissedUntilMs = until, nowWallMs = until - 1))
        assertTrue(AppUpdateRules.bannerVisible(UpdateKind.SUGGESTED, 20, true, dismissedUntilMs = until, nowWallMs = until))
    }

    @Test
    fun `a dismissal never hides a required update`() {
        val until = now + 24 * 3_600_000L
        assertTrue(AppUpdateRules.bannerVisible(UpdateKind.REQUIRED, 20, true, dismissedUntilMs = until, nowWallMs = now))
    }

    @Test
    fun `a clock moved back does not stretch a dismissal past 24 hours`() {
        // Dismissed, then the tablet's clock was set two days back: the stored end is now
        // more than 24 hours away, which a real dismissal can never be.
        val until = now + 72 * 3_600_000L
        assertTrue(AppUpdateRules.bannerVisible(UpdateKind.SUGGESTED, 20, true, dismissedUntilMs = until, nowWallMs = now))
    }

    @Test
    fun `dismissing hides a suggested update for 24 hours`() {
        assertEquals(now + 86_400_000L, AppUpdateRules.dismissedUntil(now))
    }

    // ---- When the hours rule changes by itself ---------------------------------------------

    private fun at(hour: Int, minute: Int = 0, second: Int = 0, ms: Int = 0): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Africa/Johannesburg")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 12, hour, minute, second)
            set(Calendar.MILLISECOND, ms)
        }

    @Test
    fun `the next edge is 06 00 or 18 00, whichever comes first`() {
        assertEquals(6 * 3_600_000L, AppUpdateRules.millisToNextWindowEdge(at(0)))
        assertEquals(1L, AppUpdateRules.millisToNextWindowEdge(at(5, 59, 59, 999)))
        assertEquals(12 * 3_600_000L, AppUpdateRules.millisToNextWindowEdge(at(6)))
        assertEquals(60_000L, AppUpdateRules.millisToNextWindowEdge(at(17, 59)))
        assertEquals(12 * 3_600_000L, AppUpdateRules.millisToNextWindowEdge(at(18)))
        assertEquals(6 * 3_600_000L + 30 * 60_000L, AppUpdateRules.millisToNextWindowEdge(at(23, 30)))
    }

    @Test
    fun `the next edge does not change the time it was given`() {
        val given = at(10, 15)
        val before = given.timeInMillis
        AppUpdateRules.millisToNextWindowEdge(given)
        assertEquals(before, given.timeInMillis)
    }
}
