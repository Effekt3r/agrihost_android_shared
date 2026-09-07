package za.co.mjrsolutions.shared.app_config

import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exercises the global-document plumbing through the internal document hook, exactly as the
 * Firestore listener would call it, so no Firebase instance is needed.
 */
class AgriAppConfigGlobalTest {

    @After
    fun tearDown() {
        AgriAppConfig.setGlobalCallback(null)
    }

    @Test
    fun `setGlobalCallback delivers the latest snapshot immediately when one exists`() {
        AgriAppConfig.onGlobalDocument(mapOf("calculateButtonAlwaysVisible" to true))

        var received: GlobalConfigSnapshot? = null
        AgriAppConfig.setGlobalCallback { received = it }

        assertNotNull(received)
        assertTrue(received!!.getBoolean("calculateButtonAlwaysVisible", false))
    }

    @Test
    fun `the global callback fires for every document update and the snapshot tracks the latest`() {
        val seen = mutableListOf<GlobalConfigSnapshot>()
        AgriAppConfig.onGlobalDocument(mapOf("v" to 1L))
        AgriAppConfig.setGlobalCallback { seen += it }
        AgriAppConfig.onGlobalDocument(mapOf("v" to 2L))
        AgriAppConfig.onGlobalDocument(mapOf("v" to 3L))

        assertEquals(listOf(1L, 2L, 3L), seen.map { it.data["v"] })
        assertEquals(3L, AgriAppConfig.getGlobalSnapshot()?.data?.get("v"))
    }

    @Test
    fun `a missing document delivers an empty snapshot so consumers apply their defaults`() {
        var received: GlobalConfigSnapshot? = null
        AgriAppConfig.setGlobalCallback { received = it }
        AgriAppConfig.onGlobalDocument(null)

        assertNotNull(received)
        assertTrue(received!!.data.isEmpty())
        assertEquals(false, received!!.getBoolean("calculateButtonAlwaysVisible", false))
    }

    @Test
    fun `clearing the callback stops delivery`() {
        // Setting a callback may legitimately deliver the snapshot that is already held, so
        // only the update pushed AFTER the callback was cleared is the one that must not arrive.
        val seen = mutableListOf<Any?>()
        AgriAppConfig.setGlobalCallback { seen += it.data["v"] }
        AgriAppConfig.setGlobalCallback(null)
        AgriAppConfig.onGlobalDocument(mapOf("v" to 9L))

        assertEquals(false, seen.contains(9L), "cleared callback still received an update")
    }
}
