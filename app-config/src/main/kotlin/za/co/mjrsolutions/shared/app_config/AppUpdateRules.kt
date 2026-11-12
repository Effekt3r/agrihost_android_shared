package za.co.mjrsolutions.shared.app_config

import com.google.android.play.core.install.model.UpdateAvailability
import java.util.Calendar

/**
 * What one answer from Google Play said.
 * WAITING: Play has a newer version for this install.
 * NONE: it has none.
 * KEEP: no usable answer (an unknown code), so the last answer stands.
 */
enum class PlayAnswer { WAITING, NONE, KEEP }

/**
 * Which update the red banner is about.
 * REQUIRED: the app is below the console's minVersion and Force is ticked.
 * SUGGESTED: below a minVersion without Force, or Google Play has a newer version. The X hides it.
 * NONE: nothing to show.
 */
enum class UpdateKind { NONE, SUGGESTED, REQUIRED }

/**
 * The rules behind the red update banner, with no Android or Play calls, so they are JVM-tested
 * (AppUpdateRulesTest). AgriAppUpdate asks Play; AppUpdateBanner applies these to what it sees.
 */
object AppUpdateRules {

    const val PLAY_INSTALLER = "com.android.vending"

    /** After a failed Play check, the next one waits this long ([playDue]). */
    const val PLAY_RETRY_AFTER_FAILURE_MS = 15L * 60L * 1000L

    /** A check with no answer after this long is given up on ([checkStillRunning]). */
    const val CHECK_TIMEOUT_MS = 60L * 1000L

    /** The banner shows from this hour (device time) up to [MORNING_END_HOUR], Wi-Fi or not. */
    const val EVENING_START_HOUR = 18

    /** See [EVENING_START_HOUR]. */
    const val MORNING_END_HOUR = 6

    /** How long the X hides a suggested update. */
    const val SUGGESTED_DISMISS_MS = 24L * 60L * 60L * 1000L

    /**
     * True when Google Play installed the app: PackageManager's installing package, trimmed, is
     * exactly Play's. Anything else (App Distribution, adb, a sideloaded APK, another store) has no
     * Play to ask.
     */
    fun installedByPlay(installer: String?): Boolean = installer?.trim() == PLAY_INSTALLER

    /** Play's AppUpdateInfo.updateAvailability(). An update of ours that was interrupted still waits. */
    fun fromPlay(availability: Int): PlayAnswer = when (availability) {
        UpdateAvailability.UPDATE_AVAILABLE,
        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> PlayAnswer.WAITING
        UpdateAvailability.UPDATE_NOT_AVAILABLE -> PlayAnswer.NONE
        else -> PlayAnswer.KEEP
    }

    /**
     * True when Play's last check didn't fail, or failed [PLAY_RETRY_AFTER_FAILURE_MS] ago or more.
     * Some Play errors come back on every resume (the app not owned by the Play account on the
     * tablet, no Play Store), so after a failure the next check waits. Both times come from
     * SystemClock.elapsedRealtime.
     */
    fun playDue(lastFailedAtMs: Long?, nowMs: Long): Boolean =
        lastFailedAtMs == null || nowMs - lastFailedAtMs >= PLAY_RETRY_AFTER_FAILURE_MS

    /**
     * True while the check that started at [startedAtMs] may still answer. One with no answer after
     * [CHECK_TIMEOUT_MS] no longer holds up later checks. Both times come from
     * SystemClock.elapsedRealtime.
     */
    fun checkStillRunning(startedAtMs: Long?, nowMs: Long): Boolean =
        startedAtMs != null && nowMs - startedAtMs < CHECK_TIMEOUT_MS

    /** Required only below the minimum with Force ticked; any other gap is a suggestion. */
    fun kind(belowMinVersion: Boolean, forceUpdate: Boolean, playWaiting: Boolean): UpdateKind = when {
        belowMinVersion && forceUpdate -> UpdateKind.REQUIRED
        belowMinVersion || playWaiting -> UpdateKind.SUGGESTED
        else -> UpdateKind.NONE
    }

    /** 18:00 up to 06:00. */
    fun isEveningOrNight(hourOfDay: Int): Boolean =
        hourOfDay >= EVENING_START_HOUR || hourOfDay < MORNING_END_HOUR

    /**
     * Whether the red banner shows: there is an update, it is evening or night or the tablet is on
     * unmetered Wi-Fi, and a suggested update was not hidden with the X. A required update ignores
     * the X. A dismissal more than [SUGGESTED_DISMISS_MS] away can only come from a clock that was
     * moved back, so it no longer counts.
     */
    fun bannerVisible(
        kind: UpdateKind,
        hourOfDay: Int,
        onUnmeteredWifi: Boolean,
        dismissedUntilMs: Long,
        nowWallMs: Long,
    ): Boolean {
        if (kind == UpdateKind.NONE) return false
        if (!onUnmeteredWifi && !isEveningOrNight(hourOfDay)) return false
        if (kind == UpdateKind.SUGGESTED) {
            val remaining = dismissedUntilMs - nowWallMs
            if (remaining in 1..SUGGESTED_DISMISS_MS) return false
        }
        return true
    }

    /** When a suggested update hidden now with the X comes back. */
    fun dismissedUntil(nowWallMs: Long): Long = nowWallMs + SUGGESTED_DISMISS_MS

    /**
     * Milliseconds from [now] to the next 06:00 or 18:00, when the hours rule can change on its own.
     * [now] is not changed.
     */
    fun millisToNextWindowEdge(now: Calendar): Long {
        val next = now.clone() as Calendar
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        when {
            hour < MORNING_END_HOUR -> next.set(Calendar.HOUR_OF_DAY, MORNING_END_HOUR)
            hour < EVENING_START_HOUR -> next.set(Calendar.HOUR_OF_DAY, EVENING_START_HOUR)
            else -> {
                next.add(Calendar.DAY_OF_YEAR, 1)
                next.set(Calendar.HOUR_OF_DAY, MORNING_END_HOUR)
            }
        }
        return next.timeInMillis - now.timeInMillis
    }
}
