package za.co.mjrsolutions.shared.network.outbox

import android.net.Network
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ConnectivityMonitorTest {

    @Test
    fun `onAvailable fires the supplied callback`() {
        var fired = 0
        val monitor = ConnectivityMonitor(ApplicationProvider.getApplicationContext()) { fired++ }
        // Invoke the exposed callback directly — no need to simulate a real network transition.
        monitor.networkCallback.onAvailable(mock(Network::class.java))
        assertEquals(1, fired)
    }

    @Test
    fun `start and stop do not throw`() {
        val monitor = ConnectivityMonitor(ApplicationProvider.getApplicationContext()) {}
        monitor.start()
        monitor.stop()
    }
}
