package za.co.mjrsolutions.shared.diagnostics

import org.junit.Test
import kotlin.test.assertTrue

class StallReportTest {

    @Test
    fun `formats the stall duration and every main thread frame`() {
        val frames = arrayOf(
            StackTraceElement("a.B", "c", "B.kt", 1),
            StackTraceElement("d.E", "f", "E.kt", 2),
        )
        val text = formatStall(1234L, frames)
        assertTrue(text.startsWith("main thread blocked for >=1234ms"), text)
        assertTrue(text.contains("\n\tat a.B.c(B.kt:1)"), text)
        assertTrue(text.contains("\n\tat d.E.f(E.kt:2)"), text)
    }
}
