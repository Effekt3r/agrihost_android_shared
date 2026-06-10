package za.co.mjrsolutions.shared.audit

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgriAuditTest {
    @Test
    fun `logging before init is a silent no-op`() {
        AgriAudit.resetForTest()
        AgriAudit.logGetFromServer("x", true)   // must not throw
        AgriAudit.logSyncToServer("x", true, AuditReportInfo("r", null, null))
    }
}
