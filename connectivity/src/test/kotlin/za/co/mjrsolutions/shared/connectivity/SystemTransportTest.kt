package za.co.mjrsolutions.shared.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.assertEquals

/** The first two rungs of the ladder against the system's ConnectivityManager. */
@RunWith(RobolectricTestRunner::class)
class SystemTransportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Test
    fun `an active network whose capabilities are unavailable reads as no transport`() {
        // Robolectric's ShadowConnectivityManager cannot represent getActiveNetwork() == null
        // (it NPEs internally where a device returns null), so this covers the other real way
        // rung one fails: the network dropped between getActiveNetwork() and
        // getNetworkCapabilities(), which then returns null.
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork!!, null)
        assertEquals(TransportStatus.NONE, ConnectivityProbe.systemTransport(context))
    }

    @Test
    fun `an active network without a validated route reads as attached but unvalidated`() {
        val network = cm.activeNetwork!!
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(network, caps)

        assertEquals(TransportStatus.ATTACHED_UNVALIDATED, ConnectivityProbe.systemTransport(context))
    }

    @Test
    fun `a validated active network reads as validated`() {
        val network = cm.activeNetwork!!
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(cm).setNetworkCapabilities(network, caps)

        assertEquals(TransportStatus.VALIDATED, ConnectivityProbe.systemTransport(context))
    }
}
