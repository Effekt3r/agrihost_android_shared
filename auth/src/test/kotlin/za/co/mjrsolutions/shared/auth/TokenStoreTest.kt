package za.co.mjrsolutions.shared.auth

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreTest {

    @Test
    fun `getAuthHeaders includes api key always`() {
        val headers = TokenStore.getAuthHeaders(token = null)
        assertEquals("t4LywO234234TDDSZctwMT1uuR234IL6fPjfOO234cVIf5aWNGwjgksdai", headers["x-api-key"])
        assertEquals("application/json", headers["Accept"])
        assertEquals("application/json", headers["Content-Type"])
        assertNull(headers["Authorization"])
    }

    @Test
    fun `getAuthHeaders includes Bearer when token is present`() {
        val headers = TokenStore.getAuthHeaders(token = "tok_abc")
        assertEquals("Bearer tok_abc", headers["Authorization"])
        assertEquals("t4LywO234234TDDSZctwMT1uuR234IL6fPjfOO234cVIf5aWNGwjgksdai", headers["x-api-key"])
    }
}
