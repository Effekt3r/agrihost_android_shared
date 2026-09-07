package za.co.mjrsolutions.shared.diagnostics

/** Where the engine sends what it saw. Implementations must never throw into the engine. */
internal interface StallReporter {

    /** The main thread has not answered for [stalledMs]; [mainThreadFrames] is its stack right now. */
    fun onStall(stalledMs: Long, mainThreadFrames: Array<StackTraceElement>)

    /** A stall that was reported has ended; [stalledMs] is roughly how long it lasted. */
    fun onRecovered(stalledMs: Long)
}

/** One line per frame, in logcat's own "at ..." shape so it reads like a real trace. */
internal fun formatStall(stalledMs: Long, frames: Array<StackTraceElement>): String {
    val sb = StringBuilder("main thread blocked for >=").append(stalledMs).append("ms")
    for (frame in frames) {
        sb.append("\n\tat ").append(frame)
    }
    return sb.toString()
}
