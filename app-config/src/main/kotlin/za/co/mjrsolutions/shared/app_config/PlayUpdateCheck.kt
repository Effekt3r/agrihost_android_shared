package za.co.mjrsolutions.shared.app_config

import android.content.Context
import android.os.Build
import androidx.annotation.MainThread
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager

/**
 * Whether Google Play has a newer version of this install, kept for the process (a cold start
 * shows no Play update until the first check answers, well under a second). Main thread only.
 *
 * - Only an app that Play installed is checked: nothing else has a Play to ask.
 * - One check at a time. One with no answer after a minute is given up on and counts as a failed
 *   check (its answer still applies if it comes).
 * - After a failed check Play is not asked again for 15 minutes: some Play errors come back on
 *   every resume. A failure keeps the last answer.
 */
internal class PlayUpdateCheck(
    private val managerFor: (Context) -> AppUpdateManager,
    private val installerOf: (Context) -> String?,
    private val elapsed: () -> Long,
    private val report: (where: String, e: Throwable) -> Unit,
    private val onChange: () -> Unit,
) {

    /** True while Play has a newer version for this install. */
    var waiting: Boolean = false
        private set

    /** When the check now running started, or null. Each check keeps its own start ([endCheck]). */
    private var checkStartedAt: Long? = null

    /** When Play's last check failed, or null once Play answers ([AppUpdateRules.playDue]). */
    private var failedAt: Long? = null

    /** Whether Play installed the app, once a lookup succeeds; an update ends the process. */
    private var cachedInstalledByPlay: Boolean? = null

    /**
     * Asks Play when it installed the app and a check is due. [onPlayInfo] also gets Play's answer,
     * with the manager that gave it: AppUpdateFlow continues an interrupted update of ours with it.
     */
    @MainThread
    fun check(context: Context, onPlayInfo: ((AppUpdateManager, AppUpdateInfo) -> Unit)? = null) {
        if (!installedByPlay(context)) return
        val now = elapsed()
        if (AppUpdateRules.checkStillRunning(checkStartedAt, now)) return
        if (checkStartedAt != null) {
            // No answer within a minute: given up on and counted as a failure, so a Play that never
            // answers is not asked (and reported) again on every resume.
            checkStartedAt = null
            failedAt = now
            report("Play check", AppUpdateException("no answer within a minute"))
        }
        if (AppUpdateRules.playDue(failedAt, now)) ask(context, onPlayInfo)
    }

    /** Whether Play installed this app. A lookup that fails isn't kept: the next check asks again. */
    @MainThread
    fun installedByPlay(context: Context): Boolean {
        cachedInstalledByPlay?.let { return it }
        return try {
            AppUpdateRules.installedByPlay(installerOf(context)).also { cachedInstalledByPlay = it }
        } catch (e: Exception) {
            report("Installer", e)
            false
        }
    }

    /**
     * Play's availability, from a check or from a tap's own request: an update waits, there is none,
     * or the last answer stands. A change tells [onChange].
     */
    @MainThread
    fun apply(availability: Int) {
        val next = when (AppUpdateRules.fromPlay(availability)) {
            PlayAnswer.WAITING -> true
            PlayAnswer.NONE -> false
            PlayAnswer.KEEP -> return
        }
        if (next == waiting) return
        waiting = next
        onChange()
    }

    private fun ask(context: Context, onPlayInfo: ((AppUpdateManager, AppUpdateInfo) -> Unit)?) {
        val startedAt = elapsed()
        checkStartedAt = startedAt
        try {
            val manager = managerFor(context)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    // Play's Task listeners don't catch: a throw here would crash the app on resume.
                    try {
                        failedAt = null
                        apply(info.updateAvailability())
                    } catch (e: Exception) {
                        report("Play answer", e)
                    }
                    if (onPlayInfo != null) {
                        try {
                            onPlayInfo(manager, info)
                        } catch (e: Exception) {
                            report("Play answer", e)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    failedAt = elapsed()
                    report("Play check", e)
                }
                .addOnCompleteListener { endCheck(startedAt) }
        } catch (e: Exception) {
            endCheck(startedAt)
            failedAt = elapsed()
            report("Play check", e)
        }
    }

    /** Ends the check that started at [startedAt]; a late answer from one given up on leaves a newer one alone. */
    private fun endCheck(startedAt: Long) {
        if (checkStartedAt == startedAt) checkStartedAt = null
    }
}

/** A check or tap that failed without an exception of its own (a status, or no answer). */
internal class AppUpdateException(message: String) : Exception(message)

/** The package that installed this app: the install source from Android 11, the installer before. */
internal fun installerOf(context: Context): String? {
    val pm = context.packageManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        pm.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        pm.getInstallerPackageName(context.packageName)
    }
}
