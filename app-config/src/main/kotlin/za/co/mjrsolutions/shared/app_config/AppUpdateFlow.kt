package za.co.mjrsolutions.shared.app_config

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.model.ActivityResult as PlayActivityResult

/** Taps closer together than this count once: one Play screen per double tap. */
private const val TAP_WINDOW_MS = 1_000L

/**
 * The update itself, for one main screen: Google Play's full-screen update inside the app. Play
 * downloads and installs the update, then restarts the app.
 *
 * - Only Play can update an install Play made. A copy from anywhere else (App Distribution, adb)
 *   gets the app's Play Store page instead, as does a tap when Play's screen can't open.
 * - An update the assessor accepted and then left continues on the next resume while the tablet is
 *   online (Play's guidance). Offline the download can't move, so the app stays usable and the next
 *   resume online brings Play's screen back.
 * - A failure while continuing on resume is only reported: opening the store page by itself would
 *   open it again on every return to the app.
 *
 * Created in the screen's onCreate: its launcher must register before the screen starts. Nothing
 * here throws to the screen; each way in reports what it catches.
 */
internal class AppUpdateFlow(
    private val activity: ComponentActivity,
    private val play: PlayUpdateCheck,
    private val managerFor: (Context) -> AppUpdateManager,
    private val online: () -> Boolean,
    private val elapsed: () -> Long,
) {

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(StartIntentSenderForResult()) { result ->
            guarded("Play result") { onPlayResult(result) }
        }

    /** Play's full-screen update: whether it is allowed, and the launch. */
    private val immediate: AppUpdateOptions = AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)

    /**
     * Whether the last launch of Play's screen came from a tap: only a tap's failure opens the store
     * page. It starts false after the screen is recreated, the safe side (reported, no store page).
     */
    private var launchedFromTap = false

    /** When the last tap that counted came, or null before the first. */
    private var lastTapAt: Long? = null

    /** When a tap's request to Play went out, until it answers; one with no answer in a minute no longer holds taps back. */
    private var tapInFlightSince: Long? = null

    /** The screen's onResume: Play's check, and an interrupted update of ours continues while online. */
    fun onResume() {
        guarded("Resume") {
            play.check(activity) { manager, info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    if (online()) {
                        startImmediate(manager, info, fromTap = false)
                    } else {
                        AgriAppUpdate.log("interrupted update not continued while offline")
                    }
                }
            }
        }
    }

    /**
     * The banner's tap or a pop-up's Update button. [belowMinVersion]: the console's minimum is ahead
     * of this version, so when Play has nothing newer (Play not caught up yet) the store page opens
     * rather than nothing. Without it, a Play update that is gone by now just clears the banner.
     */
    fun start(belowMinVersion: Boolean) {
        guarded("Tap") {
            val now = elapsed()
            val last = lastTapAt
            if (last != null && now - last < TAP_WINDOW_MS) return
            if (AppUpdateRules.checkStillRunning(tapInFlightSince, now)) return
            lastTapAt = now
            if (!play.installedByPlay(activity)) {
                openStorePage("not installed from Play")
                return
            }
            startPlay(belowMinVersion)
        }
    }

    private fun startPlay(belowMinVersion: Boolean) {
        val sentAt = elapsed()
        try {
            val manager = managerFor(activity)
            // A fresh AppUpdateInfo: Play lets each one start a single update.
            tapInFlightSince = sentAt
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    // Each answer guards itself: nothing catches what a Task listener throws.
                    try {
                        when (AppUpdateRules.fromPlay(info.updateAvailability())) {
                            PlayAnswer.WAITING -> startImmediate(manager, info, fromTap = true)
                            PlayAnswer.NONE -> {
                                play.apply(info.updateAvailability())
                                if (belowMinVersion) {
                                    openStorePage("Play: nothing newer yet, minimum version ahead")
                                } else {
                                    AgriAppUpdate.log("Play: no update any more")
                                }
                            }
                            PlayAnswer.KEEP -> openStorePage("Play: availability ${info.updateAvailability()}")
                        }
                    } catch (e: Exception) {
                        AgriAppUpdate.reportFailure("Play update", e)
                    }
                }
                .addOnFailureListener { e ->
                    try {
                        AgriAppUpdate.reportFailure("Play update", e)
                        openStorePage("Play: update info failed")
                    } catch (ex: Exception) {
                        AgriAppUpdate.reportFailure("Play update", ex)
                    }
                }
                .addOnCompleteListener { if (tapInFlightSince == sentAt) tapInFlightSince = null }
        } catch (e: Exception) {
            if (tapInFlightSince == sentAt) tapInFlightSince = null
            AgriAppUpdate.reportFailure("Play update", e)
            openStorePage("Play: no update manager")
        }
    }

    /**
     * Play's full-screen update for [info]. From a tap, a screen that can't open sends the assessor
     * to the store page; from a resume the failure is only reported ([playFailed]).
     */
    private fun startImmediate(manager: AppUpdateManager, info: AppUpdateInfo, fromTap: Boolean) {
        if (!isUsable()) return
        if (!info.isUpdateTypeAllowed(immediate)) {
            val failed = info.getFailedUpdatePreconditions(immediate)
            playFailed(fromTap, "Play: immediate update not allowed, failed preconditions $failed")
            return
        }
        // Set before the launch: androidx reports a launch that throws later, through onPlayResult.
        launchedFromTap = fromTap
        val started = try {
            manager.startUpdateFlowForResult(info, launcher, immediate)
        } catch (e: Exception) {
            playFailed(fromTap, "Play: update screen didn't open", e)
            return
        }
        when {
            !started -> playFailed(fromTap, "Play: update screen didn't open")
            fromTap -> AgriAppUpdate.log("Play: update screen opened, version ${info.availableVersionCode()}")
            else -> AgriAppUpdate.log("Play: interrupted update continued, install status ${info.installStatus()}")
        }
    }

    /** What Play's update screen answered. A failure after a tap's launch opens the store page. */
    private fun onPlayResult(result: ActivityResult) {
        val fromTap = launchedFromTap
        launchedFromTap = false
        when (result.resultCode) {
            Activity.RESULT_OK -> AgriAppUpdate.log("Play: update accepted")
            // androidx reports a launch that threw (a SendIntentException) as a cancel carrying it.
            Activity.RESULT_CANCELED ->
                if (result.data?.hasExtra(StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true) {
                    resultFailed(fromTap, "Play: update screen couldn't open")
                } else {
                    AgriAppUpdate.log("Play: update cancelled")
                }
            PlayActivityResult.RESULT_IN_APP_UPDATE_FAILED -> resultFailed(fromTap, "Play: in-app update failed")
            else -> AgriAppUpdate.log("Play: result ${result.resultCode}")
        }
    }

    private fun resultFailed(fromTap: Boolean, why: String) = playFailed(fromTap, why, AppUpdateException(why))

    /**
     * Play's update didn't run. From a tap the store page opens instead, [failure] reported first.
     * From a resume it is only reported, and the banner stays for the tap.
     */
    private fun playFailed(fromTap: Boolean, why: String, failure: Exception? = null) {
        if (fromTap) {
            if (failure != null) AgriAppUpdate.reportFailure("Play update", failure)
            openStorePage(why)
        } else {
            AgriAppUpdate.reportFailure("Play continue", failure ?: AppUpdateException(why))
        }
    }

    /**
     * The app's Play Store page: in the Play Store app first, then the market link any store may
     * open, then Play's web page. A link no app opens is skipped; any other failure is reported and
     * the next link is tried.
     */
    private fun openStorePage(why: String) {
        if (!isUsable()) return
        val pkg = activity.packageName
        val pages = listOf(
            storePage("market://details?id=$pkg", AppUpdateRules.PLAY_INSTALLER),
            storePage("market://details?id=$pkg"),
            storePage("https://play.google.com/store/apps/details?id=$pkg"),
        )
        for (page in pages) {
            val opened = try {
                activity.startActivity(page)
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: Exception) {
                AgriAppUpdate.reportFailure("Store page", e)
                false
            }
            if (opened) {
                AgriAppUpdate.log("$why: store page opened (${page.`package` ?: page.data?.scheme})")
                return
            }
        }
        AgriAppUpdate.reportFailure("Store page", AppUpdateException("$why: no app opens the store page"))
    }

    private fun storePage(link: String, storePackage: String? = null): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply { if (storePackage != null) setPackage(storePackage) }

    /** Runs [block], reporting what it throws as [where]'s failure: the flow never throws to the screen. */
    private inline fun guarded(where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            AgriAppUpdate.reportFailure(where, e)
        }
    }

    private fun isUsable(): Boolean = !activity.isFinishing && !activity.isDestroyed
}
