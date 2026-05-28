package za.co.mjrsolutions.shared.permissions.internal

import android.content.Context
import android.location.LocationManager

object LocationStateProbe {

    @JvmStatic
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
