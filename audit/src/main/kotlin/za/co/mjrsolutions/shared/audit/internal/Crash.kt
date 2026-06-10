package za.co.mjrsolutions.shared.audit.internal

import android.util.Log

internal object Crash {
    fun record(t: Throwable) {
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t)
        } catch (suppressed: Throwable) { // incl. NoClassDefFoundError off-app
            Log.e("AgriAudit", "audit error (crashlytics unavailable)", t)
        }
    }
}
