package za.co.mjrsolutions.shared.diagnostics

import android.os.Handler
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The heartbeat loop behind [MainThreadWatchdog], with every environment dependency
 * injected so the behaviour can be unit-tested against a paused Robolectric looper.
 *
 * Asks the main thread to answer once per [WatchdogConfig.heartbeatIntervalMs]. If it has
 * not answered within [WatchdogConfig.stallThresholdMs], the main thread's stack is handed
 * to the [StallReporter] once per stall, capped at [WatchdogConfig.maxReportsPerProcess]
 * reports for the life of the process.
 */
internal class WatchdogEngine(
    private val config: WatchdogConfig,
    private val mainHandler: Handler,
    private val mainThread: Thread,
    private val reporter: StallReporter,
    /** Milliseconds on the clock the main looper runs on (uptime in production). */
    private val clock: () -> Long,
    private val debuggerConnected: () -> Boolean,
) {

    /** Blocking heartbeat loop. Returns only when the calling thread is interrupted. */
    fun run() {
        var reports = 0

        while (true) {
            val answered = AtomicBoolean(false)
            val askedAt = clock()
            mainHandler.post { answered.set(true) }

            var reportedThisStall = false
            var stalledMs = 0L

            while (!answered.get()) {
                if (!sleep(config.heartbeatIntervalMs)) {
                    return
                }
                stalledMs = clock() - askedAt

                // A connected debugger (Android Studio) suspends the main thread at
                // breakpoints, while stepping, and on any VM pause, and the clock keeps
                // advancing meanwhile, so a debugger pause is indistinguishable from a real
                // stall. Skip reporting while one is attached; reportedThisStall stays false so
                // a genuine stall is still reported if the debugger detaches mid-stall.
                if (!reportedThisStall && stalledMs >= config.stallThresholdMs && !answered.get()
                    && !debuggerConnected()
                ) {
                    reportedThisStall = true
                    if (reports < config.maxReportsPerProcess) {
                        reports++
                        report(stalledMs)
                    }
                }
            }

            if (reportedThisStall) {
                safely { reporter.onRecovered(stalledMs) }
            }

            if (!sleep(config.heartbeatIntervalMs)) {
                return
            }
        }
    }

    private fun report(stalledMs: Long) {
        val frames = try {
            mainThread.stackTrace
        } catch (t: Throwable) {
            emptyArray<StackTraceElement>()
        }
        safely { reporter.onStall(stalledMs, frames) }
    }

    /** A diagnostic must never take the app down; a broken reporter is logged and ignored. */
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(MainThreadWatchdog.TAG, "stall reporter failed", t)
        }
    }

    /** @return false if the thread was interrupted and the loop should stop. */
    private fun sleep(millis: Long): Boolean {
        return try {
            Thread.sleep(millis)
            true
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
}
