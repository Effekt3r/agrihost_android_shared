package za.co.mjrsolutions.shared.app_config

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionCompareTest {

    @Test
    fun `equal versions return 0`() {
        assertEquals(0, VersionCompare.compare("8.0.30", "8.0.30"))
    }

    @Test
    fun `lower returns negative`() {
        assertTrue(VersionCompare.compare("8.0.25", "8.0.30") < 0)
    }

    @Test
    fun `higher returns positive`() {
        assertTrue(VersionCompare.compare("8.1.0", "8.0.30") > 0)
    }

    @Test
    fun `different segment counts handled`() {
        assertTrue(VersionCompare.compare("8.0", "8.0.0") == 0)
        assertTrue(VersionCompare.compare("8.0.1", "8.0") > 0)
    }

    @Test
    fun `null treated as lowest`() {
        assertTrue(VersionCompare.compare(null, "1.0") < 0)
        assertTrue(VersionCompare.compare("1.0", null) > 0)
        assertEquals(0, VersionCompare.compare(null, null))
    }

    @Test
    fun `non-numeric segments default to 0`() {
        assertEquals(0, VersionCompare.compare("1.0.x", "1.0.0"))
    }
}
