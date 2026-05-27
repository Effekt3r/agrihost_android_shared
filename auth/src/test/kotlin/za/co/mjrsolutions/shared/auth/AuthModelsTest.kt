package za.co.mjrsolutions.shared.auth

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthModelsTest {

    @Test
    fun `AuthUser holds all login response fields`() {
        val user = AuthUser(
            serverUserId = "123",
            username = "FrancoL",
            name = "Franco",
            surname = "Landman",
            email = "franco@test.com",
            apiToken = "tok_abc",
            specialInstructions = "Test instructions",
            languageCode = "af",
            languageVersion = "3",
            serverTime = "2026-05-27T00:00:00"
        )
        assertEquals("123", user.serverUserId)
        assertEquals("FrancoL", user.username)
        assertEquals("tok_abc", user.apiToken)
        assertEquals("af", user.languageCode)
    }

    @Test
    fun `AuthUser allows nullable fields`() {
        val user = AuthUser(
            serverUserId = "123",
            username = "FrancoL",
            name = "Franco",
            surname = "Landman",
            email = null,
            apiToken = "tok_abc",
            specialInstructions = null,
            languageCode = null,
            languageVersion = null,
            serverTime = null
        )
        assertNull(user.email)
        assertNull(user.specialInstructions)
    }

    @Test
    fun `AuthError carries type and message`() {
        val error = AuthError(AuthErrorType.INVALID_CREDENTIALS, "Bad password")
        assertEquals(AuthErrorType.INVALID_CREDENTIALS, error.type)
        assertEquals("Bad password", error.message)
    }
}
