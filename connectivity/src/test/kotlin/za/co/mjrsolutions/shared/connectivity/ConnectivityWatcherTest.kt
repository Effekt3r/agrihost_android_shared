package za.co.mjrsolutions.shared.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Probes run on a synchronous executor and deliveries land on the paused main looper, so
 * every hop is observable: idle the looper to run a tick, idleFor to reach the next one.
 */
@RunWith(RobolectricTestRunner::class)
class ConnectivityWatcherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val base = "https://dev.example.test/api"
    private val deliveries = mutableListOf<Pair<ConnectivityState, ConnectivityState>>()
    private val prober = FakeProber()
    private val executor = DirectExecutor()

    private val watcher = ConnectivityWatcher(
        context = context,
        baseUrlProvider = { base },
        listener = { previous, current -> deliveries += previous to current },
        pingIntervalMs = 30_000L,
        prober = prober,
        executor = executor,
        mainHandler = Handler(Looper.getMainLooper()),
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `start probes immediately and delivers the first state as unknown to online`() {
        watcher.start()
        idle()

        assertEquals(listOf(ConnectivityState.UNKNOWN to ConnectivityState.ONLINE), deliveries)
        assertEquals(ConnectivityState.ONLINE, watcher.state)
        assertEquals(listOf(base), prober.probed)
    }

    @Test
    fun `re-probes every interval while started`() {
        watcher.start()
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30_000L))

        assertEquals(2, prober.probed.size)
    }

    @Test
    fun `stop cancels the scheduled re-probe`() {
        watcher.start()
        idle()
        watcher.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60_000L))

        assertEquals(1, prober.probed.size)
    }

    @Test
    fun `a default network change drops the cached verdict and re-probes at once`() {
        watcher.start()
        idle()
        watcher.networkCallback.onLost(mock(Network::class.java))
        idle()

        assertEquals(1, prober.invalidations)
        assertEquals(2, prober.probed.size)
    }

    @Test
    fun `every delivery carries the previous state`() {
        watcher.start()
        idle()
        prober.answer = ConnectivityState.NO_INTERNET
        watcher.pingNow()
        idle()

        assertEquals(ConnectivityState.ONLINE to ConnectivityState.NO_INTERNET, deliveries[1])
        assertTrue(watcher.state.isOffline)
    }

    @Test
    fun `a ping after shutdown neither throws nor probes`() {
        watcher.start()
        idle()
        watcher.shutdown()
        watcher.pingNow()
        idle()

        assertEquals(1, prober.probed.size)
        assertTrue(executor.isShutdown)
    }

    @Test
    fun `start registers the default network callback once and stop removes it`() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        watcher.start()
        watcher.start()
        assertEquals(1, shadowOf(cm).networkCallbacks.size)
        watcher.stop()
        assertEquals(0, shadowOf(cm).networkCallbacks.size)
    }

    private class FakeProber : ConnectivityProber {
        var answer = ConnectivityState.ONLINE
        val probed = mutableListOf<String>()
        var invalidations = 0

        override fun probe(baseUrl: String): ConnectivityState {
            probed += baseUrl
            return answer
        }

        override fun invalidateInternetVerdict() {
            invalidations++
        }
    }

    /** Runs each task on the calling thread so a probe completes inside the tick that started it. */
    private class DirectExecutor : AbstractExecutorService() {
        private var down = false
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() { down = true }
        override fun shutdownNow(): MutableList<Runnable> { down = true; return mutableListOf() }
        override fun isShutdown() = down
        override fun isTerminated() = down
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
}
