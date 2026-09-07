package za.co.mjrsolutions.shared.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Keeps a live [ConnectivityState] for one screen: probes on [start], then every
 * [pingIntervalMs], and again the instant the OS reports the default network changed, so a
 * banner and an offline gate reflect reality within a fraction of a second rather than on
 * the next scheduled tick.
 *
 * Lifecycle, from the owning Activity:
 *
 *     onResume  -> start()
 *     onPause   -> stop()
 *     onDestroy -> shutdown()
 *
 * Deliveries arrive on the main thread with the previous state, so the listener can show a
 * "restored" message on an offline-to-online edge. Every probe result is delivered, changed
 * or not.
 */
class ConnectivityWatcher internal constructor(
    context: Context,
    private val baseUrlProvider: () -> String,
    private val listener: Listener,
    private val pingIntervalMs: Long,
    private val prober: ConnectivityProber,
    private val executor: ExecutorService,
    private val mainHandler: Handler,
) {

    fun interface Listener {
        /** Main thread. */
        fun onConnectivityState(previous: ConnectivityState, current: ConnectivityState)
    }

    /**
     * @param baseUrlProvider read on every probe, so a base URL that changes after login
     *   (per-flavor server) is always the one pinged.
     */
    @JvmOverloads
    constructor(
        context: Context,
        baseUrlProvider: () -> String,
        listener: Listener,
        pingIntervalMs: Long = DEFAULT_PING_INTERVAL_MS,
    ) : this(
        context = context,
        baseUrlProvider = baseUrlProvider,
        listener = listener,
        pingIntervalMs = pingIntervalMs,
        prober = ConnectivityProbe(context),
        executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ConnectivityWatcher").apply { isDaemon = true }
        },
        mainHandler = Handler(Looper.getMainLooper()),
    )

    private val appContext: Context = context.applicationContext

    /** The last delivered state. [ConnectivityState.UNKNOWN] until the first probe resolves. */
    @Volatile
    var state: ConnectivityState = ConnectivityState.UNKNOWN
        private set

    /** True only once a probe has confirmed the server cannot be reached. */
    val isOffline: Boolean
        get() = state.isOffline

    private var callbackRegistered = false

    /** Exposed for tests; the OS invokes it on its own thread. */
    internal val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onDefaultNetworkChanged()
        override fun onLost(network: Network) = onDefaultNetworkChanged()
    }

    private val pingTask = object : Runnable {
        override fun run() {
            // A network callback racing shutdown() could land a ping after the executor is
            // gone; execute() would then throw on the main thread.
            if (executor.isShutdown) return
            try {
                executor.execute {
                    val result = try {
                        prober.probe(baseUrlProvider())
                    } catch (t: Throwable) {
                        recordNonFatal(t)
                        return@execute
                    }
                    mainHandler.post { deliver(result) }
                }
            } catch (e: RejectedExecutionException) {
                return
            }
            mainHandler.postDelayed(this, pingIntervalMs)
        }
    }

    /** Registers for network changes and probes immediately, then every interval. */
    fun start() {
        registerNetworkCallback()
        pingNow()
    }

    /** Stops the periodic probe and the network callback; the executor stays usable. */
    fun stop() {
        unregisterNetworkCallback()
        mainHandler.removeCallbacks(pingTask)
    }

    /** Probe now; the periodic schedule restarts from this tick. Safe from any thread. */
    fun pingNow() {
        mainHandler.removeCallbacks(pingTask)
        mainHandler.post(pingTask)
    }

    /**
     * [stop] plus releasing the probe thread. shutdown() rather than shutdownNow(): interrupting
     * a probe mid-socket would make it read unreachable and cache that wrong verdict for the
     * next two minutes. Letting it finish costs at most one socket timeout.
     */
    fun shutdown() {
        stop()
        executor.shutdown()
    }

    // Main thread only.
    private fun deliver(result: ConnectivityState) {
        val previous = state
        state = result
        listener.onConnectivityState(previous, result)
    }

    // The default network changed, so any cached internet verdict was taken on a different
    // network and tells us nothing about this one. Drop it, then re-probe at once.
    private fun onDefaultNetworkChanged() {
        prober.invalidateInternetVerdict()
        pingNow()
    }

    private fun registerNetworkCallback() {
        if (callbackRegistered) return
        try {
            val cm = connectivityManager() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback)
            } else {
                cm.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
            }
            callbackRegistered = true
        } catch (e: Exception) {
            recordNonFatal(e)
        }
    }

    private fun unregisterNetworkCallback() {
        if (!callbackRegistered) return
        try {
            connectivityManager()?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            recordNonFatal(e)
        } finally {
            callbackRegistered = false
        }
    }

    private fun connectivityManager(): ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    companion object {
        const val DEFAULT_PING_INTERVAL_MS = 30_000L
    }
}
