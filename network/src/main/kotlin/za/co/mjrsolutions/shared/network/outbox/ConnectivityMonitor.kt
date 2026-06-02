package za.co.mjrsolutions.shared.network.outbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Calls [onAvailable] whenever a network with INTERNET capability becomes available
 * (WiFi or cellular). The callback is exposed as [networkCallback] so it is directly
 * unit-testable without simulating a real transition.
 */
internal class ConnectivityMonitor(
    context: Context,
    private val onAvailable: () -> Unit
) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { onAvailable() }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, networkCallback)
        } catch (_: RuntimeException) {
            // too many listeners / not supported — non-fatal; periodic sync still runs.
        }
    }

    fun stop() {
        try {
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: RuntimeException) {
            // never registered — ignore.
        }
    }
}
