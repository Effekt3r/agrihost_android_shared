package za.co.mjrsolutions.shared.connectivity

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Crashlytics non-fatal that never throws itself (Firebase absent in unit tests). */
internal fun recordNonFatal(t: Throwable) {
    try {
        FirebaseCrashlytics.getInstance().recordException(t)
    } catch (_: Throwable) {
        // Nothing to report to; the caller already handled the failure.
    }
}
