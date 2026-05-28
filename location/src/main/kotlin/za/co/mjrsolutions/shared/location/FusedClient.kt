package za.co.mjrsolutions.shared.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

internal object FusedClient {

    @Volatile private var client: FusedLocationProviderClient? = null

    fun init(context: Context) {
        if (client == null) {
            client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        }
    }

    fun get(): FusedLocationProviderClient = client ?: error(
        "FusedClient not initialized. AgriLocation.init(...) must run from Application.onCreate."
    )

    fun freshFixRequest(): LocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 1_000L
    )
        .setMinUpdateIntervalMillis(500L)
        .setMaxUpdateAgeMillis(0L)
        .setWaitForAccurateLocation(true)
        .setMaxUpdateDelayMillis(2_000L)
        .build()

    fun continuousRequest(config: LocationConfig): LocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, config.continuousIntervalMs
    )
        .setMinUpdateIntervalMillis(config.continuousMinUpdateIntervalMs)
        .setMaxUpdateAgeMillis(config.maxFixAgeMs)
        .build()

    @SuppressLint("MissingPermission")
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
