package za.co.mjrsolutions.shared.diagnostics

import android.os.Handler
import android.os.Looper
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Robolectric runs the test on the main thread with a PAUSED main looper: a heartbeat the
 * engine posts to the main handler only runs when the test idles the looper. Not idling it
 * IS a main-thread stall; idling it is the main thread answering.
 */
@RunWith(RobolectricTestRunner::class)
class WatchdogEngineTest {

    private val config = WatchdogConfig(
        stallThresholdMs = 100L,
        heartbeatIntervalMs = 10L,
        maxReportsPerProcess = 20,
    )
    private val reporter = RecordingReporter()
    private val debuggerAttached = AtomicBoolean(false)
    private var thread: Thread? = null

    private fun startEngine(cfg: WatchdogConfig = config) {
        val engine = WatchdogEngine(
            config = cfg,
            mainHandler = Handler(Looper.getMainLooper()),
            mainThread = Looper.getMainLooper().thread,
            reporter = reporter,
            // SystemClock.uptimeMillis is frozen under the paused Robolectric looper, so the
            // engine gets a wall clock instead.
            clock = { System.nanoTime() / 1_000_000L },
            debuggerConnected = { debuggerAttached.get() },
        )
        thread = Thread(engine::run, "watchdog-under-test").apply {
            isDaemon = true
            start()
        }
    }

    @After
    fun tearDown() {
        thread?.interrupt()
        thread?.join(1_000)
    }

    @Test
    fun `reports a stall once the main looper has not answered for the threshold`() {
        startEngine()
        // The looper is never idled, so the heartbeat never gets its answer.
        assertTrue(awaitUntil(2_000) { reporter.stalls.size == 1 }, "expected one stall report")
        assertTrue(reporter.stalls[0] >= 100L, "stalledMs must be at least the threshold, was ${reporter.stalls[0]}")
        assertTrue(reporter.frames[0].isNotEmpty(), "the report must carry the main thread frames")
    }

    @Test
    fun `does not report while the main looper keeps answering`() {
        startEngine()
        answerMainLooperFor(300)
        assertEquals(0, reporter.stalls.size)
    }

    @Test
    fun `reports once per stall and reports recovery when the looper answers`() {
        startEngine()
        assertTrue(awaitUntil(2_000) { reporter.stalls.size == 1 })
        Thread.sleep(300) // three more thresholds inside the same stall
        assertEquals(1, reporter.stalls.size)
        shadowOf(Looper.getMainLooper()).idle() // the answer finally arrives
        assertTrue(awaitUntil(2_000) { reporter.recovered.size == 1 }, "expected a recovery report")
        assertTrue(reporter.recovered[0] >= 100L)
    }

    @Test
    fun `stops reporting after the per-process cap but still reports recovery`() {
        startEngine(config.copy(maxReportsPerProcess = 1))
        assertTrue(awaitUntil(2_000) { reporter.stalls.size == 1 })
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(awaitUntil(2_000) { reporter.recovered.size == 1 })

        Thread.sleep(400) // a second, uncapped stall would have reported by now
        assertEquals(1, reporter.stalls.size)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(awaitUntil(2_000) { reporter.recovered.size == 2 })
    }

    @Test
    fun `holds the report while a debugger is attached and fires when it detaches mid-stall`() {
        debuggerAttached.set(true)
        startEngine()
        Thread.sleep(300)
        assertEquals(0, reporter.stalls.size)
        debuggerAttached.set(false)
        assertTrue(awaitUntil(2_000) { reporter.stalls.size == 1 })
    }

    private fun answerMainLooperFor(ms: Long) {
        val end = System.nanoTime() / 1_000_000L + ms
        while (System.nanoTime() / 1_000_000L < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun awaitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.nanoTime() / 1_000_000L + timeoutMs
        while (System.nanoTime() / 1_000_000L < end) {
            if (condition()) return true
            Thread.sleep(5)
        }
        return condition()
    }

    private class RecordingReporter : StallReporter {
        val stalls = CopyOnWriteArrayList<Long>()
        val frames = CopyOnWriteArrayList<Array<StackTraceElement>>()
        val recovered = CopyOnWriteArrayList<Long>()

        override fun onStall(stalledMs: Long, mainThreadFrames: Array<StackTraceElement>) {
            stalls += stalledMs
            frames += mainThreadFrames
        }

        override fun onRecovered(stalledMs: Long) {
            recovered += stalledMs
        }
    }
}
