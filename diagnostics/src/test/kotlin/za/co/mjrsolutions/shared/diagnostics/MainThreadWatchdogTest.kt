package za.co.mjrsolutions.shared.diagnostics

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MainThreadWatchdogTest {

    @Test
    fun `start is idempotent and runs one daemon thread`() {
        val quiet = WatchdogConfig(stallThresholdMs = 60_000L)
        MainThreadWatchdog.start(quiet)
        MainThreadWatchdog.start(quiet)

        assertTrue(MainThreadWatchdog.isRunning())
        val threads = Thread.getAllStackTraces().keys
            .filter { it.name == MainThreadWatchdog.TAG && it.isAlive }
        assertEquals(1, threads.size, "expected exactly one watchdog thread")
        assertTrue(threads[0].isDaemon, "the watchdog must never keep the process alive")
    }
}
