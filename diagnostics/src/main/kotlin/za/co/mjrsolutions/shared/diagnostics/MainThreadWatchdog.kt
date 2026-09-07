package za.co.mjrsolutions.shared.diagnostics

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Diagnostic guard for main-thread stalls.
 *
 * Android writes an ANR's stack into /data/anr, which only a rooted device can read, so the
 * blocking frame behind a field-tablet lock-up was never visible: the bugreport Java dump
 * fails and the trace is dropped as soon as the app is force stopped.
 *
 * This watchdog asks the main thread to answer once a second. If it has not answered within
 * [WatchdogConfig.stallThresholdMs] it records the main thread stack to logcat (tag [TAG])
 * and to Crashlytics as a non-fatal, which names the blocking call on any device, without
 * root. Timing uses [SystemClock.uptimeMillis], the clock the main looper itself runs on: it
 * does not advance while the device is in deep sleep or while the process is frozen as a
 * cached app, so neither is mistaken for a stall.
 *
 * Start it first thing in `Application.onCreate` so it also covers the rest of that method:
 *
 *     MainThreadWatchdog.start()
 */
object MainThreadWatchdog {

    const val TAG = "MainThreadWatchdog"

    @Volatile
    private var thread: Thread? = null

    @JvmStatic
    fun start() = start(WatchdogConfig())

    /** Idempotent: a second call while the watchdog is running is a no-op. */
    @JvmStatic
    @Synchronized
    fun start(config: WatchdogConfig) {
        if (thread?.isAlive == true) {
            return
        }
        val engine = WatchdogEngine(
            config = config,
            mainHandler = Handler(Looper.getMainLooper()),
            mainThread = Looper.getMainLooper().thread,
            reporter = CrashlyticsStallReporter(),
            clock = { SystemClock.uptimeMillis() },
            debuggerConnected = { Debug.isDebuggerConnected() },
        )
        thread = Thread(engine::run, TAG).apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    @JvmStatic
    fun isRunning(): Boolean = thread?.isAlive == true
}
