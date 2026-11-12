package za.co.mjrsolutions.shared.app_config

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** What the system says about the tablet's current network. Never throws. */
internal object NetworkState {

    /**
     * True on Wi-Fi the system does not count as metered: office or home Wi-Fi. A phone's hotspot is
     * Wi-Fi too, but Android marks it metered, so it does not count; neither does mobile data. The
     * same rule as the photo backup's Wi-Fi-only copying.
     */
    fun isOnUnmeteredWifi(context: Context): Boolean {
        val caps = activeCapabilities(context) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** True on a network the system has validated as reaching the internet. */
    fun isOnline(context: Context): Boolean {
        val caps = activeCapabilities(context) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun activeCapabilities(context: Context): NetworkCapabilities? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        if (network == null) null else cm.getNetworkCapabilities(network)
    } catch (e: Exception) {
        // A missing ACCESS_NETWORK_STATE, or a system service that went away: no network.
        null
    }
}
