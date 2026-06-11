package za.co.mjrsolutions.shared.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult

internal class FreshFixRequester(
    private val context: Context,
    private val config: LocationConfig,
    private val callback: FixCallback,
    /** Invoked on the main looper for every fix received while waiting — lets a
     *  progress UI show the live accuracy as the fix converges. */
    private val onInterim: ((AgriFix) -> Unit)? = null
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bestSoFar: AgriFix? = null
    private var completed = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (completed) return
            for (loc in result.locations) {
                val fix = FixFilter.toAgriFix(loc, config, System.currentTimeMillis())
                if (bestSoFar == null || isBetter(fix, bestSoFar!!)) {
                    bestSoFar = fix
                }
                onInterim?.invoke(fix)
                if (fix.isFresh && fix.isAccurate) {
                    complete { callback.onFix(fix) }
                    return
                }
            }
        }
    }

    private val timeoutRunnable = Runnable {
        if (!completed) {
            complete { callback.onTimeout(bestSoFar) }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission(context)) {
            callback.onPermissionDenied()
            return
        }
        if (!FusedClient.isLocationEnabled(context)) {
            callback.onLocationDisabled()
            return
        }
        FusedClient.init(context)
        FusedClient.get().requestLocationUpdates(
            FusedClient.freshFixRequest(),
            locationCallback,
            Looper.getMainLooper()
        )
        mainHandler.postDelayed(timeoutRunnable, config.freshFixTimeoutMs)
    }

    private fun hasPermission(context: Context): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun complete(action: () -> Unit) {
        completed = true
        mainHandler.removeCallbacks(timeoutRunnable)
        try {
            FusedClient.get().removeLocationUpdates(locationCallback)
        } catch (_: Exception) {
        }
        action()
    }

    private fun isBetter(a: AgriFix, b: AgriFix): Boolean {
        if (a.isFresh && !b.isFresh) return true
        if (a.isAccurate && !b.isAccurate) return true
        if (a.ageMs < b.ageMs) return true
        return a.accuracyMeters < b.accuracyMeters
    }
}
