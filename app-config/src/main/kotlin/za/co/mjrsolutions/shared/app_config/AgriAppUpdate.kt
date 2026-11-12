package za.co.mjrsolutions.shared.app_config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallException
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The process-wide side of the update banner: Google Play's answer for this install, the banners
 * to redraw when it or the console's config changes, and the reporting. AppUpdateBanner is the
 * part an app touches.
 */
internal object AgriAppUpdate {

    private const val TAG = "AgriAppUpdate"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** The redraws of the banners on screens now in view (added on resume, removed on pause). */
    private val listeners = CopyOnWriteArraySet<Runnable>()

    /** The failures already sent as non-fatals in this process, by place and failure. */
    private val recorded = HashSet<String>()

    /** Play's answer, kept for the process. */
    val play = PlayUpdateCheck(
        managerFor = ::managerFor,
        installerOf = ::installerOf,
        elapsed = SystemClock::elapsedRealtime,
        report = ::reportFailure,
        onChange = ::notifyChanged,
    )

    fun managerFor(context: Context): AppUpdateManager =
        AppUpdateManagerFactory.create(context.applicationContext)

    fun addListener(listener: Runnable) {
        listeners.add(listener)
    }

    fun removeListener(listener: Runnable) {
        listeners.remove(listener)
    }

    /** Play's answer or the console's config changed: every banner in view redraws. Any thread. */
    fun notifyChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (listener in listeners) listener.run()
        } else {
            mainHandler.post { for (listener in listeners) listener.run() }
        }
    }

    /**
     * A failed check or tap: a breadcrumb every time, and a Crashlytics non-fatal the first time each
     * failure (place and what failed) happens in this process, so a Play error that comes back on
     * every resume makes one report, not dozens. It names what failed (Play's error code or the
     * failure's class), never the assessor.
     */
    fun reportFailure(where: String, e: Throwable) {
        val detail = describe(e)
        log("$where failed: $detail")
        val first = synchronized(recorded) { recorded.add("$where: $detail") }
        if (!first) return
        try {
            FirebaseCrashlytics.getInstance().recordException(e)
        } catch (_: Throwable) {
            // Crashlytics unavailable (unit tests, Firebase not initialised): the breadcrumb is all.
        }
    }

    /** A Crashlytics breadcrumb: shows with the next report from this process. Never throws. */
    fun log(message: String) {
        try {
            FirebaseCrashlytics.getInstance().log("$TAG: $message")
        } catch (_: Throwable) {
            // As above.
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is AppUpdateException -> e.message ?: "AppUpdateException"
        is InstallException -> "InstallException ${e.errorCode}"
        else -> e.javaClass.name
    }
}
