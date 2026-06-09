package za.co.mjrsolutions.shared.auth

import androidx.test.core.app.ApplicationProvider
import at.favre.lib.crypto.bcrypt.BCrypt
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OfflineAuthStoreSeedTest {

    private fun user(name: String = "FrancoL") = AuthUser(
        serverUserId = "42", username = name, name = "Franco", surname = "Landman",
        email = "f@x.co", apiToken = "", specialInstructions = null,
        languageCode = "af", languageVersion = "1", serverTime = null
    )

    @Before
    fun setup() {
        OfflineAuthStore.init(ApplicationProvider.getApplicationContext())
        OfflineAuthStore.clear()
    }

    @Test
    fun `seedWithPlaintext then verifyOffline succeeds`() {
        assertTrue(OfflineAuthStore.seedWithPlaintext(user(), "M@L0ngane"))
        assertNotNull(OfflineAuthStore.verifyOffline("FrancoL", "M@L0ngane"))
    }

    @Test
    fun `seedWithHash accepts a legacy cost-06 hash and verifyOffline succeeds`() {
        val legacy = BCrypt.with(BCrypt.Version.VERSION_2Y).hashToString(6, "M@L0ngane".toCharArray())
        assertTrue(OfflineAuthStore.seedWithHash(user(), legacy))
        assertNotNull(OfflineAuthStore.verifyOffline("FrancoL", "M@L0ngane"))
    }

    @Test
    fun `seed does not overwrite a real online-login row`() {
        OfflineAuthStore.upsertUser(user(), "realOnlinePass")
        assertFalse(OfflineAuthStore.seedWithPlaintext(user(), "staleLegacyPass"))
        assertNotNull(OfflineAuthStore.verifyOffline("FrancoL", "realOnlinePass"))
        assertNull(OfflineAuthStore.verifyOffline("FrancoL", "staleLegacyPass"))
    }

    @Test
    fun `seed with blank credential is rejected`() {
        assertFalse(OfflineAuthStore.seedWithPlaintext(user(), ""))
        assertFalse(OfflineAuthStore.seedWithHash(user(), ""))
    }
}
