package za.co.mjrsolutions.shared.audit.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserRecordWriterTest {

    private class FakeDocs : UserDocsGateway {
        val roots = mutableListOf<Pair<String, Map<String, Any?>>>()
        val bundles = mutableListOf<Triple<String, String, Map<String, Any?>>>()
        override fun mergeUser(username: String, fields: Map<String, Any?>) { roots.add(username to fields) }
        override fun mergeBundle(username: String, applicationId: String, fields: Map<String, Any?>) {
            bundles.add(Triple(username, applicationId, fields))
        }
    }

    private fun writer(docs: FakeDocs, applicationId: String = "za.co.x.agrihostnewapi") =
        UserRecordWriter(docs, appVersion = "10.0.24", applicationId = applicationId, clock = { 1234L })

    @Test
    fun `writesRootAndBundleMerge - hael's bundle fields plus the root profile`() {
        val docs = FakeDocs()
        writer(docs).write("FrancoL", "Franco Landman", isAdmin = true, pushToken = "tok")
        assertEquals("FrancoL", docs.roots.single().first)
        assertEquals(
            mapOf("username" to "FrancoL", "displayName" to "Franco Landman", "appVersion" to "10.0.24",
                "lastLoginTime" to 1234L, "pushToken" to "tok", "isAdmin" to true),
            docs.roots.single().second
        )
        val (user, bundleId, bundle) = docs.bundles.single()
        assertEquals("FrancoL", user)
        assertEquals("za.co.x.agrihostnewapi", bundleId)
        assertEquals(
            mapOf("username" to "FrancoL", "displayName" to "Franco Landman", "appVersion" to "10.0.24",
                "lastLoginTime" to 1234L, "pushToken" to "tok", "admin" to true),
            bundle
        )
    }

    @Test
    fun `omitsNullAdminAndToken - an app that doesn't know never clobbers the console's isAdmin`() {
        val docs = FakeDocs()
        writer(docs).write("janb", "Jan Botha", isAdmin = null, pushToken = null)
        val root = docs.roots.single().second
        assertFalse(root.containsKey("isAdmin"))
        assertFalse(root.containsKey("pushToken"))
        val bundle = docs.bundles.single().third
        assertFalse(bundle.containsKey("admin"))
        assertFalse(bundle.containsKey("pushToken"))
        assertTrue(bundle.containsKey("lastLoginTime"))
    }

    @Test
    fun `blankUsernameWritesNothing`() {
        val docs = FakeDocs()
        writer(docs).write("  ", "Jan", null, null)
        writer(docs).write(null, "Jan", null, null)
        assertTrue(docs.roots.isEmpty())
        assertTrue(docs.bundles.isEmpty())
    }

    @Test
    fun `blank display name falls back to the login id`() {
        val docs = FakeDocs()
        writer(docs).write("janb", null, null, null)
        assertEquals("janb", docs.roots.single().second["displayName"])
    }

    @Test
    fun `noApplicationIdSkipsBundle - only the root profile is written`() {
        val docs = FakeDocs()
        writer(docs, applicationId = "").write("janb", "Jan Botha", null, null)
        assertEquals(1, docs.roots.size)
        assertTrue(docs.bundles.isEmpty())
    }
}
