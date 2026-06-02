package za.co.mjrsolutions.shared.network.interceptor

import okhttp3.Headers
import org.junit.Test
import kotlin.test.assertEquals

class SensitiveDataTest {

    @Test
    fun `authorization is masked`() {
        val h = Headers.headersOf("Authorization", "Bearer secret-token")
        val out = SensitiveData.redactHeaders(h)
        assertEquals(SensitiveData.REDACTED, out["Authorization"])
    }

    @Test
    fun `x-api-key is masked case-insensitively`() {
        val h = Headers.headersOf("X-Api-Key", "abc123")
        val out = SensitiveData.redactHeaders(h)
        assertEquals(SensitiveData.REDACTED, out["X-Api-Key"])
    }

    @Test
    fun `any password header is masked`() {
        val h = Headers.headersOf("X-User-Password", "hunter2")
        val out = SensitiveData.redactHeaders(h)
        assertEquals(SensitiveData.REDACTED, out["X-User-Password"])
    }

    @Test
    fun `non-sensitive headers pass through`() {
        val h = Headers.headersOf("Accept", "application/json")
        val out = SensitiveData.redactHeaders(h)
        assertEquals("application/json", out["Accept"])
    }
}
