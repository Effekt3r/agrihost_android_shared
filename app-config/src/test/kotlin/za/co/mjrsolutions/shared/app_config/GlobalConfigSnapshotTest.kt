package za.co.mjrsolutions.shared.app_config

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalConfigSnapshotTest {

    @Test
    fun `getBoolean returns the stored boolean`() {
        val snapshot = GlobalConfigSnapshot(mapOf("calculateButtonAlwaysVisible" to true))
        assertTrue(snapshot.getBoolean("calculateButtonAlwaysVisible", false))
    }

    @Test
    fun `getBoolean falls back to the default when the key is absent or not a boolean`() {
        val snapshot = GlobalConfigSnapshot(mapOf("flag" to "yes"))
        assertTrue(snapshot.getBoolean("missing", true))
        assertEquals(false, snapshot.getBoolean("missing", false))
        assertEquals(false, snapshot.getBoolean("flag", false))
    }

    @Test
    fun `getString returns strings only`() {
        val snapshot = GlobalConfigSnapshot(mapOf("host" to "smtp.example.test", "port" to 587L))
        assertEquals("smtp.example.test", snapshot.getString("host"))
        assertNull(snapshot.getString("port"))
        assertNull(snapshot.getString("missing"))
    }

    @Test
    fun `getFlavorMap returns the sub-map for the flavor`() {
        val snapshot = GlobalConfigSnapshot(
            mapOf(
                "smtpByFlavor" to mapOf(
                    "AGRIHOST_UAT" to mapOf("enabled" to true, "port" to 587L),
                    "GEMOEDSRUS" to mapOf("enabled" to false),
                )
            )
        )
        val uat = snapshot.getFlavorMap("smtpByFlavor", "AGRIHOST_UAT")
        assertEquals(true, uat?.get("enabled"))
        assertEquals(587L, uat?.get("port"))
    }

    @Test
    fun `getFlavorMap returns null when the parent or the flavor entry is not a map`() {
        val notAMap = GlobalConfigSnapshot(mapOf("smtpByFlavor" to "broken"))
        assertNull(notAMap.getFlavorMap("smtpByFlavor", "AGRIHOST_UAT"))

        val flavorNotAMap = GlobalConfigSnapshot(mapOf("smtpByFlavor" to mapOf("AGRIHOST_UAT" to "broken")))
        assertNull(flavorNotAMap.getFlavorMap("smtpByFlavor", "AGRIHOST_UAT"))

        val flavorMissing = GlobalConfigSnapshot(mapOf("smtpByFlavor" to mapOf("GEMOEDSRUS" to mapOf("enabled" to true))))
        assertNull(flavorMissing.getFlavorMap("smtpByFlavor", "AGRIHOST_UAT"))

        assertNull(GlobalConfigSnapshot(emptyMap()).getFlavorMap("smtpByFlavor", "AGRIHOST_UAT"))
    }
}
