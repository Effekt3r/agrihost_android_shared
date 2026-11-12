package za.co.mjrsolutions.shared.files.internal

import android.util.Log

/** Crashlytics when the app has it; never throws (unit tests, apps without Crashlytics). */
internal object Report {
    fun exception(t: Throwable) {
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t)
        } catch (suppressed: Throwable) {
            try { Log.e("AgriFiles", "error (crashlytics unavailable)", t) } catch (ignored: Throwable) { }
        }
    }
}
