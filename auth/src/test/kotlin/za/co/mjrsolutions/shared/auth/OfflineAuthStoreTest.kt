package za.co.mjrsolutions.shared.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineAuthStoreTest {

    @Test
    fun `BCrypt hash and verify round-trip`() {
        val password = "M@L0ngane"
        val hash = BCrypt.with(BCrypt.Version.VERSION_2Y).hashToString(10, password.toCharArray())
        val result = BCrypt.verifyer(BCrypt.Version.VERSION_2Y).verifyStrict(password.toCharArray(), hash.toCharArray())
        assertTrue(result.verified)
    }

    @Test
    fun `BCrypt rejects wrong password`() {
        val hash = BCrypt.with(BCrypt.Version.VERSION_2Y).hashToString(10, "correct".toCharArray())
        val result = BCrypt.verifyer(BCrypt.Version.VERSION_2Y).verifyStrict("wrong".toCharArray(), hash.toCharArray())
        assertFalse(result.verified)
    }

    @Test
    fun `isOfflineLoginExpired returns false within 30 days`() {
        val now = System.currentTimeMillis()
        assertFalse(OfflineAuthStore.isExpired(now, now))
    }

    @Test
    fun `isOfflineLoginExpired returns true after 30 days`() {
        val thirtyOneDaysAgo = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000)
        assertTrue(OfflineAuthStore.isExpired(thirtyOneDaysAgo, System.currentTimeMillis()))
    }
}
