package za.co.mjrsolutions.shared.app_config

import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The per-app document (appConfig/{product}/bundles/{bundleId}) through the internal document hook,
 * exactly as the Firestore listener calls it, so no Firebase instance is needed.
 */
@RunWith(RobolectricTestRunner::class)
class AgriAppConfigBundleTest {

    private val app = AppConfigInit(product = "Hael", bundleId = "za.test", currentVersion = "10.0.23")
    private var redraws = 0
    private val banner = Runnable { redraws++ }

    @After
    fun tearDown() {
        AgriAppUpdate.removeListener(banner)
        AgriAppConfig.setCallback(null)
    }

    @Test
    fun `a minimum above this version with force is a required update`() {
        AgriAppConfig.onBundleDocument(app, mapOf("minVersion" to "10.0.24", "forceUpdate" to true))
        val snapshot = AgriAppConfig.getSnapshot()!!
        assertTrue(snapshot.updateRequired)
        assertTrue(snapshot.forceUpdate)
        assertEquals("10.0.24", snapshot.minVersion)
        assertEquals("10.0.23", snapshot.currentVersion)
    }

    @Test
    fun `a minimum at or below this version is no update`() {
        AgriAppConfig.onBundleDocument(app, mapOf("minVersion" to "10.0.23", "forceUpdate" to true))
        assertFalse(AgriAppConfig.getSnapshot()!!.updateRequired)
    }

    @Test
    fun `every new document redraws the banners in view`() {
        AgriAppUpdate.addListener(banner)
        AgriAppConfig.onBundleDocument(app, mapOf("minVersion" to "10.0.24"))
        AgriAppConfig.onBundleDocument(app, mapOf("minVersion" to "10.0.23"))
        assertEquals(2, redraws)
    }

    @Test
    fun `the app's own callback still gets every snapshot`() {
        val seen = mutableListOf<Boolean>()
        AgriAppConfig.setCallback(object : AppConfigCallback {
            override fun onConfigChanged(snapshot: AppConfigSnapshot) {
                seen += snapshot.updateRequired
            }
        })
        AgriAppConfig.onBundleDocument(app, mapOf("minVersion" to "10.0.24"))
        AgriAppConfig.onBundleDocument(app, mapOf("minVersion" to "10.0.23"))
        assertEquals(listOf(true, false), seen.takeLast(2))
    }

    @Test
    fun `fields the app does not know land in the extras`() {
        AgriAppConfig.onBundleDocument(app, mapOf("minVersion" to "10.0.23", "shouldUploadVideos" to true))
        assertEquals(mapOf<String, Any?>("shouldUploadVideos" to true), AgriAppConfig.getSnapshot()!!.extras)
    }
}
