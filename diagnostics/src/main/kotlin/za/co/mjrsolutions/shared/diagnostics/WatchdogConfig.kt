package za.co.mjrsolutions.shared.diagnostics

/**
 * Tuning for [MainThreadWatchdog]. The defaults are what shipped in hael 9.0.61 onwards.
 */
data class WatchdogConfig(
    /** Report once the main thread has been unresponsive for this long. */
    val stallThresholdMs: Long = 15_000L,
    /** How often the main thread is asked to answer. */
    val heartbeatIntervalMs: Long = 1_000L,
    /** Cap per process so a permanently wedged main thread cannot flood Crashlytics. */
    val maxReportsPerProcess: Int = 20,
)
