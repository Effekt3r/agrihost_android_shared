package za.co.mjrsolutions.shared.diagnostics

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Logs the stall to logcat under [MainThreadWatchdog.TAG] and records it to Crashlytics as a
 * non-fatal whose stack IS the main thread's stack, so the reports group by the blocking
 * call rather than by the watchdog.
 */
internal class CrashlyticsStallReporter : StallReporter {

    override fun onStall(stalledMs: Long, mainThreadFrames: Array<StackTraceElement>) {
        Log.e(MainThreadWatchdog.TAG, formatStall(stalledMs, mainThreadFrames))
        try {
            val stall = Throwable("Main thread blocked >=${stalledMs}ms")
            stall.stackTrace = mainThreadFrames
            FirebaseCrashlytics.getInstance().recordException(stall)
        } catch (e: Exception) {
            Log.e(MainThreadWatchdog.TAG, "failed to record main thread stall", e)
        }
    }

    override fun onRecovered(stalledMs: Long) {
        Log.w(MainThreadWatchdog.TAG, "main thread recovered after ~${stalledMs}ms")
    }
}
