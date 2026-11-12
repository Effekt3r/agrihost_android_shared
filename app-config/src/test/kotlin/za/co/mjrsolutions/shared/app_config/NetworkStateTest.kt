package za.co.mjrsolutions.shared.app_config

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class NetworkStateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun activeNetworkIs(transport: Int, metered: Boolean, validated: Boolean = true) {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(transport)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (metered) {
            shadowOf(caps).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } else {
            shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        if (validated) {
            shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            shadowOf(caps).removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
    }

    @Test
    fun `office or home Wi-Fi counts as unmetered Wi-Fi`() {
        activeNetworkIs(NetworkCapabilities.TRANSPORT_WIFI, metered = false)
        assertTrue(NetworkState.isOnUnmeteredWifi(context))
    }

    @Test
    fun `a phone hotspot is Wi-Fi but metered, so it does not count`() {
        activeNetworkIs(NetworkCapabilities.TRANSPORT_WIFI, metered = true)
        assertFalse(NetworkState.isOnUnmeteredWifi(context))
    }

    @Test
    fun `mobile data never counts, even unmetered`() {
        activeNetworkIs(NetworkCapabilities.TRANSPORT_CELLULAR, metered = false)
        assertFalse(NetworkState.isOnUnmeteredWifi(context))
    }

    @Test
    fun `no network is neither Wi-Fi nor online`() {
        shadowOf(cm).setDefaultNetworkActive(false)
        shadowOf(cm).setActiveNetworkInfo(null)
        assertFalse(NetworkState.isOnUnmeteredWifi(context))
        assertFalse(NetworkState.isOnline(context))
    }

    @Test
    fun `online means a network the system has validated`() {
        activeNetworkIs(NetworkCapabilities.TRANSPORT_WIFI, metered = true, validated = true)
        assertTrue(NetworkState.isOnline(context))
        activeNetworkIs(NetworkCapabilities.TRANSPORT_WIFI, metered = true, validated = false)
        assertFalse(NetworkState.isOnline(context))
    }
}
